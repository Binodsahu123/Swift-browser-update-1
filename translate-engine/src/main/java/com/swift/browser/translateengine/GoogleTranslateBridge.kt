package com.swift.browser.translateengine

import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class GoogleTranslateBridge : TranslationProvider {
    override val name: String = "Google Translate"

    override suspend fun translate(text: String, srcLang: String, targetLang: String): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext ""
        try {
            val encodedQuery = URLEncoder.encode(text, "UTF-8")
            val urlString = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$srcLang&tl=$targetLang&dt=t&q=$encodedQuery"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8"))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                val jsonResponse = JSONArray(response.toString())
                val sentenceArray = jsonResponse.optJSONArray(0)
                if (sentenceArray != null) {
                    val result = StringBuilder()
                    for (i in 0 until sentenceArray.length()) {
                        val element = sentenceArray.optJSONArray(i)
                        val translatedText = element?.optString(0)
                        if (translatedText != null && translatedText != "null") {
                            result.append(translatedText)
                        }
                    }
                    return@withContext result.toString()
                }
            } else {
                Log.e("GoogleTranslateBridge", "Server returned HTTP status: $responseCode")
            }
        } catch (e: Exception) {
            Log.e("GoogleTranslateBridge", "Translation failed for: $text", e)
        }
        return@withContext text // Fallback to original text on error
    }

    override suspend fun translateBatch(
        texts: List<String>,
        srcLang: String,
        targetLang: String
    ): Map<String, String> = coroutineScope {
        // Translate a batch in parallel using custom coroutine Dispatchers.IO allocation
        // To avoid freezing the UI and maximize speeds
        val deferredList = texts.map { text ->
            async(Dispatchers.IO) {
                text to translate(text, srcLang, targetLang)
            }
        }
        deferredList.awaitAll().toMap()
    }
}
