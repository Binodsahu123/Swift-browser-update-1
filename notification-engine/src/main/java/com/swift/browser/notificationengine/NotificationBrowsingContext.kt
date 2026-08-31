package com.swift.browser.notificationengine

/**
 * Encapsulates browsing mode context for notification generation and history isolation.
 */
data class NotificationBrowsingContext(
    val isPrivate: Boolean = false,
    val privateSessionId: String? = null
) {
    companion object {
        val NORMAL = NotificationBrowsingContext(isPrivate = false)
        val PRIVATE = NotificationBrowsingContext(isPrivate = true)
    }
}
