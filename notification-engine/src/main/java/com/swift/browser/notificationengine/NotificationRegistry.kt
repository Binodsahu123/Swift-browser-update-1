package com.swift.browser.notificationengine

import android.util.Log

object NotificationRegistry {
    private const val TAG = "NotificationRegistry"

    // Maps website base URLs/domains to their corresponding standard RSS/Atom feed URLs
    private val PRESET_FEEDS = mapOf(
        "aajtak.in" to "https://www.aajtak.in/rss/news-feed",
        "abplive.com" to "https://www.abplive.com/home/feed",
        "ndtv.com" to "https://feeds.feedburner.com/ndtvnews-top-stories",
        "bbc.com" to "https://feeds.bbci.co.uk/news/rss.xml",
        "bbc.co.uk" to "https://feeds.bbci.co.uk/news/rss.xml",
        "facebook.com" to "https://newsroom.fb.com/feed/",
        "instagram.com" to "https://about.instagram.com/blog/rss",
        "indiatoday.in" to "https://www.indiatoday.in/rss/home"
    )

    /**
     * Resolves an RSS feed URL for a given website URL.
     * Checks prests first, falls back to parsing/detection or basic guessing.
     */
    fun resolveRssUrl(websiteUrl: String): String {
        val host = getHostDomain(websiteUrl)
        
        // Check exact or partial preset match
        for ((key, feed) in PRESET_FEEDS) {
            if (host.contains(key) || key.contains(host)) {
                return feed
            }
        }

        // Standard fallbacks based on common domain structures
        val cleanUrl = websiteUrl.trimEnd('/')
        return when {
            cleanUrl.endsWith(".xml") || cleanUrl.endsWith(".rss") -> cleanUrl
            else -> "$cleanUrl/feed" // common default for WordPress and other CMS systems
        }
    }

    fun getHostDomain(url: String): String {
        return try {
            val uri = java.net.URI(url)
            val host = uri.host ?: ""
            if (host.startsWith("www.")) host.substring(4) else host
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing host from $url: ${e.message}")
            url
        }
    }

    /**
     * Helper to check support labels for the technical report/evaluation.
     */
    fun getWebsitesSupportStatus(): Map<String, String> {
        return mapOf(
            "AajTak" to "SUPPORTED (RSS Feed Integrated: https://www.aajtak.in/rss/news-feed)",
            "ABP" to "SUPPORTED (RSS Feed Integrated: https://www.abplive.com/home/feed)",
            "NDTV" to "SUPPORTED (RSS Feed Integrated: https://feeds.feedburner.com/ndtvnews-top-stories)",
            "BBC" to "SUPPORTED (RSS Feed Integrated: https://feeds.bbci.co.uk/news/rss.xml)",
            "Facebook" to "PARTIAL (Supported via Facebook Newsroom RSS, personal user-specific notifications require active app sessions)",
            "Instagram" to "PARTIAL (Supported via official Instagram Blog RSS updates, real-time activity not supported on standard offline WebViews)",
            "X" to "NOT POSSIBLE (Requires active Push integration or proprietary background sockets on strict proprietary domains)"
        )
    }
}
