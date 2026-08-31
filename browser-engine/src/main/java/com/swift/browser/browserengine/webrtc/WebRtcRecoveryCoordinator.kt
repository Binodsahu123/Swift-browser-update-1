package com.swift.browser.browserengine.webrtc

import android.content.Context
import android.util.Log
import android.webkit.WebView
import com.swift.browser.networkcore.WebRtcNetworkMonitor
import com.swift.browser.networkcore.WebRtcNetworkState
import com.swift.browser.networkcore.WebRtcNetworkType
import com.swift.browser.networkstatsengine.RecoveryTraceModel
import com.swift.browser.networkstatsengine.TraceRepository
import com.swift.browser.networkstatsengine.WebRtcConnectionDiagnostics
import kotlinx.coroutines.*

object WebRtcRecoveryCoordinator : WebRtcNetworkMonitor.NetworkObserver {
    private const val TAG = "WebRtcRecoveryCoordinator"

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var activeRecoveryJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private var isMonitoring = false

    fun start(context: Context) {
        synchronized(this) {
            if (isMonitoring) return
            WebRtcNetworkMonitor.startMonitoring(context)
            WebRtcNetworkMonitor.registerObserver(this)
            isMonitoring = true
            Log.i(TAG, "WebRtcRecoveryCoordinator successfully started and observing network changes.")
        }
    }

    fun stop() {
        synchronized(this) {
            if (!isMonitoring) return
            WebRtcNetworkMonitor.unregisterObserver(this)
            WebRtcNetworkMonitor.stopMonitoring()
            isMonitoring = false
            activeRecoveryJobs.values.forEach { it.cancel() }
            activeRecoveryJobs.clear()
            Log.i(TAG, "WebRtcRecoveryCoordinator stopped.")
        }
    }

    fun cancelRecoveryForTab(tabId: String) {
        activeRecoveryJobs.remove(tabId)?.cancel()
        Log.d(TAG, "Cancelled active recovery job for tab: $tabId")
    }

    override fun onNetworkChanged(type: WebRtcNetworkType, state: WebRtcNetworkState) {
        val message = "Network interface updated to transport: $type, state: $state"
        Log.i(TAG, message)

        // Log general network trace to central diagnostic repository
        TraceRepository.addTrace(
            RecoveryTraceModel(
                message = message,
                engineId = "webrtc_recovery_coordinator",
                success = state == WebRtcNetworkState.CONNECTED
            )
        )

        // Propagate network state transition directly to WebRTC manager
        val isConnected = state == WebRtcNetworkState.CONNECTED
        WebRtcRuntimeManager.handleNetworkTransition(isConnected)

        // Evaluate and initiate recovery sequences for active failed/disconnected sessions
        val activeSessions = WebRtcRuntimeManager.getActiveSessions()
        for (session in activeSessions) {
            // Record initial transition diagnostics
            WebRtcConnectionDiagnostics.recordDiagnostic(
                tabId = session.tabId,
                iceState = session.connectionState.name,
                connectionState = state.name,
                rtt = null,
                candidatePairState = "unknown",
                packetLoss = null,
                bytesSent = null,
                bytesReceived = null,
                message = "Network status changed: type=$type state=$state"
            )

            if (isConnected) {
                // If we recovered connection, launch natural recovery evaluation job
                val tabId = session.tabId
                // Cancel any previous recovery jobs for this tab to prevent conflicts
                activeRecoveryJobs[tabId]?.cancel()
                
                val job = coroutineScope.launch {
                    performSessionRecovery(tabId)
                }
                activeRecoveryJobs[tabId] = job
            }
        }
    }

    /**
     * Conducts a stepped, non-disruptive recovery process for a given tab.
     */
    private suspend fun performSessionRecovery(tabId: String) {
        Log.i(TAG, "Initiating WebRTC recovery evaluation for tab: $tabId")
        
        // Step 1: Let Chromium/WebRTC attempt natural recovery first (Grace Period)
        delay(3000)

        // Verify if tab session is still registered and hasn't been closed
        val activeSessions = WebRtcRuntimeManager.getSessionsForTab(tabId)
        if (activeSessions.isEmpty()) {
            Log.d(TAG, "No active WebRTC sessions for tab $tabId. Skipping recovery.")
            return
        }

        val failedSession = activeSessions.find { 
            it.connectionState == WebRtcSessionState.FAILED || 
            it.connectionState == WebRtcSessionState.DISCONNECTED 
        }

        if (failedSession == null) {
            Log.i(TAG, "Tab $tabId WebRTC recovered naturally. No safe actions needed.")
            TraceRepository.addTrace(
                RecoveryTraceModel(
                    message = "WebRTC tab $tabId recovered naturally during grace period.",
                    engineId = "webrtc_recovery_coordinator",
                    success = true
                )
            )
            return
        }

        // Retrieve WebView instance
        val webView = WebMediaDeviceManager.getWebView(tabId)
        if (webView == null) {
            Log.w(TAG, "WebView for tab $tabId is gone or garbage collected. Aborting recovery.")
            return
        }

        // TIER 1: ICE RESTART
        Log.i(TAG, "Tier 1 Recovery: Triggering ICE Restart for tab: $tabId")
        TraceRepository.addTrace(
            RecoveryTraceModel(
                message = "Tier 1: Triggering ICE Restart for active PeerConnections on tab $tabId.",
                engineId = "webrtc_recovery_coordinator",
                success = false
            )
        )

        withContext(Dispatchers.Main) {
            failedSession.peerConnections.keys.forEach { pcId ->
                webView.evaluateJavascript(
                    "if (typeof window.AndroidWebRtcBridge_triggerIceRestart === 'function') { window.AndroidWebRtcBridge_triggerIceRestart('$pcId'); }",
                    null
                )
            }
        }

        // Wait 5 seconds for ICE Restart to transition to connected state
        delay(5000)

        // Verify if any PeerConnection state transitioned to "connected" or if session as a whole recovered
        var currentSessions = WebRtcRuntimeManager.getSessionsForTab(tabId)
        var recovered = currentSessions.any { s ->
            s.connectionState == WebRtcSessionState.CONNECTED || 
            s.peerConnections.values.any { it.connectionState == "connected" }
        }

        if (recovered) {
            Log.i(TAG, "Tier 1 Recovery succeeded: ICE Restart recovered WebRTC session for tab $tabId.")
            TraceRepository.addTrace(
                RecoveryTraceModel(
                    message = "Tier 1 Recovery succeeded: ICE Restart recovered WebRTC session.",
                    engineId = "webrtc_recovery_coordinator",
                    success = true
                )
            )
            return
        }

        // TIER 2: TRACK RE-ACQUISITION (re-getUserMedia)
        Log.i(TAG, "Tier 2 Recovery: Attempting track re-acquisition (re-getUserMedia) for tab: $tabId")
        TraceRepository.addTrace(
            RecoveryTraceModel(
                message = "Tier 1 failed. Tier 2: Attempting dynamic track re-acquisition on tab $tabId.",
                engineId = "webrtc_recovery_coordinator",
                success = false
            )
        )

        withContext(Dispatchers.Main) {
            webView.evaluateJavascript(
                """
                (function() {
                    if (navigator.mediaDevices && typeof navigator.mediaDevices.getUserMedia === 'function') {
                        navigator.mediaDevices.getUserMedia({ video: true, audio: true })
                            .then(function(stream) {
                                console.log("Orion recovery: Track re-acquisition succeeded.");
                            })
                            .catch(function(err) {
                                console.error("Orion recovery: Track re-acquisition failed", err);
                            });
                    }
                })()
                """.trimIndent(),
                null
            )
        }

        // Wait 4 seconds for track re-acquisition to finish negotiating
        delay(4000)

        // Verify if recovered
        currentSessions = WebRtcRuntimeManager.getSessionsForTab(tabId)
        recovered = currentSessions.any { s ->
            s.connectionState == WebRtcSessionState.CONNECTED || 
            s.peerConnections.values.any { it.connectionState == "connected" }
        }

        if (recovered) {
            Log.i(TAG, "Tier 2 Recovery succeeded: Track re-acquisition recovered WebRTC session for tab $tabId.")
            TraceRepository.addTrace(
                RecoveryTraceModel(
                    message = "Tier 2 Recovery succeeded: Track re-acquisition recovered WebRTC session.",
                    engineId = "webrtc_recovery_coordinator",
                    success = true
                )
            )
            return
        }

        // TIER 3: REJECT / FORCE FAIL THE SESSION & RELOAD PAGE
        Log.e(TAG, "Tier 3 Recovery: Soft recovery tiers exhausted. Reloading page for tab $tabId")
        TraceRepository.addTrace(
            RecoveryTraceModel(
                message = "Tier 2 failed. Tier 3: Hard failure. Reloading WebView page for tab $tabId.",
                engineId = "webrtc_recovery_coordinator",
                success = false
            )
        )

        // Inject diagnostic failure trace in manager
        WebRtcRuntimeManager.getSessionsForTab(tabId).forEach { s ->
            WebRtcRuntimeManager.handleGetUserMediaFailure(
                tabId = tabId,
                origin = s.origin,
                error = "Tiered recovery failed to restore WebRTC streaming."
            )
        }

        withContext(Dispatchers.Main) {
            if (WebMediaDeviceManager.getWebView(tabId) != null) {
                webView.reload()
                Log.i(TAG, "WebView page successfully reloaded for tab $tabId.")
            }
        }
    }
}
