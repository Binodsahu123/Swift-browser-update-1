package com.swift.browser.desktopengine.viewport

import com.swift.browser.desktopengine.useragent.BrowserCompatibilityProfile

object DeviceMetricsManager {
    fun getMetricsScript(isDesktop: Boolean, profile: BrowserCompatibilityProfile? = null): String {
        return DeviceMetricsOverride.getMetricsOverrideScript(isDesktop, profile = profile)
    }
}
