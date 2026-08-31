package com.swift.browser.translateengine

import android.content.Context
import android.webkit.WebView

interface TranslateEngine {
    /**
     * Translates a single text segment.
     */
    suspend fun translateText(text: String, srcLang: String, targetLang: String): String

    /**
     * Performs clean, background, in-place visual transition of Web Page to foreign languages.
     */
    fun translateActivePage(context: Context, webView: WebView, targetLangCode: String, tabId: String, isDesktopMode: Boolean, onFinished: (Int) -> Unit)
}

class AppTranslateEngine(private val context: Context) : TranslateEngine {
    private val providerAdapter = GoogleTranslateBridge()
    private val coordinator = TranslationCoordinator(context, providerAdapter)

    var progressManager: TranslationProgressManager? = null
        set(value) {
            field = value
            coordinator.progressManager = value
        }

    override suspend fun translateText(text: String, srcLang: String, targetLang: String): String {
        return providerAdapter.translate(text, srcLang, targetLang)
    }

    override fun translateActivePage(
        context: Context,
        webView: WebView,
        targetLangCode: String,
        tabId: String,
        isDesktopMode: Boolean,
        onFinished: (Int) -> Unit
    ) {
        coordinator.translatePage(webView, targetLangCode, tabId, isDesktopMode, onFinished)
    }

    fun stopSession(tabId: String) {
        coordinator.stopSession(tabId)
    }
}
