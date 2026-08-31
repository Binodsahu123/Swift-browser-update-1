package com.swift.browser.permissionengine

data class PermissionRequestModel(
    val requestId: String,
    val origin: String,
    val siteUrl: String,
    val pageUrl: String,
    val frameId: String,
    val tabId: String,
    val requestSourceType: String, // "website", "extension", "browser_ui", "download_flow", etc.
    val permissionType: String, // "CAMERA", "MICROPHONE", "LOCATION", "NOTIFICATIONS", "STORAGE", "CLIPBOARD", "COOKIES", "DOWNLOADS", "FILE_UPLOAD", "PROTECTED_MEDIA"
    val resourcesRequested: List<String>,
    val isUserGesture: Boolean? = null,
    val isTopLevelFrame: Boolean = true,
    val isSecureOrigin: Boolean = true,
    val isIncognito: Boolean = false,
    val riskLevel: String, // "Low", "Medium", "High"
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap(),
    val requestedByExtensionId: String? = null,
    val requestedByExtensionName: String? = null
)
