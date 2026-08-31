package com.swift.browser.browserengine

import android.util.Log
import com.swift.browser.analyticscore.AnalyticsCore

class BrowserDiagnosticsEngine(private val stateEngine: BrowserStateEngine) {
    companion object {
        private const val TAG = "BrowserDiagnosticsEngine"
    }

    fun logDiagnostic(
        tag: String,
        message: String,
        operationId: String? = null,
        tabId: String? = null
    ) {
        val formatted = "[$tag] $message"
        Log.d(TAG, formatted)
        stateEngine.updateDiagnostics(formatted)
        AnalyticsCore.logDiagnostic(
            engineName = "browser_engine",
            module = tag,
            function = "logDiagnostic",
            reason = message,
            operationId = operationId,
            tabId = tabId
        )
    }
}
