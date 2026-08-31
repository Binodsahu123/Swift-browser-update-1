package com.swift.browser.adblockengine.onlinemusic

/**
 * Handles evaluation policies specific to online music player instances.
 */
object OnlineMusicRequestPolicy {
    fun shouldBlockInMusicScope(url: String): Boolean {
        // If it's a critical media stream domain, we never block it to preserve playback stability
        if (OnlineMusicFilterScope.isCriticalMusicDomain(url)) {
            // Only block explicit ads and logs, never actual audio chunks
            val lower = url.lowercase()
            if (lower.contains("/ad/") || lower.contains("/pagead/") || lower.contains("doubleclick")) {
                return true
            }
            return false
        }
        return true
    }
}
