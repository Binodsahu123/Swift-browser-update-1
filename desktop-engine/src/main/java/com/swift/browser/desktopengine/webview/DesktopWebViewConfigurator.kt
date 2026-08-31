package com.swift.browser.desktopengine.webview

import android.webkit.WebSettings
import android.webkit.WebView
import com.swift.browser.desktopengine.api.DesktopMode
import com.swift.browser.desktopengine.useragent.UserAgentPolicy

object DesktopWebViewConfigurator {

    fun configure(
        webView: WebView,
        host: String,
        mode: DesktopMode,
        isJavaScriptEnabled: Boolean = true
    ) {
        val settings = webView.settings
        val isDesktop = (mode == DesktopMode.DESKTOP)

        settings.javaScriptEnabled = isJavaScriptEnabled
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true

        if (isDesktop) {
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
        } else {
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = false
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
        }

        val targetUA = UserAgentPolicy.resolveUserAgent(host, mode, webView.context)
        if (settings.userAgentString != targetUA) {
            settings.userAgentString = targetUA
        }
    }
}
