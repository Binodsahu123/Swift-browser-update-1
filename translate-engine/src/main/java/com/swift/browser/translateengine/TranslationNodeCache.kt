package com.swift.browser.translateengine

class TranslationNodeCache {
    data class CacheEntry(
        val nodeId: String,
        val originalText: String,
        val translatedText: String,
        val language: String
    )

    private val cache = java.util.concurrent.ConcurrentHashMap<String, CacheEntry>()

    fun put(nodeId: String, originalText: String, translatedText: String, language: String) {
        cache[nodeId] = CacheEntry(nodeId, originalText, translatedText, language)
    }

    fun get(nodeId: String): CacheEntry? {
        return cache[nodeId]
    }

    fun clear() {
        cache.clear()
    }
}
