package com.swift.browser.analyticscore

import java.util.UUID

/**
 * Encapsulates analytics context and privacy boundaries for logging.
 */
data class AnalyticsContext(
    val isPrivate: Boolean = false,
    val sessionId: String? = null
) {
    companion object {
        val NORMAL = AnalyticsContext(isPrivate = false)
        val PRIVATE = AnalyticsContext(isPrivate = true)
        fun private(sessionId: String? = null) = AnalyticsContext(isPrivate = true, sessionId = sessionId)
    }
}

/**
 * Data models for Swift Browser Analytics Core
 */

enum class DiagnosticSeverity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}

enum class StartupType {
    COLD,
    WARM
}

data class BrowserAnalyticsEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val eventName: String,
    val category: String = "BROWSER",
    val params: Map<String, Any> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

data class PerformanceMetricRecord(
    val key: String,
    val durationMs: Long,
    val jvmHeapUsedMb: Long = 0,
    val ramUsedMb: Long = jvmHeapUsedMb,
    val cpuUsagePercent: Float? = null,
    val fps: Int? = null,
    val operationId: String? = null,
    val tabId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class CrashReport(
    val crashId: String = UUID.randomUUID().toString(),
    val exceptionClass: String,
    val message: String,
    val stackTrace: String,
    val isFatal: Boolean = true,
    val threadName: String = Thread.currentThread().name,
    val timestamp: Long = System.currentTimeMillis()
)

data class EngineDiagnosticTrace(
    val traceId: String = UUID.randomUUID().toString(),
    val engineName: String,
    val module: String,
    val function: String,
    val message: String,
    val severity: DiagnosticSeverity = DiagnosticSeverity.INFO,
    val operationId: String? = null,
    val tabId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class DiagnosticsSummary(
    val totalEventsCount: Long = 0L,
    val errorCount: Long = 0L,
    val warningCount: Long = 0L,
    val lastEvent: EngineDiagnosticTrace? = null,
    val recentErrors: List<EngineDiagnosticTrace> = emptyList(),
    val engineHealthMap: Map<String, Int> = emptyMap()
)

data class PerformanceSummary(
    val jvmHeapUsedMb: Long = 0L,
    val processMemoryMb: Long = 0L,
    val cpuUsagePercent: Float? = null,
    val measuredFps: Int? = null,
    val jankCount: Int = 0,
    val recentDurationAverageMs: Long = 0L,
    val lastRecordedKey: String = "",
    val lastDurationMs: Long = 0L
)

data class FeatureUsageRecord(
    val featureId: String,
    val action: String,
    val metadata: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

data class NavigationTrace(
    val traceId: String = UUID.randomUUID().toString(),
    val rawUrl: String,
    val sanitizedUrl: String,
    val domain: String,
    val loadDurationMs: Long,
    val httpCode: Int = 200,
    val isSuccess: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

data class StartupTrace(
    val startupType: StartupType,
    val phaseName: String,
    val durationMs: Long,
    val totalBootDurationMs: Long = durationMs,
    val timestamp: Long = System.currentTimeMillis()
)

data class SessionState(
    val sessionId: String = UUID.randomUUID().toString(),
    val startTimeMs: Long = System.currentTimeMillis(),
    var endTimeMs: Long? = null,
    var pageViewsCount: Int = 0,
    var activeTabCount: Int = 1,
    var isBackgrounded: Boolean = false,
    val deviceMetadata: String = "Android/${android.os.Build.VERSION.SDK_INT}"
)

data class PrivacyTelemetryPayload(
    val anonymousSessionHash: String,
    val eventType: String,
    val scrubbedParams: Map<String, String>,
    val timestamp: Long = System.currentTimeMillis()
)

data class InternalPerformanceSnapshot(
    val heapAllocatedMb: Long,
    val maxMemoryMb: Long,
    val freeMemoryMb: Long,
    val activeThreadCount: Int,
    val uptimeMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)
