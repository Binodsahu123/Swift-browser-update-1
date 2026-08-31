package com.swift.browser.desktopengine.state

import com.swift.browser.desktopengine.api.DesktopMode

data class DesktopSiteState(
    val host: String = "",
    val mode: DesktopMode = DesktopMode.MOBILE,
    val isExplicitOverride: Boolean = false,
    val inheritedFromDefault: Boolean = true,
    val lastTransitionAt: Long = 0L,
    val lastAppliedUserAgent: String = "",
    val lastAppliedViewportWidth: Int = 1280,
    val isApplying: Boolean = false,
    val transitionGeneration: Int = 0
)
