package com.example.friday

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object ModelAssetManager {

    private const val TAG = "ModelAssetManager"
    private const val MODEL_FILE_NAME = "gemma.litertlm"
    private const val MODEL_URL = "https://huggingface.co/stevel7/ondevice_gemma_litertlm/resolve/main/gemma.litertlm"

    private const val CHUNK_COUNT = 4          // parallel connections -> speed boost
    private const val MAX_RETRIES = 5          // per chunk / per attempt
    private const val CONNECT_TIMEOUT = 15000
    private const val READ_TIMEOUT = 20000

    suspend fun getModelPath(
        context: Context,
        onProgress: suspend (progress: Int, message: String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val outFile = File(context.filesDir, MODEL_FILE_NAME)

        if (outFile.exists() && outFile.length() > 0) {
            Log.d(TAG, "Model already exists at: ${outFile.absolutePath}")
            withContext(Dispatchers.Main) { onProgress(100, "WAKING UP FRIDAY...") }
            return@withContext outFile.absolutePath
        }

        withContext(Dispatchers.Main) { onProgress(0, "CONNECTING TO SERVER...") }

        val resolvedUrl = resolveRedirect(MODEL_URL)
        val fileLength = getContentLength(resolvedUrl)
        val supportsRange = fileLength > 0 && checkRangeSupport(resolvedUrl)

        if (supportsRange) {
            Log.d(TAG, "Server supports Range -> parallel chunk download ($CHUNK_COUNT chunks)")
            downloadParallelChunks(resolvedUrl, outFile, fileLength, onProgress)
        } else {
            Log.d(TAG, "Range not supported / unknown length -> single-stream download with resume")
            downloadSingleStreamWithRetry(resolvedUrl, outFile, onProgress, fileLength)
        }

        outFile.absolutePath
    }

    // ---------------------------------------------------------------
    // Redirect / metadata helpers
    // ---------------------------------------------------------------

    private fun resolveRedirect(urlString: String): String {
        var current = urlString
        repeat(5) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "HEAD"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location")
                    if (loc == null) return current
                    current = loc
                } else {
                    return current
                }
            } finally {
                conn.disconnect()
            }
        }
        return current
    }

    private fun getContentLength(urlString: String): Long {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
        }
        return try {
            conn.responseCode
            conn.contentLengthLong
        } catch (e: Exception) {
            -1L
        } finally {
            conn.disconnect()
        }
    }

    private fun checkRangeSupport(urlString: String): Boolean {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
        }
        return try {
            conn.responseCode
            conn.getHeaderField("Accept-Ranges")?.equals("bytes", ignoreCase = true) == true
        } catch (e: Exception) {
            false
        } finally {
            conn.disconnect()
        }
    }

    // ---------------------------------------------------------------
    // Parallel chunked download (fast path)
    // ---------------------------------------------------------------

    private suspend fun downloadParallelChunks(
        urlString: String,
        outFile: File,
        fileLength: Long,
        onProgress: suspend (Int, String) -> Unit
    ) = coroutineScope {
        val chunkSize = fileLength / CHUNK_COUNT
        val ranges = (0 until CHUNK_COUNT).map { i ->
            val start = i * chunkSize
            val end = if (i == CHUNK_COUNT - 1) fileLength - 1 else (start + chunkSize - 1)
            start to end
        }
        val chunkFiles = ranges.indices.map { i -> File(outFile.parentFile, "${outFile.name}.part$i") }

        val downloadedBytes = LongArray(CHUNK_COUNT)
        val totalMb = fileLength / (1024 * 1024)
        var lastReportedPercent = -1
        val progressMutex = Mutex()

        suspend fun reportProgress() {
            val total = downloadedBytes.sum()
            val percent = ((total.toDouble() / fileLength.toDouble()) * 100).toInt()
            if (percent != lastReportedPercent) {
                lastReportedPercent = percent
                val downloadedMb = total / (1024 * 1024)
                withContext(Dispatchers.Main) {
                    onProgress(percent, "DOWNLOADING... $downloadedMb MB / $totalMb MB")
                }
            }
        }

        try {
            val jobs = ranges.mapIndexed { i, (start, end) ->
                async {
                    downloadChunkWithRetry(urlString, chunkFiles[i], start, end) { bytesSoFar ->
                        downloadedBytes[i] = bytesSoFar
                        progressMutex.withLock { reportProgress() }
                    }
                }
            }
            jobs.awaitAll()
        } catch (e: Exception) {
            // don't delete chunk files -- next attempt can resume from where each chunk stopped
            withContext(Dispatchers.Main) { onProgress(0, "DOWNLOAD FAILED. PLEASE RESTART.") }
            throw e
        }

        // merge chunks in order into the final file
        withContext(Dispatchers.Main) { onProgress(99, "ASSEMBLING MODEL...") }
        val tempMerged = File(outFile.parentFile, "${outFile.name}.merging")
        FileOutputStream(tempMerged).use { out ->
            for (chunkFile in chunkFiles) {
                chunkFile.inputStream().use { it.copyTo(out, bufferSize = 8 * 1024 * 1024) }
            }
        }
        chunkFiles.forEach { it.delete() }
        tempMerged.renameTo(outFile)

        withContext(Dispatchers.Main) { onProgress(100, "BOOTING AI CORE...") }
    }

    private suspend fun downloadChunkWithRetry(
        urlString: String,
        chunkFile: File,
        rangeStart: Long,
        rangeEnd: Long,
        onBytes: suspend (Long) -> Unit
    ) {
        var attempt = 0
        while (true) {
            // resume this chunk from wherever it last stopped
            val alreadyHave = if (chunkFile.exists()) chunkFile.length() else 0L
            val actualStart = rangeStart + alreadyHave

            if (actualStart > rangeEnd) {
                onBytes(alreadyHave) // this chunk is already fully downloaded
                return
            }

            var conn: HttpURLConnection? = null
            try {
                conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    setRequestProperty("Range", "bytes=$actualStart-$rangeEnd")
                    connectTimeout = CONNECT_TIMEOUT
                    readTimeout = READ_TIMEOUT
                }
                val code = conn.responseCode
                if (code != HttpURLConnection.HTTP_PARTIAL && code != HttpURLConnection.HTTP_OK) {
                    throw Exception("Chunk download HTTP $code")
                }

                FileOutputStream(chunkFile, alreadyHave > 0).use { out ->
                    conn.inputStream.use { input ->
                        val buffer = ByteArray(1 * 1024 * 1024)
                        var bytesRead: Int
                        var total = alreadyHave
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            out.write(buffer, 0, bytesRead)
                            total += bytesRead
                            onBytes(total)
                        }
                    }
                }
                return // this chunk finished successfully
            } catch (e: Exception) {
                attempt++
                if (attempt > MAX_RETRIES) throw e
                val backoffMs = (1000L * (1L shl (attempt - 1))).coerceAtMost(15000L)
                Log.w(TAG, "Chunk [$rangeStart-$rangeEnd] retry $attempt/$MAX_RETRIES after: ${e.message}, waiting ${backoffMs}ms")
                delay(backoffMs)
            } finally {
                conn?.disconnect()
            }
        }
    }

    // ---------------------------------------------------------------
    // Single-stream fallback (server doesn't support Range, or length unknown)
    // ---------------------------------------------------------------

    private suspend fun downloadSingleStreamWithRetry(
        urlString: String,
        outFile: File,
        onProgress: suspend (Int, String) -> Unit,
        knownLength: Long
    ) {
        val tempFile = File(outFile.parentFile, "${outFile.name}.tmp")
        var attempt = 0

        while (true) {
            var conn: HttpURLConnection? = null
            try {
                var resumeFrom = if (tempFile.exists()) tempFile.length() else 0L
                conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    if (resumeFrom > 0) setRequestProperty("Range", "bytes=$resumeFrom-")
                    connectTimeout = CONNECT_TIMEOUT
                    readTimeout = READ_TIMEOUT
                }
                val code = conn.responseCode
                if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                    throw Exception("Server returned HTTP $code")
                }
                // server ignored our Range header -> it's sending the whole file again, restart clean
                if (code == HttpURLConnection.HTTP_OK && resumeFrom > 0) {
                    resumeFrom = 0
                    tempFile.delete()
                }

                val total = if (knownLength > 0) knownLength else conn.contentLengthLong.let { if (it > 0) it + resumeFrom else -1L }
                val totalMb = if (total > 0) total / (1024 * 1024) else 0
                var lastLoggedMb = resumeFrom / (1024 * 1024)

                withContext(Dispatchers.IO) {
                    FileOutputStream(tempFile, resumeFrom > 0).use { output ->
                        conn.inputStream.use { input ->
                            val buffer = ByteArray(8 * 1024 * 1024)
                            var bytesRead: Int
                            var downloaded = resumeFrom
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                downloaded += bytesRead
                                val downloadedMb = downloaded / (1024 * 1024)
                                if (downloadedMb - lastLoggedMb >= 5) {
                                    val percent =
                                        if (total > 0) ((downloaded.toDouble() / total.toDouble()) * 100).toInt() else 0
                                    withContext(Dispatchers.Main) {
                                        onProgress(
                                            percent,
                                            "DOWNLOADING... $downloadedMb MB / $totalMb MB"
                                        )
                                    }
                                    lastLoggedMb = downloadedMb
                                }
                            }
                        }
                    }
                }

                tempFile.renameTo(outFile)
                withContext(Dispatchers.Main) { onProgress(100, "BOOTING AI CORE...") }
                return

            } catch (e: Exception) {
                attempt++
                if (attempt > MAX_RETRIES) {
                    withContext(Dispatchers.Main) { onProgress(0, "DOWNLOAD FAILED. PLEASE RESTART.") }
                    throw e
                }
                val backoffMs = (1000L * (1L shl (attempt - 1))).coerceAtMost(15000L)
                Log.w(TAG, "Download retry $attempt/$MAX_RETRIES after: ${e.message}, waiting ${backoffMs}ms")
                withContext(Dispatchers.Main) {
                    onProgress(0, "NETWORK ERROR, RETRYING ($attempt/$MAX_RETRIES)...")
                }
                delay(backoffMs)
            } finally {
                conn?.disconnect()
            }
        }
    }
}