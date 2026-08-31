package com.swift.browser.desktopengine.useragent

import com.swift.browser.desktopengine.api.DesktopMode

/**
 * Registry policy to dynamically select and format the appropriate User Agent string
 * and layout properties for any given target website and mode, driven by the real detected version.
 */
object WebCompatibilityMatrix {

    /**
     * Resolves user-agent string for a host dynamically under the selected mode.
     * All standards-compliant websites use the generic compatibility path driven by the real detected WebView version.
     */
    fun resolveUserAgent(host: String, mode: DesktopMode, webViewVersion: String): String {
        return when (mode) {
            DesktopMode.DESKTOP -> {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$webViewVersion Safari/537.36"
            }
            DesktopMode.MOBILE -> {
                "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$webViewVersion Mobile Safari/537.36"
            }
        }
    }
}

