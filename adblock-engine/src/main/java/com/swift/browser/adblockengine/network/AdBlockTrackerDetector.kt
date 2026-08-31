package com.swift.browser.adblockengine.network

/**
 * Checks for well-known trackers or analytics keywords in the URL pattern.
 */
object AdBlockTrackerDetector {
    private val trackerKeywords = listOf(
        "telemetry", "analytics", "tracking", "metrics", "amplitude",
        "doubleclick", "adservice", "google-analytics", "hotjar", "mixpanel"
    )

    fun isTracker(url: String): Boolean {
        val lower = url.lowercase()
        return trackerKeywords.any { lower.contains(it) }
    }
}
