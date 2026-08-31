package com.swift.browser.extensionengine

/**
 * Universal target representation for loading any Chrome Extension HTML page or surface.
 * Enforces single-source surface attributes and prevents lossy context propagation across views.
 */
data class ExtensionPageTarget(
    val extensionId: String,
    val surfaceType: ExtensionSurfaceType,
    val relativePath: String,
    val fullUrl: String,
    val tabId: String? = null,
    val windowId: String? = null,
    val frameId: Int = 0,
    val isPrivate: Boolean = false,
    val privateSessionId: String? = null,
    val source: String = "manifest",
    val openInTab: Boolean = false,
    val overrideType: String? = null,
    val pageType: String = surfaceType.name
)
