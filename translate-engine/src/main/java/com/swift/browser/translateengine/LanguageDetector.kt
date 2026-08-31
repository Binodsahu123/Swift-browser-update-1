package com.swift.browser.translateengine

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class LanguageDetector {

    suspend fun detectLanguage(text: String): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext "en"

        // Step 1: Rapid local script checks to bypass HTTP latency
        val sample = text.take(200)
        val script = detectScriptOffline(sample)
        if (script != null) {
            return@withContext script
        }

        // Step 2: Query Google Translate auto-detection endpoint
        try {
            val encodedQuery = URLEncoder.encode(text.take(150), "UTF-8")
            val urlString = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=en&dt=t&q=$encodedQuery"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8"))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                val jsonResponse = JSONArray(response.toString())
                // In Google's single API response, index 2 is usually the source language detected code string
                if (jsonResponse.length() > 2) {
                    val detectedLang = jsonResponse.optString(2)
                    if (!detectedLang.isNullOrBlank() && detectedLang != "null") {
                        return@withContext detectedLang
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("LanguageDetector", "Online language detection failed", e)
        }
        return@withContext "en" // Ultimate default
    }

    fun detectScriptOffline(text: String): String? {
        var devanagariCount = 0
        var CyrillicCount = 0
        var arabicCount = 0
        var hanCount = 0
        var bengaliCount = 0
        var totalCount = 0

        for (char in text) {
            if (char.isWhitespace()) continue
            totalCount++
            val block = Character.UnicodeBlock.of(char)
            when (block) {
                Character.UnicodeBlock.DEVANAGARI -> devanagariCount++
                Character.UnicodeBlock.BENGALI -> bengaliCount++
                Character.UnicodeBlock.CYRILLIC -> CyrillicCount++
                Character.UnicodeBlock.ARABIC -> arabicCount++
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
                Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION -> hanCount++
            }
        }

        if (totalCount == 0) return null

        val devRatio = devanagariCount.toFloat() / totalCount
        val benRatio = bengaliCount.toFloat() / totalCount
        val cyrRatio = CyrillicCount.toFloat() / totalCount
        val arabRatio = arabicCount.toFloat() / totalCount
        val hanRatio = hanCount.toFloat() / totalCount

        return when {
            devRatio > 0.2f -> "hi"
            benRatio > 0.2f -> "bn"
            cyrRatio > 0.2f -> "ru"
            arabRatio > 0.2f -> "ar"
            hanRatio > 0.2f -> "zh-CN"
            else -> null
        }
    }
}
