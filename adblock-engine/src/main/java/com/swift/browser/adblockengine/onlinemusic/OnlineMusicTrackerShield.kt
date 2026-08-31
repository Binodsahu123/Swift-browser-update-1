package com.swift.browser.adblockengine.onlinemusic

import com.swift.browser.adblockengine.network.AdBlockTrackerDetector

/**
 * Handles tracker shielding on music resource channels while ensuring player components don't crash.
 */
object OnlineMusicTrackerShield {
    fun evaluateTrackerBlock(url: String): Boolean {
        // Run standard tracker block check but ignore if it's Spotify or Soundcloud core APIs
        if (OnlineMusicFilterScope.isCriticalMusicDomain(url)) {
            val lower = url.lowercase()
            if (lower.contains("/api/tracker") || lower.contains("/log_event")) {
                return true
            }
            return false // skip blocking API endpoints to preserve playback
        }
        return AdBlockTrackerDetector.isTracker(url)
    }
}
