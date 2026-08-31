package com.swift.browser.readerengine

import android.content.Context

interface ReaderEngine {
    fun parseArticle(html: String): ArticleSummary
}

data class ArticleSummary(
    val title: String,
    val content: String,
    val wordCount: Int,
    val estimatedReadTimeMinutes: Int
)

class ArticleExtractor : ReaderEngine {
    override fun parseArticle(html: String): ArticleSummary {
        // Strip tags simplistically
        val cleanContent = html.replace("<[^>]*>".toRegex(), " ").trim()
        val wordCount = cleanContent.split("\\s+".toRegex()).size
        val readTime = (wordCount / 200).coerceAtLeast(1)
        return ArticleSummary("Extracted Article", cleanContent, wordCount, readTime)
    }
}

class ReadingStats(private val context: Context) {
    fun recordReadSession(durationSeconds: Long) {
        // stats tracing
    }
}

class ReaderThemes {
    fun getThemeStyles(themeName: String): Map<String, String> {
        return mapOf(
            "bg" to if (themeName == "Dark") "#121212" else "#FDFBF7",
            "text" to if (themeName == "Dark") "#E0E0E0" else "#2C2C2C"
        )
    }
}
