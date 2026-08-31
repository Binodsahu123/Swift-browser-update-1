package com.swift.browser.privatemode

import android.content.Context
import android.util.Log

/**
 * Handles Web Notifications policy during Private Mode sessions.
 */
class PrivateNotificationPolicy {
    fun shouldAllowNotification(isPrivateSession: Boolean): Boolean {
        // Notifications requested during private browsing should be session-only and not persisted
        return true
    }
}

/**
 * Handles file downloads policy for Private Mode.
 */
class PrivateDownloadPolicy {
    fun getDownloadWarning(isPrivateSession: Boolean): String? {
        return if (isPrivateSession) {
            "Downloaded files will remain on your device after Private Mode is closed."
        } else null
    }
}

/**
 * Handles Web Extensions policy in Private Mode.
 */
class PrivateExtensionPolicy {
    fun isExtensionAllowedInPrivateMode(extensionId: String, allowedInIncognito: Boolean): Boolean {
        return allowedInIncognito
    }
}
