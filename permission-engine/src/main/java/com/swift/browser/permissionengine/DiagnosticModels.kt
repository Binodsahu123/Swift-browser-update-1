package com.swift.browser.permissionengine

data class EngineStateModel(
    val engineId: String = "",
    val engineName: String = "",
    val fileName: String = "",
    val className: String = "",
    val primaryMethod: String = "",
    val lastCallback: String = "",
    val state: String = "PASS", // "PASS", "FAIL", "WARN", "SKIP", "STALLED", "NOT_CONNECTED", "DEPRECATED", "DORMANT", "PENDING", "RETRYING", "RECOVERED", "UPDATED_BROKEN", "UPDATED_FIXED", "REGRESSED"
    val health: Int = 100, // 0 to 100
    val dependencies: String = "",
    val startupLatency: Long = 0L,
    val lastEventTime: Long = System.currentTimeMillis(),
    val lastError: String = "",
    val lastSuccess: String = "",
    val warningCount: Int = 0,
    val errorCount: Int = 0,
    val staleCount: Int = 0,
    val updatedRegressionCount: Int = 0,
    val isVisible: Boolean = true,
    val isConnected: Boolean = true,
    val isEnabled: Boolean = true,
    val isDeprecated: Boolean = false,
    val isDormant: Boolean = false,
    val isBlocking: Boolean = false,
    val isCritical: Boolean = false,
    val isActive: Boolean = true,
    val isDuplicate: Boolean = false,
    val regressionCount: Int = 0,
    val startupCostMs: Long = 0L,
    val dependencyIds: List<String> = emptyList(),
    val moduleCategory: String = "",
    val primaryMethods: List<String> = emptyList(),
    val lastTraceTime: Long = System.currentTimeMillis()
)

data class PerformanceTraceModel(
    val eventId: String,
    val eventType: String, // "STARTUP", "RENDER", "MENU", "DB"
    val stage: String,
    val status: String, // "PASS", "WARN", "FAIL", "STALLED"
    val durationMs: Long,
    val thresholdMs: Long,
    val reason: String,
    val suggestedFix: String,
    val fileName: String,
    val className: String,
    val methodName: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class MenuTraceModel(
    val menuId: String,
    val stage: String, // "TAP", "OPEN_REQUEST", "INFLATION", "BINDING", "RENDER", "READY"
    val status: String,
    val openDelayMs: Long,
    val inflateDelayMs: Long,
    val renderDelayMs: Long,
    val reason: String,
    val suggestedFix: String,
    val fileName: String,
    val className: String,
    val methodName: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class StartupTraceModel(
    val startupId: String,
    val stage: String, // "PROCESS_START", "ACTIVITY_START", "SPLASH_SHOW", "WEBVIEW_CREATE", "WEBVIEW_WARMUP", "PERMISSION_INIT", "HOME_LOAD", "READY"
    val status: String,
    val elapsedMs: Long,
    val reason: String,
    val suggestedFix: String,
    val fileName: String,
    val className: String,
    val methodName: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class UpdateTraceModel(
    val updateId: String,
    val versionFrom: String,
    val versionTo: String,
    val stage: String,
    val status: String,
    val reason: String,
    val suggestedFix: String,
    val newErrorDetected: Boolean,
    val replacedError: String,
    val updatedRegression: Boolean,
    val fixedRegression: Boolean,
    val fileName: String,
    val className: String,
    val methodName: String,
    val timestamp: Long = System.currentTimeMillis()
)
