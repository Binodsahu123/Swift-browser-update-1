package com.swift.browser.desktopengine.internal

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import com.swift.browser.desktopengine.api.DesktopDefaultMode
import com.swift.browser.desktopengine.api.DesktopEngineApi
import com.swift.browser.desktopengine.api.DesktopMode
import com.swift.browser.desktopengine.api.DesktopNavigationDecision
import com.swift.browser.desktopengine.api.DesktopTransitionReason
import com.swift.browser.desktopengine.diagnostics.DesktopDiagnostics
import com.swift.browser.desktopengine.diagnostics.DesktopModeEvent
import com.swift.browser.desktopengine.navigation.DesktopModeTransition
import com.swift.browser.desktopengine.navigation.DesktopNavigationPolicy
import com.swift.browser.desktopengine.rules.AutoDesktopPolicy
import com.swift.browser.desktopengine.rules.DesktopCompatibilityRepository
import com.swift.browser.desktopengine.rules.DesktopHostNormalizer
import com.swift.browser.desktopengine.rules.DesktopRepository
import com.swift.browser.desktopengine.rules.DesktopSiteRule
import com.swift.browser.desktopengine.state.DesktopModeState
import com.swift.browser.desktopengine.state.DesktopModeTransitionState
import com.swift.browser.desktopengine.state.DesktopSettingsState
import com.swift.browser.desktopengine.webview.WebViewDesktopBridge
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentHashMap

class DesktopEngineImpl : DesktopEngineApi {
    private var context: Context? = null
    private var repository: DesktopRepository? = null
    private var compatibilityRepository: DesktopCompatibilityRepository? = null

    private val tabStateMap = ConcurrentHashMap<String, MutableStateFlow<DesktopModeState>>()

    private val _settingsState = MutableStateFlow(DesktopSettingsState())
    override fun getSettingsState(): StateFlow<DesktopSettingsState> = _settingsState.asStateFlow()

    @Synchronized
    override fun initialize(context: Context) {
        if (this.context != null) return
        val appContext = context.applicationContext
        this.context = appContext
        this.repository = DesktopRepository(appContext)
        this.compatibilityRepository = DesktopCompatibilityRepository(appContext)

        updateSettingsState()
    }

    private fun getRepo(): DesktopRepository {
        val r = repository
        if (r != null) return r
        val ctx = context ?: throw IllegalStateException("DesktopEngineApi not initialized")
        val created = DesktopRepository(ctx)
        this.repository = created
        return created
    }

    private fun updateSettingsState() {
        val repo = repository ?: return
        val defaultMode = repo.getDefaultMode()
        val siteExceptions = repo.getSiteExceptions()
        _settingsState.value = DesktopSettingsState(
            defaultMode = defaultMode,
            siteExceptions = siteExceptions,
            autoModeEnabled = (defaultMode == DesktopDefaultMode.AUTO)
        )
    }

    override fun isDesktopMode(host: String): Boolean {
        return getMode("", host) == DesktopMode.DESKTOP
    }

    override fun getMode(tabId: String, host: String): DesktopMode {
        if (host.isEmpty()) return resolveDefaultMode()
        val repo = repository ?: return resolveDefaultMode()

        // 1. Explicit site override
        repo.getSiteMode(host)?.let { return it }

        // 2. User default or AUTO policy
        return resolveDefaultMode()
    }

    private fun resolveDefaultMode(): DesktopMode {
        val repo = repository
        val defaultMode = repo?.getDefaultMode() ?: DesktopDefaultMode.AUTO
        val ctx = context
        return if (ctx != null && defaultMode == DesktopDefaultMode.AUTO) {
            AutoDesktopPolicy.evaluate(ctx, defaultMode)
        } else {
            when (defaultMode) {
                DesktopDefaultMode.DESKTOP -> DesktopMode.DESKTOP
                DesktopDefaultMode.MOBILE -> DesktopMode.MOBILE
                DesktopDefaultMode.AUTO -> DesktopMode.MOBILE
            }
        }
    }

    override fun setMode(tabId: String, host: String, mode: DesktopMode) {
        if (host.isEmpty()) return
        val repo = getRepo()
        repo.setSiteMode(host, mode)
        updateSettingsState()

        if (tabId.isNotEmpty()) {
            val gen = DesktopModeTransition.getGeneration(tabId)
            val flow = getOrCreateTabFlow(tabId)
            flow.value = flow.value.copy(
                mode = mode,
                isDesktopModeEnabled = (mode == DesktopMode.DESKTOP),
                generation = gen
            )
        }
    }

    override fun applyMode(
        tabId: String,
        webView: WebView,
        host: String,
        targetMode: DesktopMode,
        reason: DesktopTransitionReason
    ) {
        val effectiveHost = if (host.isNotEmpty()) host else try { Uri.parse(webView.url.orEmpty()).host.orEmpty() } catch (_: Exception) { "" }
        val rule = compatibilityRepository?.getRuleForHost(effectiveHost)

        DesktopModeTransition.executeTransition(
            tabId = tabId,
            webView = webView,
            host = effectiveHost,
            targetMode = targetMode,
            reason = reason,
            rule = rule
        )
    }

    override fun toggleForSite(
        tabId: String,
        url: String,
        context: Context?,
        webView: WebView?
    ): Boolean {
        val host = try { Uri.parse(url).host.orEmpty() } catch (_: Exception) { "" }
        if (host.isEmpty()) return false

        if (context != null && this.context == null) {
            initialize(context)
        }

        val currentMode = getMode(tabId, host)
        val newMode = if (currentMode == DesktopMode.DESKTOP) DesktopMode.MOBILE else DesktopMode.DESKTOP

        setMode(tabId, host, newMode)

        if (webView != null) {
            applyMode(
                tabId = tabId,
                webView = webView,
                host = host,
                targetMode = newMode,
                reason = DesktopTransitionReason.USER_TOGGLE
            )
        } else {
            DesktopDiagnostics.recordEvent(DesktopModeEvent.ModeToggled(host, newMode == DesktopMode.DESKTOP))
        }

        return newMode == DesktopMode.DESKTOP
    }

    override fun toggleForCurrentSite(
        tabId: String,
        url: String,
        context: Context?,
        webView: WebView?
    ) {
        toggleForSite(tabId, url, context, webView)
    }

    override fun setDefaultMode(mode: DesktopDefaultMode) {
        val repo = getRepo()
        repo.setDefaultMode(mode)
        updateSettingsState()
    }

    override fun setSiteException(host: String, mode: DesktopMode) {
        setMode("", host, mode)
    }

    override fun removeSiteException(host: String) {
        val repo = getRepo()
        repo.removeSiteMode(host)
        updateSettingsState()
    }

    override fun getSiteRules(): Flow<List<DesktopSiteRule>> = flow {
        emit(emptyList())
    }

    override fun getTabState(tabId: String): StateFlow<DesktopModeState> {
        return getOrCreateTabFlow(tabId).asStateFlow()
    }

    override fun getTabTransitionState(tabId: String): StateFlow<DesktopModeTransitionState> {
        return DesktopModeTransition.getTabTransitionState(tabId)
    }

    override fun getTransitionGeneration(tabId: String): Long {
        return DesktopModeTransition.getGeneration(tabId)
    }

    override fun isTransitionActive(tabId: String): Boolean {
        return DesktopModeTransition.isTransitionActive(tabId)
    }

    override fun clearTab(tabId: String) {
        DesktopModeTransition.clearTab(tabId)
        tabStateMap.remove(tabId)
    }

    private fun getOrCreateTabFlow(tabId: String): MutableStateFlow<DesktopModeState> {
        return tabStateMap.computeIfAbsent(tabId) { MutableStateFlow(DesktopModeState()) }
    }

    override fun configureWebViewSettings(webView: WebView, isDesktop: Boolean) {
        WebViewDesktopBridge.configureWebViewSettings(webView, isDesktop)
    }

    override fun injectDesktopRuntimeEnvironment(webView: WebView, isDesktop: Boolean) {
        WebViewDesktopBridge.injectDesktopRuntimeEnvironment(webView, isDesktop)
    }

    override fun resolveDesktopUrl(urlStr: String, isDesktop: Boolean): String {
        return DesktopNavigationPolicy.resolveDesktopUrl(urlStr, isDesktop)
    }

    override fun resolveNavigationTarget(
        tabId: String,
        currentUrl: String,
        requestedUrl: String,
        source: String
    ): DesktopNavigationDecision {
        val host = try { Uri.parse(requestedUrl).host.orEmpty() } catch (_: Exception) { "" }
        val mode = getMode(tabId, host)
        val isDesktop = (mode == DesktopMode.DESKTOP)
        val resolvedUrl = resolveDesktopUrl(requestedUrl, isDesktop)
        return DesktopNavigationDecision(
            finalUrl = resolvedUrl,
            shouldLoad = true,
            shouldTransition = false,
            targetMode = mode,
            reason = "Resolved for source $source with mode $mode"
        )
    }
}
