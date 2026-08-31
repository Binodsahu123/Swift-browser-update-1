package com.swift.browser.browserengine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow

object SwiftDeveloperEngine {
    private const val TAG = "SwiftDeveloperEngine"

    data class DesktopConnectionState(
        val userAgentApplied: Boolean = false,
        val viewportApplied: Boolean = false,
        val cssRulesApplied: Boolean = false,
        val hostRewriteApplied: Boolean = false,
        val hostRewriteSkipped: Boolean = false,
        val desktopPageLoaded: Boolean = false,
        val connectionStatus: String = "DISCONNECTED",
        val latencyMs: Long = 0L
    )

    data class DesktopModeMonitorState(
        val desktopModeEnabled: Boolean = false,
        val userAgentType: String = "Mobile",
        val viewportWidth: Int = 360,
        val urlRewriteSuccess: Boolean = false,
        val currentUrl: String = ""
    )

    data class PipelineStep(val name: String = "", val status: String = "SUCCESS", val timestamp: Long = System.currentTimeMillis())
    data class ErrorEvent(val component: String = "", val message: String = "", val timestamp: Long = System.currentTimeMillis())
    data class PermissionEvent(val origin: String = "", val permission: String = "", val action: String = "")
    data class ExtensionEvent(val extensionId: String = "", val event: String = "")
    data class DownloadEvent(val url: String = "", val filename: String = "", val status: String = "")
    data class VoiceEvent(val text: String = "", val confidence: Float = 1.0f)
    data class PermissionLog(val origin: String = "", val message: String = "")
    data class ExtensionStatus(val id: String = "", val name: String = "", val enabled: Boolean = true)
    data class NetworkMetric(val url: String = "", val responseTimeMs: Long = 0L)
    data class DownloadMonitorState(val activeDownloads: Int = 0)
    data class PermissionConnectionState(val activePermissions: Int = 0)
    data class VoiceEngineState(val isListening: Boolean = false)
    data class WebViewMonitorState(val pageTitle: String = "", val progress: Int = 100)
    data class SessionMonitorState(val activeTabsCount: Int = 0)
    data class HardwareResourceMonitorState(val memoryUsageMb: Long = 0L, val cpuUsagePct: Float = 0.0f)
    data class TraceStep(val stepName: String = "", val durationMs: Long = 0L)
    data class FailureTrace(val module: String = "", val reason: String = "")

    val desktopConnectionState = MutableStateFlow(DesktopConnectionState())
    val desktopModeMonitorState = MutableStateFlow(DesktopModeMonitorState())

    fun initFromPrefs(context: Context) {
        Log.d(TAG, "Initializing SwiftDeveloperEngine preferences.")
    }

    fun setDeveloperModeEnabled(context: Context, enabled: Boolean) {
        Log.d(TAG, "Developer mode set to $enabled")
    }

    fun logError(component: String, message: String, level: String = "INFO", location: String = "") {
        Log.d(TAG, "[$level] [$component] $message ($location)")
    }
}
