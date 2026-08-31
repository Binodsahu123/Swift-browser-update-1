package com.swift.browser.privatemode

import android.util.Log

/**
 * Manages privacy constraints for media playback during Private Mode sessions.
 */
class PrivateMediaManager {

    companion object {
        private const val TAG = "PrivateMediaManager"
    }

    /**
     * Determines if media playback session metadata can be published to external media controllers.
     */
    fun shouldPublishMediaSessionMetadata(isPrivateSession: Boolean): Boolean {
        // In private mode, suppress detailed title/artist metadata from leaking to persistent system controls if necessary
        return !isPrivateSession
    }

    fun onMediaPlaybackStarted(tabId: String, isPrivate: Boolean) {
        if (isPrivate) {
            Log.i(TAG, "Private media playback started for tab $tabId - metadata masked")
        }
    }
}
