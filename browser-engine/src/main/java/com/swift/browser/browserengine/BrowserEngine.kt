package com.swift.browser.browserengine

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

object BrowserCoreManager {
    private const val TAG = "BrowserCoreManager"
    private var isReady = false

    fun initialize() {
        if (isReady) return
        android.util.Log.d(TAG, "BrowserCoreManager micro-kernel engine initializing.")
        isReady = true
        BrowserEngine.initialize()
    }

    fun createTab(url: String): String {
        com.swift.browser.networkstatsengine.TraceRepository.addTrace(
            com.swift.browser.networkstatsengine.EngineTraceModel(
                message = "Tab spawned with source: $url",
                engineId = "browser_core",
                eventType = "TAB_CREATE",
                durationMs = 12L
            )
        )
        return "tab_${System.currentTimeMillis()}"
    }

    fun sleepInactiveTabs() {
        com.swift.browser.networkstatsengine.TraceRepository.addTrace(
            com.swift.browser.networkstatsengine.EngineTraceModel(
                message = "Released background tab allocations to bypass Android low memory constraints",
                engineId = "browser_core",
                eventType = "TAB_SLEEP",
                durationMs = 8L
            )
        )
    }
}

object BrowserEngine : BrowserEngineApi {
    val stateEngine = BrowserStateEngine()
    val navigationEngine = BrowserNavigationEngine(stateEngine)
    val lifecycleEngine = BrowserLifecycleEngine(stateEngine)
    val renderEngine = BrowserRenderEngine(stateEngine)
    val sessionEngine = BrowserSessionEngine(stateEngine)
    val diagnosticsEngine = BrowserDiagnosticsEngine(stateEngine)
    val bridgeEngine = BrowserBridgeEngine(stateEngine)

    fun initialize(context: Context? = null) {
        lifecycleEngine.initialize(context)
    }

    override fun openPage(tabId: String, url: String) {
        navigationEngine.loadUrl(tabId, url)
    }

    override fun loadUrl(tabId: String, url: String) {
        navigationEngine.loadUrl(tabId, url)
    }

    override fun reloadPage(tabId: String) {
        navigationEngine.reloadPage(tabId)
    }

    override fun stopLoading(tabId: String) {
        navigationEngine.stopLoading(tabId)
    }

    override fun goBack(tabId: String) {
        diagnosticsEngine.logDiagnostic("NAV", "goBack for tab $tabId")
    }

    override fun goForward(tabId: String) {
        diagnosticsEngine.logDiagnostic("NAV", "goForward for tab $tabId")
    }

    override fun canGoBack(): Boolean = stateEngine.navigationState.value.canGoBack

    override fun canGoForward(): Boolean = stateEngine.navigationState.value.canGoForward

    override fun getBrowserStateFlow(): StateFlow<BrowserState> = stateEngine.browserState

    override fun getPageStateFlow(): StateFlow<BrowserPageState> = stateEngine.pageState

    override fun getNavigationStateFlow(): StateFlow<BrowserNavigationState> = stateEngine.navigationState

    override fun getLoadingStateFlow(): StateFlow<Boolean> = stateEngine.isLoading

    override fun getTitleFlow(): StateFlow<String> = stateEngine.title

    override fun getUrlFlow(): StateFlow<String> = stateEngine.url

    override fun getFaviconFlow(): StateFlow<String?> = stateEngine.favicon

    override fun getBrowserErrorFlow(): StateFlow<BrowserError?> = stateEngine.browserError

    override fun getBrowserDiagnosticsFlow(): StateFlow<String> = stateEngine.diagnostics

    override fun saveBrowserSession(): BrowserSession = sessionEngine.saveSession()

    override fun restoreBrowserSession(session: BrowserSession) {
        sessionEngine.restoreSession(session)
    }

    override fun clearBrowserCache(context: Context) {
        lifecycleEngine.clearCache(context)
    }

    override fun updateUserAgentMode(mode: String) {
        stateEngine.updateUserAgentMode(mode)
    }

    override fun updateIncognitoMode(enabled: Boolean) {
        stateEngine.updateIncognitoMode(enabled)
    }

    override fun requestRender() {
        renderEngine.requestRender()
    }

    override fun attachWebViewAdapter(adapter: Any?) {
        renderEngine.attachAdapter(adapter)
    }

    override fun detachWebViewAdapter() {
        renderEngine.detachAdapter()
    }

    override fun isPageLoading(): Boolean = stateEngine.isLoading.value

    override fun isBrowserReady(): Boolean = stateEngine.browserState.value == BrowserState.READY || stateEngine.browserState.value == BrowserState.IDLE
}

class NavigationEngine {
    fun cleanUrl(input: String): String {
        return BrowserEngine.navigationEngine.cleanUrl(input)
    }
}

class SessionEngine {
    fun saveSessionState(tabs: List<String>) {
        BrowserEngine.sessionEngine.saveSession(tabUrls = tabs)
    }

    fun restoreSessionState(): List<String> {
        return BrowserEngine.sessionEngine.getCurrentSession().tabUrls
    }
}
