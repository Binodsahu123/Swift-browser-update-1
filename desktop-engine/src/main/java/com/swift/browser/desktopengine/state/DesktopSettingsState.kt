package com.swift.browser.desktopengine.state

import com.swift.browser.desktopengine.api.DesktopDefaultMode
import com.swift.browser.desktopengine.api.DesktopMode

data class DesktopSettingsState(
    val defaultMode: DesktopDefaultMode = DesktopDefaultMode.AUTO,
    val siteExceptions: Map<String, DesktopMode> = emptyMap(),
    val autoModeEnabled: Boolean = true,
    val currentSite: String = "",
    val currentSiteMode: DesktopMode = DesktopMode.MOBILE,
    val currentSiteHasOverride: Boolean = false,
    val availableModes: List<DesktopMode> = listOf(DesktopMode.MOBILE, DesktopMode.DESKTOP)
)
