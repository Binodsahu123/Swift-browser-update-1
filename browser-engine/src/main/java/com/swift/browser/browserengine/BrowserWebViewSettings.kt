package com.swift.browser.browserengine

import android.content.Context
import android.os.Build
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

object BrowserWebViewSettings {

    fun applySettings(
        webView: WebView?,
        jsEnabled: Boolean = true,
        hwEnabled: Boolean = true,
        isDesktop: Boolean = false,
        isIncognito: Boolean = false
    ) {
        if (webView == null) return

        webView.isFocusable = true
        webView.isFocusableInTouchMode = true

        webView.settings.apply {
            javaScriptEnabled = jsEnabled
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false

            // Speed Boost: Force default high-speed caching and allow local database speeds up
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            setSupportZoom(true)

            // Universal access for files to allow seamless opening of local/offline/custom web assets
            // Security: Disable file URL access in normal browser tabs
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false

            // Security: Disable multiple windows opening automatically via script tags to stop rogue click popups
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false

            loadsImagesAutomatically = true
            blockNetworkImage = false
            // Mixed content: ALWAYS ALLOW to bypass secure origins issues on complex websites
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            setGeolocationEnabled(true)

            // Performance rendering boost: set layout algorithm to TEXT_AUTOSIZING for quicker painting
            layoutAlgorithm = if (isDesktop) {
                WebSettings.LayoutAlgorithm.NORMAL
            } else {
                WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
            }
            defaultTextEncodingName = "UTF-8"

            // Enable Offscreen pre-rasterization to compile/draw elements offscreen before they are in view
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val preRasterMethod = webView.javaClass.getMethod("setOffscreenPreRaster", Boolean::class.javaPrimitiveType)
                    preRasterMethod.invoke(webView, true)
                } catch (e: Exception) {
                    // fall back
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
        }

        // Apply Desktop/Mobile mode policy and configuration
        com.swift.browser.desktopengine.api.DesktopEngineProvider.api.configureWebViewSettings(webView, isDesktop)
        com.swift.browser.desktopengine.api.DesktopEngineProvider.api.injectDesktopRuntimeEnvironment(webView, isDesktop)

        // Enable third party cookies globally on normal cookie store to avoid login failures on high-security portals
        try {
            val cookieEngine = com.swift.browser.cookieengine.CookieEngineApi.getInstance(webView.context)
            if (!isIncognito) {
                cookieEngine.setupNormalCookies(webView)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (hwEnabled) {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        } else {
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }
    }
}
