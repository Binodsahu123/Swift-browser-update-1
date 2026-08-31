package com.swift.browser.browserengine.webrtc

/**
 * Represents the type-safe state machine for WebRTC session states.
 */
enum class WebRtcSessionState {
    IDLE,
    REQUESTING_MEDIA,
    CAPTURING,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    RECONNECTING,
    FAILED,
    STOPPING,
    STOPPED
}

/**
 * Tracks and manages the runtime state, capabilities, and lifecycle of a WebRTC session scoped to a specific browser tab and origin.
 */
data class PeerConnectionStats(
    val rtt: Long? = null,
    val packetLoss: Long? = null,
    val bytesSent: Long? = null,
    val bytesReceived: Long? = null
)

data class PeerConnectionRuntimeState(
    val pcId: String,
    val connectionState: String = "new", // new, connecting, connected, disconnected, failed, closed
    val signalingState: String = "stable", // stable, have-local-offer, have-remote-offer, have-local-pranswer, have-remote-pranswer, closed
    val iceConnectionState: String = "new", // new, checking, connected, completed, failed, disconnected, closed
    val iceGatheringState: String = "new", // new, gathering, complete
    val lastStats: PeerConnectionStats? = null
)

data class MediaTrackRuntimeState(
    val trackId: String,
    val kind: String, // audio, video
    val label: String = "",
    val readyState: String = "live", // live, ended, muted
    val enabled: Boolean = true,
    val muted: Boolean = false
)

data class WebRtcRuntimeSession(
    val sessionId: String,
    val tabId: String,
    val origin: String,
    val topLevelOrigin: String,
    val cameraState: String = "IDLE", // IDLE, REQUESTING, CAPTURING, DISABLED
    val microphoneState: String = "IDLE", // IDLE, REQUESTING, CAPTURING, DISABLED
    val audioTrackState: String = "ended", // active, muted, unmuted, ended
    val videoTrackState: String = "ended", // active, muted, unmuted, ended
    val connectionState: WebRtcSessionState = WebRtcSessionState.IDLE,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    
    // Nested tracking maps for multiple connections and tracks
    val peerConnections: Map<String, PeerConnectionRuntimeState> = emptyMap(),
    val tracks: Map<String, MediaTrackRuntimeState> = emptyMap()
) {
    /**
     * Helper to check if the session is currently active.
     */
    val isActive: Boolean
        get() = connectionState == WebRtcSessionState.CONNECTING ||
                connectionState == WebRtcSessionState.CONNECTED ||
                connectionState == WebRtcSessionState.RECONNECTING ||
                cameraState == "CAPTURING" ||
                microphoneState == "CAPTURING" ||
                peerConnections.values.any { it.connectionState == "connected" }
}
