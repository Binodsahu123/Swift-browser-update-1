package com.swift.browser.desktopengine.viewport

import android.webkit.WebView
import com.swift.browser.desktopengine.api.DesktopMode

data class ViewportConfig(
    val width: Int = 1280,
    val initialScale: Float = 0.25f,
    val minScale: Float = 0.1f,
    val maxScale: Float = 5.0f,
    val userScalable: Boolean = true
)

object DesktopViewportPolicy {
    var defaultDesktopConfig = ViewportConfig(width = 1280, initialScale = 0.25f)
    var defaultMobileConfig = ViewportConfig(width = -1, initialScale = 1.0f) // -1 means device-width

    fun apply(webView: WebView, mode: DesktopMode, customConfig: ViewportConfig? = null) {
        val config = customConfig ?: if (mode == DesktopMode.DESKTOP) defaultDesktopConfig else defaultMobileConfig
        val isDesktop = (mode == DesktopMode.DESKTOP)

        webView.settings.apply {
            useWideViewPort = true
            loadWithOverviewMode = isDesktop
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }

        val script = if (isDesktop) {
            ViewportManager.getDesktopViewportScript(config.width, config.initialScale)
        } else {
            ViewportManager.getMobileViewportRestoreScript()
        }

        webView.post {
            webView.evaluateJavascript(script, null)
        }
    }
}
