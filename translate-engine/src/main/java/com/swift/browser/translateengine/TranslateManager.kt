package com.swift.browser.translateengine

import android.content.Context
import android.webkit.WebView

class TranslateManager(private val context: Context) {
    val settings = TranslateSettings(context)
    val languageManager = TranslateLanguageManager(context)
    val engine: AppTranslateEngine = AppTranslateEngine(context)
    val detector = LanguageDetector()
    val debugger = TranslationDebugger
    val stateManager = TranslationStateManager()
    val progressManager = TranslationProgressManager()

    init {
        engine.progressManager = progressManager
    }

    /**
     * Translates the active page inside a target WebView.
     */
    fun translateWebView(webView: WebView, targetLangCode: String, tabId: String, isDesktopMode: Boolean, onFinished: (Int) -> Unit) {
        debugger.targetLanguage = targetLangCode
        engine.translateActivePage(context, webView, targetLangCode, tabId, isDesktopMode) { count ->
            onFinished(count)
        }
    }

    /**
     * Detects page content language by sampling top characters or header texts.
     */
    suspend fun detectPageLanguage(sampleText: String): String {
        val code = detector.detectLanguage(sampleText)
        debugger.detectedLanguage = code
        return code
    }

    /**
     * End active loops or MutationObservers for a closed or reloaded tab.
     */
    fun finishSession(tabId: String) {
        engine.stopSession(tabId)
    }

    /**
     * Get real-time stats of the translation engine for overlays.
     */
    fun getDebuggerTelemetry(): String {
        return debugger.getTelemetrySummary()
    }
}
