package com.swift.browser.extensionengine

enum class ExtensionSurfaceType {
    ACTION_POPUP,
    SIDE_PANEL,
    OPTIONS_PAGE,
    DEVTOOLS_PANEL,
    URL_OVERRIDE,
    BACKGROUND_WORKER,
    SERVICE_WORKER,
    BACKGROUND_PAGE,
    BACKGROUND_SCRIPTS,
    CONTENT_SCRIPT,
    ACTION_ONLY,
    NONE
}

data class ResolvedExtensionSurface(
    val surfaceType: ExtensionSurfaceType,
    val relativePath: String,
    val fullUrl: String,
    val extensionId: String,
    val openInTab: Boolean = false,
    val overrideType: String? = null,
    val isVisibleUi: Boolean = when (surfaceType) {
        ExtensionSurfaceType.ACTION_POPUP,
        ExtensionSurfaceType.SIDE_PANEL,
        ExtensionSurfaceType.OPTIONS_PAGE,
        ExtensionSurfaceType.DEVTOOLS_PANEL,
        ExtensionSurfaceType.URL_OVERRIDE -> true
        else -> false
    }
) {
    fun toPageTarget(
        tabId: String? = null,
        windowId: String? = null,
        isPrivate: Boolean = false,
        privateSessionId: String? = null
    ): ExtensionPageTarget {
        return ExtensionPageTarget(
            extensionId = extensionId,
            surfaceType = surfaceType,
            relativePath = relativePath,
            fullUrl = fullUrl,
            tabId = tabId,
            windowId = windowId,
            isPrivate = isPrivate,
            privateSessionId = privateSessionId,
            openInTab = openInTab,
            overrideType = overrideType
        )
    }
}
