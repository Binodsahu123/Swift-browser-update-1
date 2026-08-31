package com.swift.browser.browserengine.webrtc

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import android.webkit.WebView
import com.swift.browser.browserengine.DiagnosticCenter
import com.swift.browser.browserengine.WebMediaCompatibilityEngine
import com.swift.browser.permissionengine.OriginNormalizer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton coordinator for WebRTC sessions inside the browser engine.
 * Observes runtime peer connections, media capture events, and track lifecycles via the JavaScript bridge.
 */
object WebRtcRuntimeManager {
    private const val TAG = "WebRtcRuntimeManager"
    const val INTERFACE_NAME = "AndroidWebRtcBridge"

    // Thread-safe maps for active session tracking
    private val activeSessions = ConcurrentHashMap<String, WebRtcRuntimeSession>()
    private val tabSessions = ConcurrentHashMap<String, MutableSet<String>>()

    // Listeners for telemetry/UI integration
    private val listeners = ConcurrentHashMap<String, (WebRtcRuntimeSession) -> Unit>()

    // For network connectivity observation
    private var isNetworkRegistered = false

    /**
     * Registers a listener to observe WebRTC session changes.
     */
    fun addListener(id: String, listener: (WebRtcRuntimeSession) -> Unit) {
        listeners[id] = listener
    }

    /**
     * Removes a registered session observer.
     */
    fun removeListener(id: String) {
        listeners.remove(id)
    }

    /**
     * Initialized network-change triggers from network-core context to automatically fail/reconnect WebRTC sessions.
     */
    fun initialize(context: Context) {
        synchronized(this) {
            WebMediaDeviceManager.initialize(context)
            if (isNetworkRegistered) return
            try {
                WebRtcRecoveryCoordinator.start(context)
                isNetworkRegistered = true
                Log.i(TAG, "WebRTC automated network transition observer successfully registered via WebRtcRecoveryCoordinator.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register network transition callback: ${e.message}", e)
            }
        }
    }

    /**
     * Retrieves or creates a WebRTC session scoped to a specific tab and origin.
     */
    fun getOrCreateSession(tabId: String, origin: String, topLevelOrigin: String): WebRtcRuntimeSession {
        val normalizedOrigin = OriginNormalizer.normalize(origin)
        val normalizedTopLevel = OriginNormalizer.normalize(topLevelOrigin)

        // Check if there is an existing session for this tab and origin
        val existingSessionId = tabSessions[tabId]?.firstOrNull { sessionId ->
            val session = activeSessions[sessionId]
            session != null && session.origin == normalizedOrigin
        }

        if (existingSessionId != null) {
            val existing = activeSessions[existingSessionId]
            if (existing != null) return existing
        }

        // Create new session
        val sessionId = "rtc_sess_" + UUID.randomUUID().toString().substring(0, 8)
        val newSession = WebRtcRuntimeSession(
            sessionId = sessionId,
            tabId = tabId,
            origin = normalizedOrigin,
            topLevelOrigin = normalizedTopLevel
        )

        activeSessions[sessionId] = newSession
        tabSessions.getOrPut(tabId) { ConcurrentHashMap.newKeySet() }.add(sessionId)

        Log.i(TAG, "Created WebRTC Session: sessionId=$sessionId, tabId=$tabId, origin=$normalizedOrigin")
        notifySessionChanged(newSession)
        return newSession
    }

    /**
     * Updates an active session's state and publishes the new diagnostics.
     */
    private fun updateSession(session: WebRtcRuntimeSession) {
        activeSessions[session.sessionId] = session
        notifySessionChanged(session)
    }

    private fun notifySessionChanged(session: WebRtcRuntimeSession) {
        WebRtcDiagnostics.publishDiagnostics(session)
        listeners.values.forEach { it.invoke(session) }
    }

    /**
     * Retrieves all active sessions.
     */
    fun getActiveSessions(): List<WebRtcRuntimeSession> {
        return activeSessions.values.toList()
    }

    /**
     * Retrieves active sessions for a specific tab.
     */
    fun getSessionsForTab(tabId: String): List<WebRtcRuntimeSession> {
        val sessionIds = tabSessions[tabId] ?: return emptyList()
        return sessionIds.mapNotNull { activeSessions[it] }
    }

    // ====================================================================
    // WEBVIEW / LIFECYCLE EVENT HANDLERS
    // ====================================================================

    /**
     * Handles browser tab closure. Cleans up and transitions all scoped sessions to STOPPED.
     */
    fun onTabClosed(tabId: String) {
        Log.i(TAG, "onTabClosed: tabId=$tabId")
        WebMediaDeviceManager.unregisterWebView(tabId)
        WebRtcRecoveryCoordinator.cancelRecoveryForTab(tabId)
        val sessionIds = tabSessions.remove(tabId) ?: return
        for (sessionId in sessionIds) {
            val session = activeSessions.remove(sessionId) ?: continue
            val closedPcs = session.peerConnections.mapValues { it.value.copy(connectionState = "closed", iceConnectionState = "closed") }
            val endedTracks = session.tracks.mapValues { it.value.copy(readyState = "ended", muted = true) }
            val stoppedSession = session.copy(
                connectionState = WebRtcSessionState.STOPPED,
                cameraState = "DISABLED",
                microphoneState = "DISABLED",
                audioTrackState = "ended",
                videoTrackState = "ended",
                peerConnections = closedPcs,
                tracks = endedTracks
            )
            Log.d(TAG, "Terminated WebRTC Session $sessionId on tab closure.")
            notifySessionChanged(stoppedSession)
        }
    }

    /**
     * Handles page navigation. Cleans up sessions if origin changes (origin-isolation).
     */
    fun onNavigation(tabId: String, newUrl: String) {
        val newOrigin = OriginNormalizer.normalize(newUrl)
        Log.i(TAG, "onNavigation: tabId=$tabId, newUrl=$newUrl, newOrigin=$newOrigin")

        val sessionIds = tabSessions[tabId] ?: return
        val iterator = sessionIds.iterator()
        while (iterator.hasNext()) {
            val sessionId = iterator.next()
            val session = activeSessions[sessionId] ?: continue
            if (session.origin != newOrigin) {
                // Origin mismatch -> terminate session
                iterator.remove()
                activeSessions.remove(sessionId)
                val closedPcs = session.peerConnections.mapValues { it.value.copy(connectionState = "closed", iceConnectionState = "closed") }
                val endedTracks = session.tracks.mapValues { it.value.copy(readyState = "ended", muted = true) }
                val stoppedSession = session.copy(
                    connectionState = WebRtcSessionState.STOPPED,
                    cameraState = "DISABLED",
                    microphoneState = "DISABLED",
                    audioTrackState = "ended",
                    videoTrackState = "ended",
                    peerConnections = closedPcs,
                    tracks = endedTracks
                )
                Log.d(TAG, "Destroyed WebRTC Session $sessionId due to origin isolation navigation.")
                notifySessionChanged(stoppedSession)
            }
        }
    }

    /**
     * Handles WebView destruction.
     */
    fun onWebViewDestroyed(tabId: String) {
        onTabClosed(tabId)
    }

    /**
     * Handles WebView pause. Transitions active states to STOPPING or STOPPED.
     */
    fun onWebViewPaused(tabId: String) {
        Log.i(TAG, "onWebViewPaused: tabId=$tabId")
        val sessions = getSessionsForTab(tabId)
        for (session in sessions) {
            if (session.isActive) {
                val pausedSession = session.copy(
                    connectionState = WebRtcSessionState.STOPPED,
                    cameraState = "IDLE",
                    microphoneState = "IDLE",
                    audioTrackState = "muted",
                    videoTrackState = "muted"
                )
                updateSession(pausedSession)
                DiagnosticCenter.logEvent(
                    engineName = "webrtc_runtime_engine",
                    module = "WebRtcRuntime",
                    function = "onWebViewPaused",
                    reason = "Session ${session.sessionId} paused due to WebView suspension."
                )
            }
        }
    }

    /**
     * Handles WebView resume.
     */
    fun onWebViewResumed(tabId: String) {
        Log.i(TAG, "onWebViewResumed: tabId=$tabId")
        val sessions = getSessionsForTab(tabId)
        for (session in sessions) {
            DiagnosticCenter.logEvent(
                engineName = "webrtc_runtime_engine",
                module = "WebRtcRuntime",
                function = "onWebViewResumed",
                reason = "Session ${session.sessionId} resumed."
            )
        }
    }

    /**
     * Handles global network transition callbacks (connected/disconnected) from network-core.
     */
    fun handleNetworkTransition(isConnected: Boolean) {
        Log.i(TAG, "handleNetworkTransition: isConnected=$isConnected")
        for (session in activeSessions.values) {
            if (isConnected) {
                if (session.connectionState == WebRtcSessionState.DISCONNECTED ||
                    session.connectionState == WebRtcSessionState.RECONNECTING ||
                    session.connectionState == WebRtcSessionState.FAILED
                ) {
                    val reconnectedSession = session.copy(
                        connectionState = WebRtcSessionState.RECONNECTING,
                        lastError = null
                    )
                    updateSession(reconnectedSession)
                    DiagnosticCenter.logEvent(
                        engineName = "webrtc_runtime_engine",
                        module = "WebRtcRuntime",
                        function = "handleNetworkTransition",
                        reason = "Reconnecting session ${session.sessionId} on network recovery."
                    )
                }
            } else {
                if (session.connectionState == WebRtcSessionState.CONNECTED ||
                    session.connectionState == WebRtcSessionState.CONNECTING
                ) {
                    val disconnectedSession = session.copy(
                        connectionState = WebRtcSessionState.DISCONNECTED,
                        lastError = "Network connection lost."
                    )
                    updateSession(disconnectedSession)
                    DiagnosticCenter.logEvent(
                        engineName = "webrtc_runtime_engine",
                        module = "WebRtcRuntime",
                        function = "handleNetworkTransition",
                        reason = "Session ${session.sessionId} disconnected due to offline status."
                    )
                }
            }
        }
    }

    // ====================================================================
    // JAVASCRIPT BRIDGE HOOKS
    // ====================================================================

    fun handleGetUserMediaRequested(tabId: String, origin: String, hasVideo: Boolean, hasAudio: Boolean) {
        val session = getOrCreateSession(tabId, origin, origin)
        val updated = session.copy(
            connectionState = WebRtcSessionState.REQUESTING_MEDIA,
            cameraState = if (hasVideo) "REQUESTING" else session.cameraState,
            microphoneState = if (hasAudio) "REQUESTING" else session.microphoneState
        )
        updateSession(updated)
    }

    fun handleGetUserMediaSuccess(tabId: String, origin: String, hasAudio: Boolean, hasVideo: Boolean) {
        val session = getOrCreateSession(tabId, origin, origin)
        val updated = session.copy(
            connectionState = WebRtcSessionState.CAPTURING,
            cameraState = if (hasVideo) "CAPTURING" else "DISABLED",
            microphoneState = if (hasAudio) "CAPTURING" else "DISABLED",
            audioTrackState = if (hasAudio) "active" else "ended",
            videoTrackState = if (hasVideo) "active" else "ended"
        )
        updateSession(updated)
    }

    fun handleGetUserMediaFailure(tabId: String, origin: String, error: String) {
        val session = getOrCreateSession(tabId, origin, origin)
        val updated = session.copy(
            connectionState = WebRtcSessionState.FAILED,
            cameraState = "DISABLED",
            microphoneState = "DISABLED",
            audioTrackState = "ended",
            videoTrackState = "ended",
            lastError = "getUserMedia failed: $error"
        )
        updateSession(updated)
    }

    fun handleScreenCaptureEnded(tabId: String, origin: String) {
        Log.i(TAG, "Screen capture ended for tab $tabId, origin $origin")
        val session = getOrCreateSession(tabId, origin, origin)
        val updated = session.copy(
            videoTrackState = "ended",
            cameraState = if (session.cameraState == "CAPTURING") "DISABLED" else session.cameraState
        )
        updateSession(updated)
    }

    fun handleTrackAdded(tabId: String, origin: String, trackId: String, kind: String, label: String, readyState: String, enabled: Boolean) {
        val session = getOrCreateSession(tabId, origin, origin)
        val isAudio = kind.equals("audio", ignoreCase = true)
        val stateStr = if (enabled) readyState else "muted"

        val track = MediaTrackRuntimeState(
            trackId = trackId,
            kind = kind,
            label = label,
            readyState = readyState,
            enabled = enabled,
            muted = !enabled || readyState == "muted"
        )
        val updatedTracks = session.tracks + (trackId to track)

        val updated = if (isAudio) {
            session.copy(audioTrackState = stateStr, tracks = updatedTracks)
        } else {
            session.copy(videoTrackState = stateStr, tracks = updatedTracks)
        }
        updateSession(updated)
    }

    fun handleTrackStateChanged(tabId: String, origin: String, trackId: String, kind: String, state: String) {
        val session = getOrCreateSession(tabId, origin, origin)
        val isAudio = kind.equals("audio", ignoreCase = true)

        val existing = session.tracks[trackId]
        val updatedTrack = (existing ?: MediaTrackRuntimeState(trackId = trackId, kind = kind)).copy(
            readyState = state,
            muted = state == "muted" || state == "ended"
        )
        val updatedTracks = session.tracks + (trackId to updatedTrack)

        val updated = if (isAudio) {
            session.copy(audioTrackState = state, tracks = updatedTracks)
        } else {
            session.copy(videoTrackState = state, tracks = updatedTracks)
        }
        updateSession(updated)
    }

    fun handlePeerConnectionCreated(tabId: String, origin: String, pcId: String) {
        val session = getOrCreateSession(tabId, origin, origin)
        val pc = PeerConnectionRuntimeState(pcId = pcId)
        val updatedPcs = session.peerConnections + (pcId to pc)
        val updated = session.copy(
            connectionState = WebRtcSessionState.CONNECTING,
            peerConnections = updatedPcs
        )
        updateSession(updated)
    }

    fun handleConnectionStateChanged(tabId: String, origin: String, pcId: String, state: String) {
        val session = getOrCreateSession(tabId, origin, origin)
        val existing = session.peerConnections[pcId]
        val updatedPc = (existing ?: PeerConnectionRuntimeState(pcId = pcId)).copy(
            connectionState = state
        )
        val updatedPcs = session.peerConnections + (pcId to updatedPc)

        val nextState = when (state.lowercase()) {
            "connecting" -> WebRtcSessionState.CONNECTING
            "connected" -> WebRtcSessionState.CONNECTED
            "disconnected" -> WebRtcSessionState.DISCONNECTED
            "failed" -> WebRtcSessionState.FAILED
            "closed" -> WebRtcSessionState.STOPPED
            else -> session.connectionState
        }

        val updated = session.copy(
            connectionState = nextState,
            lastError = if (nextState == WebRtcSessionState.FAILED) "PeerConnection failed state." else session.lastError,
            peerConnections = updatedPcs
        )
        updateSession(updated)
    }

    fun handleIceConnectionStateChanged(tabId: String, origin: String, pcId: String, state: String) {
        val session = getOrCreateSession(tabId, origin, origin)
        Log.d(TAG, "ICE Connection state changed: pcId=$pcId, state=$state")
        
        val existing = session.peerConnections[pcId]
        val updatedPc = (existing ?: PeerConnectionRuntimeState(pcId = pcId)).copy(
            iceConnectionState = state
        )
        val updatedPcs = session.peerConnections + (pcId to updatedPc)

        var nextState = session.connectionState
        var error: String? = session.lastError

        if (state.lowercase() == "checking") {
            nextState = WebRtcSessionState.CONNECTING
        } else if (state.lowercase() == "disconnected") {
            nextState = WebRtcSessionState.DISCONNECTED
        } else if (state.lowercase() == "failed") {
            nextState = WebRtcSessionState.FAILED
            error = "ICE connection negotiation failed."
        }

        val updated = session.copy(
            connectionState = nextState,
            lastError = error,
            peerConnections = updatedPcs
        )
        updateSession(updated)
    }

    fun handleIceGatheringStateChanged(tabId: String, origin: String, pcId: String, state: String) {
        Log.d(TAG, "ICE Gathering state changed: pcId=$pcId, state=$state")
        val session = getOrCreateSession(tabId, origin, origin)
        val existing = session.peerConnections[pcId]
        val updatedPc = (existing ?: PeerConnectionRuntimeState(pcId = pcId)).copy(
            iceGatheringState = state
        )
        val updatedPcs = session.peerConnections + (pcId to updatedPc)
        val updated = session.copy(peerConnections = updatedPcs)
        updateSession(updated)
    }

    fun handleSignalingStateChanged(tabId: String, origin: String, pcId: String, state: String) {
        Log.d(TAG, "Signaling state changed: pcId=$pcId, state=$state")
        val session = getOrCreateSession(tabId, origin, origin)
        val existing = session.peerConnections[pcId]
        val updatedPc = (existing ?: PeerConnectionRuntimeState(pcId = pcId)).copy(
            signalingState = state
        )
        val updatedPcs = session.peerConnections + (pcId to updatedPc)
        val updated = session.copy(peerConnections = updatedPcs)
        updateSession(updated)
    }

    fun handleReplaceTrack(tabId: String, origin: String, pcId: String, oldTrackId: String?, newTrackId: String?) {
        val session = getOrCreateSession(tabId, origin, origin)
        DiagnosticCenter.logEvent(
            engineName = "webrtc_runtime_engine",
            module = "WebRtcRuntime",
            function = "handleReplaceTrack",
            reason = "Session ${session.sessionId} replaced track $oldTrackId with $newTrackId."
        )
    }

    fun handleStatsUpdated(
        tabId: String,
        origin: String,
        pcId: String,
        rtt: Long?,
        packetsLost: Long?,
        bytesSent: Long?,
        bytesReceived: Long?,
        candidatePairState: String?
    ) {
        val session = getOrCreateSession(tabId, origin, origin)
        
        val statsObj = PeerConnectionStats(
            rtt = rtt,
            packetLoss = packetsLost,
            bytesSent = bytesSent,
            bytesReceived = bytesReceived
        )
        val existing = session.peerConnections[pcId]
        val updatedPc = (existing ?: PeerConnectionRuntimeState(pcId = pcId)).copy(
            lastStats = statsObj
        )
        val updatedPcs = session.peerConnections + (pcId to updatedPc)
        val updated = session.copy(peerConnections = updatedPcs)
        updateSession(updated)

        com.swift.browser.networkstatsengine.WebRtcConnectionDiagnostics.recordDiagnostic(
            tabId = tabId,
            iceState = session.connectionState.name,
            connectionState = "CONNECTED",
            rtt = rtt,
            candidatePairState = candidatePairState,
            packetLoss = packetsLost,
            bytesSent = bytesSent,
            bytesReceived = bytesReceived,
            message = "WebRTC peer connection statistics update: pcId=$pcId"
        )
    }

    /**
     * JavaScript polyfill/spy script to inject into the WebKit layer.
     */
    fun getPolyfillJs(tabId: String): String {
        return """
            (function() {
                if (window.__swift_webrtc_spy_initialized) return;
                window.__swift_webrtc_spy_initialized = true;

                function notifyNative(method, data) {
                    if (window.$INTERFACE_NAME && typeof window.$INTERFACE_NAME[method] === 'function') {
                        try {
                            window.$INTERFACE_NAME[method](JSON.stringify(data));
                        } catch (e) {
                            console.error("Failed to notify native WebRTC bridge:", e);
                        }
                    }
                }

                // Keep original getUserMedia reference
                if (navigator.mediaDevices && typeof navigator.mediaDevices.getUserMedia === 'function') {
                    window.__originalGetUserMedia = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);
                }

                // Track active tracks and peer connections for track replacement
                window.__swift_active_tracks = window.__swift_active_tracks || new Set();
                window.__swift_active_pcs = window.__swift_active_pcs || new Set();

                // Polyfill enumerateDevices
                if (navigator.mediaDevices) {
                    const originalEnumerateDevices = navigator.mediaDevices.enumerateDevices.bind(navigator.mediaDevices);
                    navigator.mediaDevices.enumerateDevices = function() {
                        return new Promise((resolve, reject) => {
                            try {
                                if (window.$INTERFACE_NAME && typeof window.$INTERFACE_NAME.getDevicesJson === 'function') {
                                    const jsonStr = window.$INTERFACE_NAME.getDevicesJson();
                                    const devices = JSON.parse(jsonStr);
                                    resolve(devices.map(d => {
                                        return {
                                            deviceId: d.deviceId,
                                            groupId: d.groupId,
                                            kind: d.kind,
                                            label: d.label,
                                            toJSON: function() { return this; }
                                        };
                                    }));
                                } else {
                                    originalEnumerateDevices().then(resolve).catch(reject);
                                }
                            } catch (e) {
                                console.error("Error in spied enumerateDevices:", e);
                                originalEnumerateDevices().then(resolve).catch(reject);
                            }
                        });
                    };
                }

                // Wrap getUserMedia to spy on track creation and media request states
                if (navigator.mediaDevices && typeof navigator.mediaDevices.getUserMedia === 'function') {
                    const originalGetUserMedia = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);
                    navigator.mediaDevices.getUserMedia = function(constraints) {
                        // Enforce selected device if any
                        if (constraints && window.$INTERFACE_NAME && typeof window.$INTERFACE_NAME.getSelectedDeviceId === 'function') {
                            const selectedVideoId = window.$INTERFACE_NAME.getSelectedDeviceId("$tabId", "videoinput");
                            const selectedAudioId = window.$INTERFACE_NAME.getSelectedDeviceId("$tabId", "audioinput");
                            
                            if (selectedVideoId && constraints.video) {
                                if (typeof constraints.video === 'boolean') {
                                    constraints.video = { deviceId: { exact: selectedVideoId } };
                                } else if (typeof constraints.video === 'object') {
                                    constraints.video.deviceId = { exact: selectedVideoId };
                                }
                            }
                            if (selectedAudioId && constraints.audio) {
                                if (typeof constraints.audio === 'boolean') {
                                    constraints.audio = { deviceId: { exact: selectedAudioId } };
                                } else if (typeof constraints.audio === 'object') {
                                    constraints.audio.deviceId = { exact: selectedAudioId };
                                }
                            }
                        }

                        // Translate any opaque deviceId back to physical ID for the original getUserMedia call
                        if (constraints && window.$INTERFACE_NAME && typeof window.$INTERFACE_NAME.getPhysicalId === 'function') {
                            if (constraints.video && typeof constraints.video === 'object' && constraints.video.deviceId) {
                                const did = constraints.video.deviceId;
                                if (typeof did === 'string') {
                                    constraints.video.deviceId = window.$INTERFACE_NAME.getPhysicalId(did);
                                } else if (typeof did === 'object') {
                                    if (did.exact) did.exact = window.$INTERFACE_NAME.getPhysicalId(did.exact);
                                    if (did.ideal) did.ideal = window.$INTERFACE_NAME.getPhysicalId(did.ideal);
                                }
                            }
                            if (constraints.audio && typeof constraints.audio === 'object' && constraints.audio.deviceId) {
                                const did = constraints.audio.deviceId;
                                if (typeof did === 'string') {
                                    constraints.audio.deviceId = window.$INTERFACE_NAME.getPhysicalId(did);
                                } else if (typeof did === 'object') {
                                    if (did.exact) did.exact = window.$INTERFACE_NAME.getPhysicalId(did.exact);
                                    if (did.ideal) did.ideal = window.$INTERFACE_NAME.getPhysicalId(did.ideal);
                                }
                            }
                        }

                        notifyNative("onGetUserMediaRequested", { constraints: constraints });
                        return originalGetUserMedia(constraints).then(function(stream) {
                            notifyNative("onGetUserMediaSuccess", {
                                hasAudio: stream.getAudioTracks().length > 0,
                                hasVideo: stream.getVideoTracks().length > 0
                            });

                            stream.getTracks().forEach(track => {
                                spyOnTrack(track);
                                window.__swift_active_tracks.add(track);
                            });
                            return stream;
                        }).catch(function(err) {
                            notifyNative("onGetUserMediaFailure", { error: err.name || err.message || "UnknownError" });
                            throw err;
                        });
                    };
                }

                // Wrap getDisplayMedia to spy on screen sharing tracks
                if (navigator.mediaDevices && typeof navigator.mediaDevices.getDisplayMedia === 'function') {
                    const originalGetDisplayMedia = navigator.mediaDevices.getDisplayMedia.bind(navigator.mediaDevices);
                    navigator.mediaDevices.getDisplayMedia = function(constraints) {
                        notifyNative("onGetUserMediaRequested", { constraints: { video: true, audio: false, display: true } });
                        return originalGetDisplayMedia(constraints).then(function(stream) {
                            notifyNative("onGetUserMediaSuccess", {
                                hasAudio: stream.getAudioTracks().length > 0,
                                hasVideo: stream.getVideoTracks().length > 0
                            });

                            stream.getTracks().forEach(track => {
                                spyOnTrack(track);
                                window.__swift_active_tracks.add(track);
                            });
                            return stream;
                        }).catch(function(err) {
                            notifyNative("onGetUserMediaFailure", { error: err.name || err.message || "UnknownError" });
                            throw err;
                        });
                    };
                }

                // Wrap MediaRecorder to observe client-side recording
                const OriginalMediaRecorder = window.MediaRecorder;
                if (OriginalMediaRecorder) {
                    const SpiedMediaRecorder = function(stream, options) {
                        const recorder = new OriginalMediaRecorder(stream, options);
                        notifyNative("onTrackAdded", {
                            id: "recorder_" + Math.random().toString(36).substr(2, 9),
                            kind: "mediarecorder",
                            label: "MediaRecorder Instance",
                            readyState: "live",
                            enabled: true
                        });
                        return recorder;
                    };
                    SpiedMediaRecorder.prototype = OriginalMediaRecorder.prototype;
                    for (let prop in OriginalMediaRecorder) {
                        if (OriginalMediaRecorder.hasOwnProperty(prop)) {
                            SpiedMediaRecorder[prop] = OriginalMediaRecorder[prop];
                        }
                    }
                    window.MediaRecorder = SpiedMediaRecorder;
                }

                function spyOnTrack(track) {
                    if (track.__swift_spied) return;
                    track.__swift_spied = true;

                    notifyNative("onTrackAdded", {
                        id: track.id,
                        kind: track.kind,
                        label: track.label,
                        readyState: track.readyState,
                        enabled: track.enabled
                    });

                    track.addEventListener('ended', function() {
                        notifyNative("onTrackEnded", { id: track.id, kind: track.kind });
                        if (window.__swift_active_tracks) window.__swift_active_tracks.delete(track);
                    });
                    track.addEventListener('mute', function() {
                        notifyNative("onTrackMuted", { id: track.id, kind: track.kind });
                    });
                    track.addEventListener('unmute', function() {
                        notifyNative("onTrackUnmuted", { id: track.id, kind: track.kind });
                    });
                }

                // Wrap RTCPeerConnection to spy on connection states
                const OriginalRTCPeerConnection = window.RTCPeerConnection || window.webkitRTCPeerConnection;
                if (OriginalRTCPeerConnection) {
                    const SpiedRTCPeerConnection = function(config, constraints) {
                        const pc = new OriginalRTCPeerConnection(config, constraints);
                        const pcId = 'pc_' + Math.random().toString(36).substr(2, 9);
                        pc.__swift_pc_id = pcId;

                        window.__swift_active_pcs.add(pc);

                        notifyNative("onPeerConnectionCreated", { pcId: pcId, config: config });

                        const originalClose = pc.close;
                        pc.close = function() {
                            window.__swift_active_pcs.delete(pc);
                            return originalClose.apply(pc, arguments);
                        };

                        pc.addEventListener('connectionstatechange', function() {
                            notifyNative("onConnectionStateChanged", { pcId: pcId, state: pc.connectionState });
                            if (pc.connectionState === 'closed') {
                                window.__swift_active_pcs.delete(pc);
                            }
                        });
                        pc.addEventListener('iceconnectionstatechange', function() {
                            notifyNative("onIceConnectionStateChanged", { pcId: pcId, state: pc.iceConnectionState });
                        });
                        pc.addEventListener('icegatheringstatechange', function() {
                            notifyNative("onIceGatheringStateChanged", { pcId: pcId, state: pc.iceGatheringState });
                        });
                        pc.addEventListener('signalingstatechange', function() {
                            notifyNative("onSignalingStateChanged", { pcId: pcId, state: pc.signalingState });
                        });

                        const originalAddTrack = pc.addTrack;
                        if (typeof originalAddTrack === 'function') {
                            pc.addTrack = function(track, ...streams) {
                                spyOnTrack(track);
                                notifyNative("onPeerConnectionAddTrack", { pcId: pcId, trackId: track.id, kind: track.kind });
                                return originalAddTrack.call(pc, track, ...streams);
                            };
                        }

                        const originalReplaceTrack = pc.replaceTrack;
                        if (typeof originalReplaceTrack === 'function') {
                            pc.replaceTrack = function(oldTrack, newTrack) {
                                if (newTrack) spyOnTrack(newTrack);
                                notifyNative("onPeerConnectionReplaceTrack", { pcId: pcId, oldTrackId: oldTrack ? oldTrack.id : null, newTrackId: newTrack ? newTrack.id : null });
                                return originalReplaceTrack.call(pc, oldTrack, newTrack);
                            };
                        }

                        return pc;
                    };

                    SpiedRTCPeerConnection.prototype = OriginalRTCPeerConnection.prototype;
                    for (let prop in OriginalRTCPeerConnection) {
                        if (OriginalRTCPeerConnection.hasOwnProperty(prop)) {
                            SpiedRTCPeerConnection[prop] = OriginalRTCPeerConnection[prop];
                        }
                    }

                    if (window.RTCPeerConnection) window.RTCPeerConnection = SpiedRTCPeerConnection;
                    if (window.webkitRTCPeerConnection) window.webkitRTCPeerConnection = SpiedRTCPeerConnection;
                }

                // Expose switch device functionality to native
                window.AndroidWebRtcBridge_switchDevice = function(kind, newDeviceId, operationId) {
                    return new Promise(function(resolve, reject) {
                        const enumDevices = (navigator.mediaDevices && typeof navigator.mediaDevices.enumerateDevices === 'function')
                            ? navigator.mediaDevices.enumerateDevices.bind(navigator.mediaDevices)
                            : null;

                        const validatePromise = enumDevices ? enumDevices() : Promise.resolve([]);

                        validatePromise.then(function(devices) {
                            const physicalId = (window.$INTERFACE_NAME && typeof window.$INTERFACE_NAME.getPhysicalId === 'function')
                                ? window.$INTERFACE_NAME.getPhysicalId(newDeviceId)
                                : newDeviceId;

                            if (devices && devices.length > 0) {
                                const targetKind = (kind === 'videoinput') ? 'videoinput' : 'audioinput';
                                const found = devices.some(d => d.kind === targetKind && (d.deviceId === newDeviceId || d.deviceId === physicalId || d.deviceId === "mic_" + physicalId || d.deviceId === "cam_" + physicalId));
                                if (!found) {
                                    throw new Error("Device " + newDeviceId + " of kind " + kind + " not found in enumerated devices.");
                                }
                            }

                            const constraints = {};
                            if (kind === 'videoinput') {
                                constraints.video = { deviceId: { exact: physicalId } };
                            } else {
                                constraints.audio = { deviceId: { exact: physicalId } };
                            }

                            const originalGetUserMedia = window.__originalGetUserMedia || (navigator.mediaDevices && navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices));
                            if (!originalGetUserMedia) {
                                throw new Error("getUserMedia is not supported by the WebView runtime");
                            }

                            return originalGetUserMedia(constraints).then(function(newStream) {
                                const newTrack = kind === 'videoinput' ? newStream.getVideoTracks()[0] : newStream.getAudioTracks()[0];
                                if (!newTrack) {
                                    throw new Error("No track acquired for device: " + newDeviceId);
                                }

                                if (newTrack.readyState !== 'live') {
                                    try { newTrack.stop(); } catch(e){}
                                    throw new Error("Acquired track is not in live state: " + newTrack.readyState);
                                }

                                spyOnTrack(newTrack);

                                const replacements = [];
                                const oldTracksToStop = [];

                                if (window.__swift_active_tracks) {
                                    window.__swift_active_tracks.forEach(function(oldTrack) {
                                        if (oldTrack.kind === newTrack.kind && oldTrack !== newTrack && oldTrack.readyState === 'live') {
                                            oldTracksToStop.push(oldTrack);
                                            if (window.__swift_active_pcs) {
                                                window.__swift_active_pcs.forEach(function(pc) {
                                                    if (typeof pc.getSenders === 'function') {
                                                        const senders = pc.getSenders();
                                                        const sender = senders.find(s => s.track === oldTrack);
                                                        if (sender && typeof sender.replaceTrack === 'function') {
                                                            const p = sender.replaceTrack(newTrack).catch(function(err) {
                                                                console.error("replaceTrack failed for peer connection:", err);
                                                                throw err;
                                                            });
                                                            replacements.push(p);
                                                        }
                                                    }
                                                });
                                            }
                                        }
                                    });
                                }

                                return Promise.all(replacements).then(function() {
                                    // Only stop old tracks AFTER replaceTrack has succeeded!
                                    oldTracksToStop.forEach(function(oldTrack) {
                                        try {
                                            oldTrack.stop();
                                        } catch(e) {
                                            console.error("Error stopping old track:", e);
                                        }
                                        if (window.__swift_active_tracks) {
                                            window.__swift_active_tracks.delete(oldTrack);
                                        }
                                    });
                                    if (window.__swift_active_tracks) {
                                        window.__swift_active_tracks.add(newTrack);
                                    }
                                    return true;
                                }).catch(function(err) {
                                    try { newTrack.stop(); } catch(e){}
                                    throw err;
                                });
                            });
                        }).then(resolve).catch(reject);
                    });
                };

                // Expose ICE restart function to native recovery coordinator
                window.AndroidWebRtcBridge_triggerIceRestart = function(pcId) {
                    let triggered = false;
                    if (window.__swift_active_pcs) {
                        window.__swift_active_pcs.forEach(function(pc) {
                            if (pc.__swift_pc_id === pcId && typeof pc.restartIce === 'function') {
                                try {
                                    pc.restartIce();
                                    triggered = true;
                                    console.log("Orion recovery: ICE restarted successfully for pcId: " + pcId);
                                } catch (e) {
                                    console.error("Orion recovery: Failed pc.restartIce() for pcId: " + pcId, e);
                                }
                            }
                        });
                    }
                    return triggered;
                };

                // Periodic statistics poller for active PeerConnections
                if (!window.__swift_webrtc_stats_poller_registered) {
                    window.__swift_webrtc_stats_poller_registered = true;
                    setInterval(function() {
                        if (window.__swift_active_pcs) {
                            window.__swift_active_pcs.forEach(function(pc) {
                                if (pc && pc.connectionState !== 'closed' && typeof pc.getStats === 'function') {
                                    pc.getStats(null).then(function(stats) {
                                        let rtt = null;
                                        let packetsLost = null;
                                        let bytesSent = null;
                                        let bytesReceived = null;
                                        let candidatePairState = null;

                                        stats.forEach(function(report) {
                                            if (report.type === 'candidate-pair' && report.state === 'succeeded') {
                                                candidatePairState = report.state;
                                                if (typeof report.currentRoundTripTime === 'number') {
                                                    rtt = Math.round(report.currentRoundTripTime * 1000);
                                                }
                                            }
                                            if (report.type === 'inbound-rtp') {
                                                if (typeof report.packetsLost === 'number') {
                                                    packetsLost = (packetsLost || 0) + report.packetsLost;
                                                }
                                                if (typeof report.bytesReceived === 'number') {
                                                    bytesReceived = (bytesReceived || 0) + report.bytesReceived;
                                                }
                                            }
                                            if (report.type === 'outbound-rtp') {
                                                if (typeof report.bytesSent === 'number') {
                                                    bytesSent = (bytesSent || 0) + report.bytesSent;
                                                }
                                            }
                                        });

                                        let pcId = pc.__swift_pc_id || "pc_unknown";

                                        notifyNative("onStatsUpdated", {
                                            pcId: pcId,
                                            rtt: rtt,
                                            packetsLost: packetsLost,
                                            bytesSent: bytesSent,
                                            bytesReceived: bytesReceived,
                                            candidatePairState: candidatePairState
                                        });
                                    }).catch(function(err) {
                                        console.error("Failed to query WebRTC getStats:", err);
                                    });
                                }
                            });
                        }
                    }, 3000);
                }
            })();
        """.trimIndent()
    }
}
