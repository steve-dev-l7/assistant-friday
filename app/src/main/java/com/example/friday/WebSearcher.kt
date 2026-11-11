package com.example.friday

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class WebSearcher {

    private val TAG = "WebSearcher"

    suspend fun search(query: String): String? = withContext(Dispatchers.IO) {
        try {
            val q = URLEncoder.encode(query, "UTF-8")

            // Step 1: find best matching Wikipedia page title
            val searchJson = JSONObject(
                get("https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$q&srlimit=1&format=json")
            )
            val results = searchJson.getJSONObject("query").getJSONArray("search")
            if (results.length() == 0) return@withContext null
            val title = results.getJSONObject(0).getString("title")

            // Step 2: get the short summary of that page
            val titleEnc = URLEncoder.encode(title, "UTF-8").replace("+", "%20")
            val summaryJson = JSONObject(
                get("https://en.wikipedia.org/api/rest_v1/page/summary/$titleEnc")
            )
            summaryJson.optString("extract").take(1200).ifEmpty { null }
        } catch (e: Exception) {
            Log.e(TAG, "Search failed for '$query'", e)
            null
        }
    }
    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        conn.setRequestProperty("User-Agent", "FridayAssistant/1.0 (Android)")
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

}