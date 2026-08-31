package com.swift.browser.browserengine

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

interface BrowserEngineApi {
    fun openPage(tabId: String, url: String)
    fun loadUrl(tabId: String, url: String)
    fun reloadPage(tabId: String)
    fun stopLoading(tabId: String)
    fun goBack(tabId: String)
    fun goForward(tabId: String)
    fun canGoBack(): Boolean
    fun canGoForward(): Boolean
    fun getBrowserStateFlow(): StateFlow<BrowserState>
    fun getPageStateFlow(): StateFlow<BrowserPageState>
    fun getNavigationStateFlow(): StateFlow<BrowserNavigationState>
    fun getLoadingStateFlow(): StateFlow<Boolean>
    fun getTitleFlow(): StateFlow<String>
    fun getUrlFlow(): StateFlow<String>
    fun getFaviconFlow(): StateFlow<String?>
    fun getBrowserErrorFlow(): StateFlow<BrowserError?>
    fun getBrowserDiagnosticsFlow(): StateFlow<String>
    fun saveBrowserSession(): BrowserSession
    fun restoreBrowserSession(session: BrowserSession)
    fun clearBrowserCache(context: Context)
    fun updateUserAgentMode(mode: String)
    fun updateIncognitoMode(enabled: Boolean)
    fun requestRender()
    fun attachWebViewAdapter(adapter: Any?)
    fun detachWebViewAdapter()
    fun isPageLoading(): Boolean
    fun isBrowserReady(): Boolean
}
