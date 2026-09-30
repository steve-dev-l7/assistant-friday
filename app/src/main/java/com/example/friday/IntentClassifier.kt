package com.example.friday

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class CommandResult(
    val intent: String,
    val target: String? = null,
    val text: String? = null,
    val task: String? = null,
    val time: String? = null,
    val timeType: String? = null,
    val confidence: Float = 0f
)

class IntentClassifier(context: Context) {

    private val TAG = "IntentClassifier"

    // FIX: must match max_length used in training (Python side reduced 20 -> 14)
    private val MAX_LEN = 14
    // safety net only — the model's own trained UNKNOWN class is the main
    // defense against out-of-domain chat, this just catches low-confidence edge cases
    private val CONFIDENCE_THRESHOLD = 0.40f

    private val interpreter: Interpreter
    private val wordDict: Map<String, Int>
    private val labelMap: Map<Int, String>
    private val oovIndex: Int
    private val fallbackIntent: String

    private val KERAS_FILTER_REGEX = Regex("[!\"#\$%&()*+,\\-./:;<=>?@\\[\\]^_`{|}~\\t\\n]")

    init {
        // Only 3 files now — there's no slot_label_map.json anymore.
        // Slots are extracted with regex below, same logic as the Python
        // build_json() pipeline, not a second model output.
        interpreter = Interpreter(loadModelFile(context, "intent_model.tflite"))
        wordDict = loadWordDict(context, "word_dict.json")
        labelMap = loadLabelMap(context, "label_map.json")
        oovIndex = wordDict["<OOV>"] ?: 1
        fallbackIntent = if (labelMap.values.contains("UNKNOWN")) "UNKNOWN" else (labelMap[0] ?: "UNKNOWN")
        Log.d(TAG, "Loaded intent model. Intents=${labelMap.size}")
    }

    private fun loadModelFile(context: Context, fileName: String): MappedByteBuffer {
        val fd = context.assets.openFd(fileName)
        val stream = java.io.FileInputStream(fd.fileDescriptor)
        return stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    private fun loadWordDict(context: Context, fileName: String): Map<String, Int> {
        val json = context.assets.open(fileName).bufferedReader().use { it.readText() }
        val obj = JSONObject(json)
        val map = mutableMapOf<String, Int>()
        obj.keys().forEach { k -> map[k] = obj.getInt(k) }
        return map
    }

    private fun loadLabelMap(context: Context, fileName: String): Map<Int, String> {
        val json = context.assets.open(fileName).bufferedReader().use { it.readText() }
        val obj = JSONObject(json)
        val map = mutableMapOf<Int, String>()
        obj.keys().forEach { k -> map[k.toInt()] = obj.getString(k) }
        return map
    }

    private fun tokenize(text: String): FloatArray {
        val cleaned = text.lowercase().replace(KERAS_FILTER_REGEX, " ")
        val words = cleaned.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val ids = FloatArray(MAX_LEN) { 0f }
        for (i in words.indices) {
            if (i >= MAX_LEN) break
            ids[i] = (wordDict[words[i]] ?: oovIndex).toFloat()
        }
        return ids
    }

    // Single-output model now (just intent probabilities) -> plain interpreter.run(),
    // no more runForMultipleInputsOutputs / slot tensor.
    fun predict(text: String): CommandResult {
        val tokenIds = tokenize(text)
        val input = arrayOf(tokenIds)
        val output = Array(1) { FloatArray(labelMap.size) }

        interpreter.run(input, output)

        val probs = output[0]
        var maxIdx = 0
        var maxVal = probs[0]
        for (i in probs.indices) {
            if (probs[i] > maxVal) {
                maxVal = probs[i]
                maxIdx = i
            }
        }

        var intent = labelMap[maxIdx] ?: fallbackIntent
        if (maxVal < CONFIDENCE_THRESHOLD) {
            intent = fallbackIntent
        }

        val result = buildResult(text, intent, maxVal)
        Log.d(TAG, "predict: $result")
        return result
    }

    // ================= REGEX SLOT EXTRACTION (Kotlin port of build_json) =================

    private val CALL_TRIGGERS = listOf(
        "make a phone call to", "make a call to", "give a call to", "connect me to",
        "i need to speak with", "speak with", "ring up", "call to", "dial", "call", "ring"
    )
    private val STOP_CALL = setOf(
        "hey", "can", "you", "please", "could", "would", "the", "my", "to", "for", "me",
        "right", "now", "immediately", "number", "i", "need", "a", "an", "phone"
    )

    private fun extractCall(cmd: String): String? {
        var text = cmd
        for (trig in CALL_TRIGGERS) {
            if (text.contains(trig)) {
                text = text.replaceFirst(trig, "")
                break
            }
        }
        val tokens = text.split(Regex("\\s+")).filter { it.isNotEmpty() && it !in STOP_CALL }
        return tokens.joinToString(" ").trim().ifEmpty { null }
    }

    private fun extractMessage(cmd: String): Pair<String?, String> {
        Regex("^let\\s+(\\S+)\\s+know\\s+(?:that\\s+)?(.*)$").find(cmd)?.let {
            return it.groupValues[1] to it.groupValues[2].trim()
        }
        Regex("^\\s*(?:can you )?tell\\s+(\\S+)\\s+(?:that\\s+)?(.*)$").find(cmd)?.let {
            return it.groupValues[1] to it.groupValues[2].trim()
        }
        Regex("^text\\s+(?!to\\b)(\\S+)\\s+(?:that\\s+)?(.*)$").find(cmd)?.let {
            return it.groupValues[1] to it.groupValues[2].trim()
        }
        Regex("^message\\s+(?!to\\b)(\\S+)\\s+(.*)$").find(cmd)?.let {
            return it.groupValues[1] to it.groupValues[2].trim()
        }
        Regex("\\bto\\s+(\\S+)\\s+(?:that\\s+|saying\\s+)?(.*)$").find(cmd)?.let {
            return it.groupValues[1] to it.groupValues[2].trim()
        }
        return null to cmd
    }

    private val LAUNCH_TRIGGERS = listOf(
        "i want to use", "want to use", "boot up", "start the", "open the",
        "launch the", "use", "open", "launch", "start"
    )
    private val STOP_LAUNCH = setOf("hey", "please", "can", "you", "my", "the")
    private val TRAILING_LAUNCH = setOf("application", "app")

    private fun extractLaunch(cmd: String): String? {
        var text = cmd
        for (trig in LAUNCH_TRIGGERS) {
            if (text.contains(trig)) {
                text = text.replaceFirst(trig, "")
                break
            }
        }
        val tokens = text.split(Regex("\\s+")).filter { it.isNotEmpty() && it !in STOP_LAUNCH }.toMutableList()
        while (tokens.isNotEmpty() && tokens.last() in TRAILING_LAUNCH) tokens.removeAt(tokens.size - 1)
        return tokens.joinToString(" ").trim().ifEmpty { null }
    }

    private fun extractDeviceLaunch(cmd: String, deviceWords: List<String>): String? {
        var text = cmd
        val devicePattern = deviceWords.joinToString("|") { Regex.escape(it) }

        text = Regex("^(hey\\s+)?($devicePattern)\\s+").replaceFirst(text, "")
        for (trig in LAUNCH_TRIGGERS) {
            if (text.contains(trig)) {
                text = text.replaceFirst(trig, "")
                break
            }
        }
        text = Regex("\\s+on\\s+(the\\s+|my\\s+)?($devicePattern)\\s*$").replaceFirst(text, "")

        val tokens = text.split(Regex("\\s+")).filter { it.isNotEmpty() && it !in STOP_LAUNCH }.toMutableList()
        while (tokens.isNotEmpty() && tokens.last() in TRAILING_LAUNCH) tokens.removeAt(tokens.size - 1)
        return tokens.joinToString(" ").trim().ifEmpty { null }
    }

    private fun extractMovieOtt(cmd: String): Pair<String?, String?> {
        val m = Regex("^play\\s+(.+?)\\s+in\\s+(.+?)\\s+on\\s+(?:the\\s+)?tv\\s*$").find(cmd)
        return if (m != null) m.groupValues[1].trim() to m.groupValues[2].trim() else null to null
    }

    private val OFF_KEYWORDS = setOf("off", "disable", "disconnect", "stop", "kill")

    private fun extractToggle(cmd: String): String {
        val tokens = cmd.split(Regex("\\s+"))
        return if (tokens.any { it in OFF_KEYWORDS }) "off" else "on"
    }

    private val PLAY_FILLER = setOf("some", "a", "my", "on", "spotify", "the")
    private val PLAY_NO_TARGET_PHRASES = setOf(
        "play music", "play some songs", "play a song", "play music on spotify",
        "start playing music", "play something relaxing", "put on some music",
        "resume music", "play the last song", "play it", "resume playback",
        "continue playing", "play whatever was playing", "start playing songs",
        "start the music"
    )

    private fun extractPlayTarget(cmd: String): String? {
        if (cmd.trim() in PLAY_NO_TARGET_PHRASES) return null
        val m = Regex("^(?:play|put on|start playing)\\s+(.*)$").find(cmd) ?: return null
        val tokens = m.groupValues[1].split(Regex("\\s+")).filter { it.isNotEmpty() && it !in PLAY_FILLER }
        return tokens.joinToString(" ").trim().ifEmpty { null }
    }

    private fun isAbsoluteTime(t: String) = Regex("\\b(am|pm)\\b").containsMatchIn(t)

    private fun extractReminder(cmd: String): Triple<String, String, String> {
        var text = cmd

        Regex("^wake me up at (.*)$").find(text)?.let {
            return Triple("wake up", it.groupValues[1].trim(), "absolute")
        }

        Regex("^remind me at (.+?) to (.+)$").find(text)?.let {
            val timeType = if (isAbsoluteTime(it.groupValues[1])) "absolute" else "relative"
            return Triple(it.groupValues[2].trim(), it.groupValues[1].trim(), timeType)
        }

        for (p in listOf("can you remind me to ", "i need a reminder for ", "set a reminder to ")) {
            if (text.startsWith(p)) {
                text = text.substring(p.length).trim()
                break
            }
        }

        Regex("^set an alarm for (.+)$").find(text)?.let {
            val t = it.groupValues[1].trim()
            return Triple("alarm", t, if (isAbsoluteTime(t)) "absolute" else "relative")
        }

        Regex("^set a timer for (.+)$").find(text)?.let {
            val t = it.groupValues[1].trim()
            return Triple("timer", t, if (isAbsoluteTime(t)) "absolute" else "relative")
        }

        var task = "just a reminder bro!"
        val timeText = ""
        val timeType = "absolute"

        return when {
            text.contains(" in ") -> {
                val parts = text.split(" in ", limit = 2)
                task = parts[0].replace("remind me to", "").replace("remind me", "").trim().ifEmpty { task }
                Triple(task, parts[1].trim(), "relative")
            }
            text.contains(" at ") -> {
                val parts = text.split(" at ", limit = 2)
                task = parts[0].replace("remind me to", "").replace("remind me", "").trim().ifEmpty { task }
                Triple(task, parts[1].trim(), "absolute")
            }
            else -> {
                task = text.replace("remind me to", "").replace("remind me", "").trim().ifEmpty { task }
                Triple(task, timeText, timeType)
            }
        }
    }

    private val NO_SLOT_INTENTS = setOf(
        "overall_pause", "overall_next", "overall_previous", "overall_mute", "deactivate"
    )

    private fun buildResult(cmdRaw: String, intent: String, confidence: Float): CommandResult {
        // strip trailing punctuation, same as the Python build_json fix
        val cmd = cmdRaw.lowercase().trim().replace(Regex("[?!.]+$"), "")
        val intentKey = intent.lowercase()

        var target: String? = null
        var text: String? = null
        var task: String? = null
        var time: String? = null
        var timeType: String? = null

        when {
            intentKey == "call" -> target = extractCall(cmd)
            intentKey == "message" -> {
                val (t, txt) = extractMessage(cmd)
                target = t; text = txt
            }
            intentKey == "launch" -> target = extractLaunch(cmd)
            intentKey == "launch_app_jarvis" -> target = extractDeviceLaunch(cmd, listOf("jarvis", "laptop"))
            intentKey == "launch_app_veronica" -> target = extractDeviceLaunch(cmd, listOf("veronica", "tv"))
            intentKey == "open_app_tv" -> target = extractDeviceLaunch(cmd, listOf("tv"))
            intentKey == "play_movie_ott_tv" -> {
                val (t, tsk) = extractMovieOtt(cmd)
                target = t; task = tsk
            }
            intentKey in setOf("hotspot", "wifi", "network", "flashlight") -> target = extractToggle(cmd)
            intentKey == "overall_play" -> target = extractPlayTarget(cmd)
            intentKey == "reminder" -> {
                val (tsk, tm, tt) = extractReminder(cmd)
                task = tsk; time = tm; timeType = tt
            }
            intentKey in NO_SLOT_INTENTS -> { /* no slot needed, intent alone is enough */ }
            else -> text = cmd
        }

        return CommandResult(
            intent = intent,
            target = target,
            text = text,
            task = task,
            time = time,
            timeType = timeType,
            confidence = confidence
        )
    }

    fun close() = interpreter.close()
}