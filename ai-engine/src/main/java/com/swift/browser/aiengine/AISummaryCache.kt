package com.swift.browser.aiengine

import java.util.concurrent.ConcurrentHashMap

data class SummaryCacheItem(
    val url: String,
    val title: String,
    val pageType: PageType,
    val summaryPrompt: String,
    val keyPointsPrompt: String,
    val factCheckPrompt: String,
    val translationStateCode: String?, // If translated, e.g. "hi"
    val languageStateName: String, // Detected language name
    val analysisResult: AIAnalysisResult?
)

object AISummaryCache {
    private val cacheMap = ConcurrentHashMap<String, SummaryCacheItem>()

    fun put(url: String, item: SummaryCacheItem) {
        val cleanUrl = cleanUrl(url)
        cacheMap[cleanUrl] = item
    }

    fun get(url: String): SummaryCacheItem? {
        val cleanUrl = cleanUrl(url)
        return cacheMap[cleanUrl]
    }

    fun clear() {
        cacheMap.clear()
    }

    private fun cleanUrl(url: String): String {
        return try {
            val uri = android.net.Uri.parse(url)
            val builder = uri.buildUpon()
            // clear dynamic fragments/tracking codes if appropriate for more generalized caching
            builder.fragment(null).build().toString()
        } catch (e: Exception) {
            url
        }
    }
}
