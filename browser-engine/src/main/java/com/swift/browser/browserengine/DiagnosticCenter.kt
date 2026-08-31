package com.swift.browser.browserengine

import com.swift.browser.analyticscore.AnalyticsCore
import com.swift.browser.analyticscore.DiagnosticSeverity

object DiagnosticCenter {
    fun logEvent(engineName: String, module: String, function: String, reason: String) {
        AnalyticsCore.logDiagnostic(
            engineName = engineName,
            module = module,
            function = function,
            reason = reason,
            severity = DiagnosticSeverity.INFO
        )
    }

    fun logError(engineName: String, module: String, function: String, error: String) {
        AnalyticsCore.logError(
            engineName = engineName,
            module = module,
            function = function,
            error = error
        )
    }
}
