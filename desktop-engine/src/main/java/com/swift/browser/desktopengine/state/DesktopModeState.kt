package com.swift.browser.desktopengine.state

import com.swift.browser.desktopengine.api.DesktopMode

data class DesktopModeState(
    val mode: DesktopMode = DesktopMode.MOBILE,
    val isDesktopModeEnabled: Boolean = false,
    val generation: Long = 0L,
    val appliedUserAgent: String = "",
    val viewportWidth: Int = 1280,
    val initialScale: Float = 0.25f,
    val devicePixelRatioOverride: Float = 1.0f,
    val isUrlRewritten: Boolean = false,
    val cssOverridesApplied: Boolean = false,
    val isVideoStatePreserved: Boolean = false,
    val lastLoadedUrl: String = ""
)
