package com.swift.browser.translateengine

import java.util.concurrent.ConcurrentHashMap

class TranslationCache {
    private val memoryCache = ConcurrentHashMap<String, String>()

    fun get(text: String, targetLang: String): String? {
        val key = buildKey(text, targetLang)
        return memoryCache[key]
    }

    fun put(text: String, targetLang: String, translated: String) {
        val key = buildKey(text, targetLang)
        memoryCache[key] = translated
    }

    fun putBatch(translations: Map<String, String>, targetLang: String) {
        translations.forEach { (text, translated) ->
            put(text, targetLang, translated)
        }
    }

    fun clear() {
        memoryCache.clear()
    }

    fun size(): Int {
        return memoryCache.size
    }

    private fun buildKey(text: String, targetLang: String): String {
        return "${text.trim()}::$targetLang"
    }
}
