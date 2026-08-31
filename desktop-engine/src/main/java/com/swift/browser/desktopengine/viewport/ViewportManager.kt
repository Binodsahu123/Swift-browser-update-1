package com.swift.browser.desktopengine.viewport

object ViewportManager {
    private const val DESKTOP_WIDTH = 1280
    private const val DESKTOP_SCALE = 0.25f

    fun getDesktopViewportScript(width: Int = DESKTOP_WIDTH, scale: Float = DESKTOP_SCALE): String {
        return """
            (function() {
                var meta = document.querySelector('meta[name="viewport"]');
                if (!meta) {
                    meta = document.createElement('meta');
                    meta.name = 'viewport';
                    document.head.appendChild(meta);
                }
                meta.content = 'width=$width, initial-scale=$scale, maximum-scale=5.0, user-scalable=yes';
            })();
        """.trimIndent()
    }

    fun getMobileViewportRestoreScript(): String {
        return """
            (function() {
                var meta = document.querySelector('meta[name="viewport"]');
                if (meta) {
                    meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes';
                }
            })();
        """.trimIndent()
    }
}
