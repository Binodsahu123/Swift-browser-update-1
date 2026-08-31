package com.swift.browser.desktopengine.viewport

import com.swift.browser.desktopengine.useragent.BrowserCompatibilityProfile

object DeviceMetricsOverride {
    fun getMetricsOverrideScript(
        isDesktop: Boolean,
        screenWidth: Int = 1920,
        screenHeight: Int = 1080,
        profile: BrowserCompatibilityProfile? = null
    ): String {
        val platformStr = profile?.platform ?: if (isDesktop) "Win32" else "Linux armv8l"
        val maxTouchPointsVal = if (isDesktop) 0 else 5

        if (!isDesktop) {
            return """
                (function() {
                    try {
                        delete window.screen.width;
                        delete window.screen.height;
                        delete window.screen.availWidth;
                        delete window.screen.availHeight;
                        delete window.devicePixelRatio;
                    } catch(e) {}
                })();
            """.trimIndent()
        }

        return """
            (function() {
                try {
                    Object.defineProperty(window.screen, 'width', { get: function() { return $screenWidth; }, configurable: true });
                    Object.defineProperty(window.screen, 'height', { get: function() { return $screenHeight; }, configurable: true });
                    Object.defineProperty(window.screen, 'availWidth', { get: function() { return $screenWidth; }, configurable: true });
                    Object.defineProperty(window.screen, 'availHeight', { get: function() { return $screenHeight; }, configurable: true });
                    Object.defineProperty(window, 'devicePixelRatio', { get: function() { return 1.0; }, configurable: true });
                    Object.defineProperty(navigator, 'maxTouchPoints', { get: function() { return $maxTouchPointsVal; }, configurable: true });
                    Object.defineProperty(navigator, 'platform', { get: function() { return '$platformStr'; }, configurable: true });
                } catch(e) {
                    console.error("DeviceMetricsOverride failure: " + e.message);
                }
            })();
        """.trimIndent()
    }
}
