package com.swift.browser.networkstatsengine

import android.util.Log

object WebRtcConnectionDiagnostics {
    private const val TAG = "WebRtcConnectionDiagnostics"

    // Thread-safe cache of current session states for in-memory querying
    private val activeDiagnostics = java.util.concurrent.ConcurrentHashMap<String, WebRtcTraceModel>()

    /**
     * Records a diagnostic update from a WebRTC session and logs it into the trace repository.
     */
    fun recordDiagnostic(
        tabId: String,
        iceState: String,
        connectionState: String,
        rtt: Long?,
        candidatePairState: String?,
        packetLoss: Long?,
        bytesSent: Long?,
        bytesReceived: Long?,
        message: String = "WebRTC Diagnostics update"
    ): WebRtcTraceModel {
        val trace = WebRtcTraceModel(
            message = message,
            tabId = tabId,
            iceState = iceState,
            connectionState = connectionState,
            rtt = rtt,
            candidatePairState = candidatePairState,
            packetLoss = packetLoss,
            bytesSent = bytesSent,
            bytesReceived = bytesReceived
        )

        activeDiagnostics[tabId] = trace
        TraceRepository.addTrace(trace)
        Log.d(TAG, "Recorded WebRTC trace for tab $tabId: $trace")
        return trace
    }

    /**
     * Gets the latest cached diagnostic trace for a specific tab.
     */
    fun getLatestDiagnostic(tabId: String): WebRtcTraceModel? {
        return activeDiagnostics[tabId]
    }

    /**
     * Clear diagnostics for a closed tab.
     */
    fun clearTabDiagnostics(tabId: String) {
        activeDiagnostics.remove(tabId)
    }

    /**
     * Retrieves all recorded WebRTC diagnostics traces.
     */
    fun getAllWebRtcTraces(): List<WebRtcTraceModel> {
        return TraceRepository.traces.value.filterIsInstance<WebRtcTraceModel>()
    }
}
