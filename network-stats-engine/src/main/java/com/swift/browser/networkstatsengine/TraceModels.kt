package com.swift.browser.networkstatsengine


import java.util.UUID

sealed interface TraceModel {
    val id: String
    val timestamp: Long
    val message: String
}

data class PermissionTraceModel(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val message: String,
    val requester: String,
    val permission: String,
    val decision: String,
    val layer: String
) : TraceModel

data class StartupTraceModel(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val message: String,
    val durationMs: Long,
    val isOptimized: Boolean
) : TraceModel

data class MenuTraceModel(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val message: String,
    val latencyMs: Long
) : TraceModel

data class PerformanceTraceModel(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val message: String,
    val ramUsedMb: Int,
    val cpuUsagePercent: Int,
    val fps: Int
) : TraceModel

data class EngineTraceModel(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val message: String,
    val engineId: String,
    val eventType: String,
    val durationMs: Long
) : TraceModel

data class DownloadTraceModel(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val message: String,
    val filename: String,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val speed: String
) : TraceModel

data class MediaTraceModel(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val message: String,
    val url: String,
    val mimeType: String,
    val quality: String
) : TraceModel

data class ExtensionTraceModel(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val message: String,
    val extensionId: String,
    val actionType: String,
    val isAllowed: Boolean
) : TraceModel

data class AITraceModel(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val message: String,
    val provider: String,
    val modelName: String,
    val tokensProcessed: Int
) : TraceModel

data class VoiceTraceModel(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val message: String,
    val hasWakeWord: Boolean,
    val confidence: Float
) : TraceModel

data class NetworkTraceModel(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val message: String,
    val url: String,
    val dnsMs: Long,
    val tlsMs: Long,
    val totalMs: Long
) : TraceModel

data class SecurityTraceModel(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val message: String,
    val domain: String,
    val trackersBlocked: Int,
    val adsBlocked: Int
) : TraceModel

data class RecoveryTraceModel(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val message: String,
    val engineId: String,
    val success: Boolean
) : TraceModel

data class ResetTraceModel(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val message: String,
    val engineId: String
) : TraceModel

data class UpdateTraceModel(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val message: String,
    val version: String,
    val updatedEngines: List<String>
) : TraceModel

data class WebRtcTraceModel(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val message: String,
    val tabId: String,
    val iceState: String,
    val connectionState: String,
    val rtt: Long?,
    val candidatePairState: String?,
    val packetLoss: Long?,
    val bytesSent: Long?,
    val bytesReceived: Long?
) : TraceModel

