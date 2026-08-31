package com.swift.browser.analyticscore

/**
 * Swift Browser Analytics Core
 * Central runtime owner and coordinator for all analytics domains:
 * 1. Browser Analytics
 * 2. Performance Analytics
 * 3. Crash Analytics Hooks
 * 4. Engine Diagnostics
 * 5. Feature Usage Analytics
 * 6. Navigation Analytics
 * 7. Startup Analytics
 * 8. Session Analytics
 * 9. Privacy-Safe Telemetry
 * 10. Internal Performance Metrics
 */
object AnalyticsCore {

    val browserAnalytics = BrowserAnalyticsManager()
    val performanceAnalytics = PerformanceAnalyticsManager()
    val crashAnalytics = CrashAnalyticsManager()
    val engineDiagnostics = EngineDiagnosticsManager()
    val featureUsage = FeatureUsageAnalyticsManager()
    val navigationAnalytics = NavigationAnalyticsManager()
    val startupAnalytics = StartupAnalyticsManager()
    val sessionAnalytics = SessionAnalyticsManager()
    val privacyTelemetry = PrivacyTelemetryManager()
    val internalMetrics = InternalMetricsManager()

    init {
        // Automatically attach uncaught crash handler hook at startup
        crashAnalytics.attachUncaughtExceptionHandler()
    }

    // --- Delegate Methods for Convenient Access ---

    fun logEvent(eventName: String, params: Map<String, Any> = emptyMap()) {
        browserAnalytics.logEvent(eventName, params)
        privacyTelemetry.recordPrivacyTelemetry(
            sessionId = sessionAnalytics.currentSession.value.sessionId,
            eventType = eventName,
            params = params.mapValues { it.value.toString() }
        )
    }

    fun logError(throwable: Throwable, message: String) {
        crashAnalytics.recordError(message, throwable)
        engineDiagnostics.logError("AnalyticsCore", "Core", "logError", message)
    }

    fun logError(
        engineName: String,
        module: String,
        function: String,
        error: String,
        operationId: String? = null,
        tabId: String? = null
    ) {
        engineDiagnostics.logError(engineName, module, function, error, operationId, tabId)
        crashAnalytics.recordError("[$engineName::$module::$function] $error")
    }

    fun logDiagnostic(
        engineName: String,
        module: String,
        function: String,
        reason: String,
        severity: DiagnosticSeverity = DiagnosticSeverity.INFO,
        operationId: String? = null,
        tabId: String? = null
    ) {
        engineDiagnostics.logDiagnostic(engineName, module, function, reason, severity, operationId, tabId)
    }

    fun recordPerformance(
        key: String,
        durationMs: Long,
        operationId: String? = null,
        tabId: String? = null
    ) {
        performanceAnalytics.recordMetric(key = key, durationMs = durationMs, operationId = operationId, tabId = tabId)
    }

    fun startTimer(key: String) {
        performanceAnalytics.startTimer(key)
    }

    fun stopTimer(key: String): Long {
        return performanceAnalytics.stopTimer(key)
    }

    fun trackPageLoad(
        url: String,
        isIncognito: Boolean = false,
        context: AnalyticsContext = if (isIncognito) AnalyticsContext.PRIVATE else AnalyticsContext.NORMAL
    ) {
        val isPrivate = context.isPrivate || isIncognito
        browserAnalytics.trackPageLoad(url, isPrivate, context)
        sessionAnalytics.incrementPageViews()
    }

    fun trackTabCreated(
        tabId: String,
        isIncognito: Boolean = false,
        context: AnalyticsContext = if (isIncognito) AnalyticsContext.PRIVATE else AnalyticsContext.NORMAL
    ) {
        val isPrivate = context.isPrivate || isIncognito
        browserAnalytics.trackTabCreated(tabId, isPrivate, context)
    }

    fun trackTabClosed(tabId: String, context: AnalyticsContext = AnalyticsContext.NORMAL) {
        browserAnalytics.trackTabClosed(tabId, context)
    }

    fun trackSearchQuery(
        query: String,
        searchEngine: String,
        context: AnalyticsContext = AnalyticsContext.NORMAL
    ) {
        browserAnalytics.trackSearchQuery(query, searchEngine, context)
    }

    fun trackFeature(featureId: String, action: String = "use", metadata: Map<String, String> = emptyMap()) {
        featureUsage.trackFeature(featureId, action, metadata)
    }

    fun trackNavigation(
        url: String,
        loadDurationMs: Long,
        httpCode: Int = 200,
        isSuccess: Boolean = true,
        context: AnalyticsContext = AnalyticsContext.NORMAL
    ) {
        navigationAnalytics.trackNavigationCompleted(url, loadDurationMs, httpCode, isSuccess, context)
    }

    fun recordStartupPhase(phaseName: String, startupType: StartupType = StartupType.COLD, durationMs: Long = 0L) {
        startupAnalytics.recordStartupPhase(phaseName, startupType, durationMs)
    }

    fun startSession() {
        sessionAnalytics.startNewSession()
    }

    fun endSession() {
        sessionAnalytics.endSession()
    }

    fun clearAll() {
        browserAnalytics.clearHistory()
        performanceAnalytics.clear()
        crashAnalytics.clear()
        engineDiagnostics.clear()
        featureUsage.clear()
        navigationAnalytics.clear()
        startupAnalytics.clear()
        privacyTelemetry.setTelemetryEnabled(true)
    }
}
