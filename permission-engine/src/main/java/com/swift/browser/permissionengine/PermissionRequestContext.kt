package com.swift.browser.permissionengine

data class PermissionRequestContext(
    val requestId: String = java.util.UUID.randomUUID().toString(),
    val tabId: String,
    val origin: String,
    val pageUrl: String,
    val frameId: String? = null,
    val isMainFrame: Boolean = true,
    val isUserGesture: Boolean? = null, // null = UNKNOWN
    val isIncognito: Boolean = false,
    val requestSource: String = "website",
    val timestamp: Long = System.currentTimeMillis()
)
