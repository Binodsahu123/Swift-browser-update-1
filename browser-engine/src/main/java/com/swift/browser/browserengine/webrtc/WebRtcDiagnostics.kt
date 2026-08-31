package com.swift.browser.browserengine.webrtc

import android.util.Log
import com.swift.browser.browserengine.DiagnosticCenter

/**
 * Diagnostic utility for formatting, translating, and logging WebRTC runtime metrics, state transitions,
 * connection events, and errors for the UI and developers.
 */
object WebRtcDiagnostics {
    private const val TAG = "WebRtcDiagnostics"
    private const val ENGINE_ID = "webrtc_runtime_engine"

    /**
     * Maps raw peer connection and track states to user-friendly messages.
     */
    fun getDiagnosticSummary(session: WebRtcRuntimeSession): String {
        val activeString = if (session.isActive) "Active" else "Inactive"
        return "WebRTC TabSession[${session.tabId}]: state=${session.connectionState} ($activeString), " +
                "origin=${session.origin}, camera=${session.cameraState}, " +
                "microphone=${session.microphoneState}, " +
                "audioTrack=${session.audioTrackState}, videoTrack=${session.videoTrackState}" +
                (if (session.lastError != null) ", lastError=${session.lastError}" else "")
    }

    /**
     * Recommends recovery actions for failed or disconnected sessions.
     */
    fun getRecommendation(session: WebRtcRuntimeSession): String {
        return when (session.connectionState) {
            WebRtcSessionState.FAILED -> "Connection failed. Please check network restrictions, firewalls (STUN/TURN accessibility), or reload the page."
            WebRtcSessionState.DISCONNECTED -> "Temporary disconnection. Moving to automatic reconnect flow..."
            WebRtcSessionState.RECONNECTING -> "Reconnecting to WebRTC endpoint... Please wait."
            WebRtcSessionState.REQUESTING_MEDIA -> "Awaiting hardware permissions. Please grant camera/microphone access in the browser permission popup."
            else -> if (session.lastError != null) "Error encountered: ${session.lastError}. Check site specifications." else "System functioning normally."
        }
    }

    /**
     * Publishes session diagnostic information to the permission engine's central telemetry/developer dashboard.
     */
    fun publishDiagnostics(session: WebRtcRuntimeSession) {
        val summary = getDiagnosticSummary(session)
        val recommendation = getRecommendation(session)
        val activeState = when (session.connectionState) {
            WebRtcSessionState.CONNECTED -> "PASS"
            WebRtcSessionState.CONNECTING, WebRtcSessionState.RECONNECTING, WebRtcSessionState.REQUESTING_MEDIA, WebRtcSessionState.CAPTURING -> "WARN"
            WebRtcSessionState.FAILED, WebRtcSessionState.DISCONNECTED -> "FAIL"
            WebRtcSessionState.IDLE, WebRtcSessionState.STOPPED, WebRtcSessionState.STOPPING -> "DORMANT"
        }

        val health = when (session.connectionState) {
            WebRtcSessionState.CONNECTED -> 100
            WebRtcSessionState.CONNECTING, WebRtcSessionState.REQUESTING_MEDIA -> 80
            WebRtcSessionState.RECONNECTING -> 60
            WebRtcSessionState.DISCONNECTED -> 40
            WebRtcSessionState.FAILED -> 20
            else -> 100
        }

        Log.i(TAG, "WebRTC Diagnostics Published: $summary | Recommendation: $recommendation")

        // Log to central diagnostic event tracking
        DiagnosticCenter.logEvent(
            engineName = "webrtc_runtime_engine",
            module = "WebRtcRuntime",
            function = "publishDiagnostics",
            reason = "Session ${session.sessionId} State -> ${session.connectionState}"
        )

        if (session.lastError != null) {
            DiagnosticCenter.logError(
                engineName = "webrtc_runtime_engine",
                module = "WebRtcRuntime",
                function = "onSessionError",
                error = session.lastError
            )
        }

        // Update Permission Engine dashboard state
        try {
            com.swift.browser.permissionengine.PermissionDiagnostics.updateEngineState(
                engineId = ENGINE_ID,
                state = activeState,
                health = health,
                lastCallback = "onSessionStateChanged (${session.connectionState})",
                lastError = session.lastError ?: "",
                lastSuccess = if (session.connectionState == WebRtcSessionState.CONNECTED) "WebRTC connection established with ${session.origin}" else ""
            )
        } catch (e: Exception) {
            Log.w(TAG, "Unable to publish WebRTC diagnostics to PermissionDiagnostics: ${e.message}")
        }
    }
}
