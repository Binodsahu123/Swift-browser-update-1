package com.swift.browser.translateengine

import android.content.Context
import org.json.JSONArray

class TranslateLanguageManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "swift_translate_settings",
        Context.MODE_PRIVATE
    )

    val languageMap = mapOf(
        "hi" to "Hindi",
        "es" to "Spanish",
        "fr" to "French",
        "de" to "German",
        "en" to "English",
        "ja" to "Japanese",
        "ar" to "Arabic",
        "bn" to "Bengali",
        "pt" to "Portuguese",
        "ru" to "Russian",
        "zh-CN" to "Chinese Simplified",
        "zh-TW" to "Chinese Traditional",
        "ur" to "Urdu",
        "tr" to "Turkish",
        "te" to "Telugu",
        "mr" to "Marathi",
        "ta" to "Tamil",
        "gu" to "Gujarati",
        "kn" to "Kannada",
        "ml" to "Malayalam",
        "pa" to "Punjabi",
        "or" to "Odia",
        "it" to "Italian",
        "ko" to "Korean",
        "vi" to "Vietnamese",
        "pl" to "Polish",
        "uk" to "Ukrainian",
        "th" to "Thai",
        "nl" to "Dutch",
        "sv" to "Swedish",
        "no" to "Norwegian",
        "fi" to "Finnish",
        "da" to "Danish",
        "el" to "Greek",
        "id" to "Indonesian",
        "ms" to "Malay"
    )

    fun getLanguageDisplayName(code: String): String {
        return languageMap[code] ?: code
    }

    fun getLanguageCode(displayName: String): String {
        return languageMap.entries.firstOrNull { it.value.equals(displayName, ignoreCase = true) }?.key ?: "en"
    }

    val availableLanguages = listOf(
        "English", "Hindi", "Tamil", "Telugu", "Bengali",
        "Marathi", "Gujarati", "Kannada", "Malayalam",
        "Punjabi", "Odia", "Urdu", "Spanish", "French",
        "German", "Japanese", "Chinese Simplified", "Arabic", "Russian",
        "Portuguese", "Italian", "Korean", "Vietnamese", "Polish",
        "Ukrainian", "Turkish", "Thai", "Dutch", "Swedish",
        "Norwegian", "Finnish", "Danish", "Greek", "Indonesian", "Malay"
    )

    fun getPreferredLanguages(): List<String> {
        val savedLanguagesJson = prefs.getString("preferred_languages_list", "[\"English\"]")
        return try {
            val array = JSONArray(savedLanguagesJson)
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
            if (list.isEmpty()) listOf("English") else list
        } catch (e: Exception) {
            listOf("English")
        }
    }

    fun savePreferredLanguages(list: List<String>) {
        val array = JSONArray()
        list.forEach { array.put(it) }
        prefs.edit().putString("preferred_languages_list", array.toString()).apply()
    }
}
