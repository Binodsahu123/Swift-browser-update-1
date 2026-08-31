package com.swift.browser.desktopengine.rules

data class DesktopSiteRule(
    val domain: String,
    val forceDesktopUA: Boolean = true,
    val viewportWidth: Int = 1280,
    val forceScale: Float = 0.25f,
    val desktopSubdomainRewrite: String? = null,
    val customCssOverrides: String? = null,
    val customJsOverrides: String? = null,
    val fallbackAllowed: Boolean = false
)
