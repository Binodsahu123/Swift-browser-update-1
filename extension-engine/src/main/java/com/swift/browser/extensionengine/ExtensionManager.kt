package com.swift.browser.extensionengine

import android.content.Context
import android.net.Uri
import android.webkit.WebView

class ExtensionManager(
    private val context: Context,
    private val delegate: BrowserDelegate?
) {
    val engine = ExtensionEngineImpl(context, delegate)

    fun setupWebView(webView: WebView, tabId: String? = null) {
        engine.setupWebView(webView, tabId)
    }

    fun injectContentScripts(
        webView: WebView,
        url: String,
        runAt: String? = null,
        isPrivate: Boolean = false,
        privateSessionId: String? = null
    ) {
        engine.injectContentScripts(webView, url, runAt, isPrivate, privateSessionId)
    }

    suspend fun installExtension(uri: Uri): ParsedExtension {
        return engine.installExtension(uri)
    }

    suspend fun uninstallExtension(id: String) {
        engine.uninstallExtension(id)
    }

    suspend fun toggleExtension(id: String, enabled: Boolean) {
        engine.toggleExtension(id, enabled)
    }

    fun shutdown() {
        engine.shutdown()
    }
}
