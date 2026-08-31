package com.swift.browser.adblockengine.onlinemusic

/**
 * Declares domain filter scope overrides so music stream endpoints never get blocked.
 */
object OnlineMusicFilterScope {
    private val criticalMusicDomains = setOf(
        "pandora.com", "tidal.com", "apple.com", "iheart.com"
    )

    fun isCriticalMusicDomain(url: String): Boolean {
        return try {
            val host = android.net.Uri.parse(url).host?.lowercase() ?: ""
            criticalMusicDomains.any { host.contains(it) }
        } catch (e: Exception) {
            false
        }
    }
}
