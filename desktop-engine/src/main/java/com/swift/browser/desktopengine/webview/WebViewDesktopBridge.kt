package com.swift.browser.desktopengine.webview

import android.webkit.WebView
import com.swift.browser.desktopengine.api.DesktopEngineProvider
import com.swift.browser.desktopengine.api.DesktopMode
import com.swift.browser.desktopengine.api.DesktopTransitionReason
import com.swift.browser.desktopengine.navigation.DesktopModeTransition
import com.swift.browser.desktopengine.navigation.DesktopNavigationPolicy
import com.swift.browser.desktopengine.useragent.UserAgentManager
import com.swift.browser.desktopengine.viewport.DesktopCssEnvironment
import com.swift.browser.desktopengine.viewport.DeviceMetricsManager
import com.swift.browser.desktopengine.viewport.DesktopViewportPolicy

object WebViewDesktopBridge {

    fun configureWebViewSettings(
        webView: WebView,
        isDesktop: Boolean,
        isJavaScriptEnabled: Boolean = true,
        isHardwareAccelerationEnabled: Boolean = true
    ) {
        val mode = if (isDesktop) DesktopMode.DESKTOP else DesktopMode.MOBILE
        val host = try { android.net.Uri.parse(webView.url.orEmpty()).host.orEmpty() } catch (_: Exception) { "" }
        DesktopWebViewConfigurator.configure(webView, host, mode, isJavaScriptEnabled)
    }

    fun injectDesktopRuntimeEnvironment(webView: WebView, isDesktop: Boolean) {
        val mode = if (isDesktop) DesktopMode.DESKTOP else DesktopMode.MOBILE
        DesktopViewportPolicy.apply(webView, mode)
        DesktopCssEnvironment.applyCompatibilityCss(webView, "", isDesktop)
        
        val runtime = com.swift.browser.desktopengine.useragent.WebViewRuntimeProfile.create(webView.context, isDesktop)
        val profile = com.swift.browser.desktopengine.useragent.BrowserCompatibilityProfile.fromRuntime(runtime, mode)
        val metricsScript = DeviceMetricsManager.getMetricsScript(isDesktop, profile)
        webView.evaluateJavascript(metricsScript, null)
    }

    fun applyConfiguration(webView: WebView, mode: DesktopMode) {
        val host = try { android.net.Uri.parse(webView.url.orEmpty()).host.orEmpty() } catch (_: Exception) { "" }
        DesktopWebViewConfigurator.configure(webView, host, mode)
        DesktopViewportPolicy.apply(webView, mode)
    }

    fun onNavigationStarted(
        tabId: String,
        url: String,
        webView: WebView
    ) {
        /*
         * Page start is an observation event.
         *
         * It MUST NOT start another DesktopModeTransition.
         *
         * The actual Desktop/Mobile transition is initiated only by
         * DesktopEngineApi when the user explicitly changes mode or when
         * an intentional top-level navigation requests a mode policy.
         */
        com.swift.browser.desktopengine.diagnostics.DesktopDiagnostics.recordNavigationStarted(
            tabId = tabId,
            url = url
        )
    }

    fun onPageFinished(tabId: String, url: String, webView: WebView) {
        DesktopModeTransition.onPageFinished(tabId, url, webView)
    }
}
