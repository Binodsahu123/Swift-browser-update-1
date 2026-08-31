package com.swift.browser.desktopengine.api

import android.content.Context
import android.webkit.WebView
import com.swift.browser.desktopengine.rules.DesktopSiteRule
import com.swift.browser.desktopengine.state.DesktopModeState
import com.swift.browser.desktopengine.state.DesktopModeTransitionState
import com.swift.browser.desktopengine.state.DesktopSettingsState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class DesktopMode {
    MOBILE,
    DESKTOP
}

enum class DesktopDefaultMode {
    AUTO,
    MOBILE,
    DESKTOP
}

enum class DesktopTransitionReason {
    USER_TOGGLE,
    DEFAULT_POLICY,
    SITE_EXCEPTION,
    NEW_NAVIGATION,
    RESTORE,
    RECOVERY
}

data class DesktopNavigationDecision(
    val finalUrl: String,
    val shouldLoad: Boolean = true,
    val shouldTransition: Boolean = false,
    val targetMode: DesktopMode = DesktopMode.MOBILE,
    val reason: String = ""
)

interface DesktopEngineApi {
    fun initialize(context: Context)

    fun isDesktopMode(host: String): Boolean

    fun getMode(tabId: String = "", host: String): DesktopMode

    fun setMode(tabId: String = "", host: String, mode: DesktopMode)

    fun applyMode(
        tabId: String = "",
        webView: WebView,
        host: String = "",
        targetMode: DesktopMode,
        reason: DesktopTransitionReason = DesktopTransitionReason.USER_TOGGLE
    )

    fun toggleForSite(
        tabId: String = "",
        url: String,
        context: Context? = null,
        webView: WebView? = null
    ): Boolean

    fun toggleForCurrentSite(
        tabId: String = "",
        url: String,
        context: Context? = null,
        webView: WebView? = null
    )

    fun setDefaultMode(mode: DesktopDefaultMode)

    fun setSiteException(host: String, mode: DesktopMode)

    fun removeSiteException(host: String)

    fun getSiteRules(): Flow<List<DesktopSiteRule>>

    fun getSettingsState(): StateFlow<DesktopSettingsState>

    fun getTabState(tabId: String): StateFlow<DesktopModeState>

    fun getTabTransitionState(tabId: String): StateFlow<DesktopModeTransitionState>

    fun getTransitionGeneration(tabId: String): Long = 0L

    fun isTransitionActive(tabId: String = ""): Boolean = false

    fun clearTab(tabId: String) {}

    fun configureWebViewSettings(webView: WebView, isDesktop: Boolean)

    fun injectDesktopRuntimeEnvironment(webView: WebView, isDesktop: Boolean)

    fun resolveDesktopUrl(urlStr: String, isDesktop: Boolean): String

    fun resolveNavigationTarget(
        tabId: String = "",
        currentUrl: String = "",
        requestedUrl: String,
        source: String = ""
    ): DesktopNavigationDecision
}
