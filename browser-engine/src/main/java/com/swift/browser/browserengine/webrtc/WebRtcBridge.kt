package com.swift.browser.browserengine.webrtc

import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.swift.browser.permissionengine.OriginNormalizer
import org.json.JSONObject

/**
 * JavaScript interface that safely binds native state machine updates to JavaScript WebRTC events spied on the page.
 */
class WebRtcBridge(
    private val webView: WebView,
    private val tabId: String
) {
    private val TAG = "WebRtcBridge"

    private val currentOrigin: String
        get() {
            val url = webView.url ?: ""
            return OriginNormalizer.normalize(url)
        }

    init {
        WebMediaDeviceManager.registerWebView(tabId, webView)
    }

    private fun validateEvent(pcId: String? = null, trackId: String? = null, requiredActive: Boolean = true): Boolean {
        val origin = currentOrigin
        if (origin.isBlank()) {
            Log.w(TAG, "Rejected bridge event: current webview origin is blank.")
            return false
        }
        val sessions = WebRtcRuntimeManager.getSessionsForTab(tabId)
        val session = sessions.firstOrNull { it.origin == origin }
        if (session == null) {
            if (requiredActive) {
                Log.w(TAG, "Rejected bridge event: no active session found for tabId=$tabId and origin=$origin")
                return false
            }
            return true
        }

        if (pcId != null && !session.peerConnections.containsKey(pcId)) {
            Log.w(TAG, "Rejected bridge event: unknown pcId=$pcId in session=${session.sessionId}")
            return false
        }

        if (trackId != null && !session.tracks.containsKey(trackId)) {
            Log.w(TAG, "Rejected bridge event: unknown trackId=$trackId in session=${session.sessionId}")
            return false
        }

        return true
    }

    @JavascriptInterface
    fun getDevicesJson(): String {
        return WebMediaDeviceManager.getDevicesJson(webView.context, tabId, currentOrigin)
    }

    @JavascriptInterface
    fun getSelectedDeviceId(checkTabId: String, kind: String): String {
        if (checkTabId != tabId) {
            Log.w(TAG, "getSelectedDeviceId rejected: tabId mismatch ($checkTabId vs $tabId)")
            return ""
        }
        return WebMediaDeviceManager.getSelectedDeviceId(checkTabId, kind) ?: ""
    }

    @JavascriptInterface
    fun getPhysicalId(opaqueId: String): String {
        return WebDeviceIdentityManager.getPhysicalId(tabId, currentOrigin, opaqueId) ?: opaqueId
    }

    @JavascriptInterface
    fun onGetUserMediaRequested(dataStr: String) {
        try {
            Log.d(TAG, "onGetUserMediaRequested: $dataStr")
            if (!validateEvent(requiredActive = false)) return

            val obj = JSONObject(dataStr)
            val constraints = obj.optJSONObject("constraints")
            val hasVideo = constraints?.has("video") == true
            val hasAudio = constraints?.has("audio") == true

            WebRtcRuntimeManager.handleGetUserMediaRequested(
                tabId = tabId,
                origin = currentOrigin,
                hasVideo = hasVideo,
                hasAudio = hasAudio
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing onGetUserMediaRequested: ${e.message}")
        }
    }

    @JavascriptInterface
    fun onGetUserMediaSuccess(dataStr: String) {
        try {
            Log.d(TAG, "onGetUserMediaSuccess: $dataStr")
            if (!validateEvent(requiredActive = true)) return

            val obj = JSONObject(dataStr)
            val hasAudio = obj.optBoolean("hasAudio", false)
            val hasVideo = obj.optBoolean("hasVideo", false)

            WebRtcRuntimeManager.handleGetUserMediaSuccess(
                tabId = tabId,
                origin = currentOrigin,
                hasAudio = hasAudio,
                hasVideo = hasVideo
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing onGetUserMediaSuccess: ${e.message}")
        }
    }

    @JavascriptInterface
    fun onGetUserMediaFailure(dataStr: String) {
        try {
            Log.d(TAG, "onGetUserMediaFailure: $dataStr")
            if (!validateEvent(requiredActive = true)) return

            val obj = JSONObject(dataStr)
            val error = obj.optString("error", "UnknownError")

            WebRtcRuntimeManager.handleGetUserMediaFailure(
                tabId = tabId,
                origin = currentOrigin,
                error = error
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing onGetUserMediaFailure: ${e.message}")
        }
    }

    @JavascriptInterface
    fun onTrackAdded(dataStr: String) {
        try {
            Log.d(TAG, "onTrackAdded: $dataStr")
            if (!validateEvent(requiredActive = true)) return

            val obj = JSONObject(dataStr)
            val id = obj.optString("id")
            val kind = obj.optString("kind")
            val label = obj.optString("label")
            val readyState = obj.optString("readyState", "live")
            val enabled = obj.optBoolean("enabled", true)

            WebRtcRuntimeManager.handleTrackAdded(
                tabId = tabId,
                origin = currentOrigin,
                trackId = id,
                kind = kind,
                label = label,
                readyState = readyState,
                enabled = enabled
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing onTrackAdded: ${e.message}")
        }
    }

    @JavascriptInterface
    fun onTrackEnded(dataStr: String) {
        try {
            Log.d(TAG, "onTrackEnded: $dataStr")
            val obj = JSONObject(dataStr)
            val id = obj.optString("id")
            val kind = obj.optString("kind")

            if (!validateEvent(trackId = id, requiredActive = true)) return

            WebRtcRuntimeManager.handleTrackStateChanged(
                tabId = tabId,
                origin = currentOrigin,
                trackId = id,
                kind = kind,
                state = "ended"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing onTrackEnded: ${e.message}")
        }
    }

    @JavascriptInterface
    fun onTrackMuted(dataStr: String) {
        try {
            Log.d(TAG, "onTrackMuted: $dataStr")
            val obj = JSONObject(dataStr)
            val id = obj.optString("id")
            val kind = obj.optString("kind")

            if (!validateEvent(trackId = id, requiredActive = true)) return

            WebRtcRuntimeManager.handleTrackStateChanged(
                tabId = tabId,
                origin = currentOrigin,
                trackId = id,
                kind = kind,
                state = "muted"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing onTrackMuted: ${e.message}")
        }
    }

    @JavascriptInterface
    fun onTrackUnmuted(dataStr: String) {
        try {
            Log.d(TAG, "onTrackUnmuted: $dataStr")
            val obj = JSONObject(dataStr)
            val id = obj.optString("id")
            val kind = obj.optString("kind")

            if (!validateEvent(trackId = id, requiredActive = true)) return

            WebRtcRuntimeManager.handleTrackStateChanged(
                tabId = tabId,
                origin = currentOrigin,
                trackId = id,
                kind = kind,
                state = "active"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing onTrackUnmuted: ${e.message}")
        }
    }

    @JavascriptInterface
    fun onPeerConnectionCreated(dataStr: String) {
        try {
            Log.d(TAG, "onPeerConnectionCreated: $dataStr")
            if (!validateEvent(requiredActive = true)) return

            val obj = JSONObject(dataStr)
            val pcId = obj.optString("pcId")

            WebRtcRuntimeManager.handlePeerConnectionCreated(
                tabId = tabId,
                origin = currentOrigin,
                pcId = pcId
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing onPeerConnectionCreated: ${e.message}")
        }
    }

    @JavascriptInterface
    fun onConnectionStateChanged(dataStr: String) {
        try {
            Log.d(TAG, "onConnectionStateChanged: $dataStr")
            val obj = JSONObject(dataStr)
            val pcId = obj.optString("pcId")
            val state = obj.optString("state")

            if (!validateEvent(pcId = pcId, requiredActive = true)) return

            WebRtcRuntimeManager.handleConnectionStateChanged(
                tabId = tabId,
                origin = currentOrigin,
                pcId = pcId,
                state = state
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing onConnectionStateChanged: ${e.message}")
        }
    }

    @JavascriptInterface
    fun onIceConnectionStateChanged(dataStr: String) {
        try {
            Log.d(TAG, "onIceConnectionStateChanged: $dataStr")
            val obj = JSONObject(dataStr)
            val pcId = obj.optString("pcId")
            val state = obj.optString("state")

            if (!validateEvent(pcId = pcId, requiredActive = true)) return

            WebRtcRuntimeManager.handleIceConnectionStateChanged(
                tabId = tabId,
                origin = currentOrigin,
                pcId = pcId,
                state = state
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing onIceConnectionStateChanged: ${e.message}")
        }
    }

    @JavascriptInterface
    fun onIceGatheringStateChanged(dataStr: String) {
        try {
            Log.d(TAG, "onIceGatheringStateChanged: $dataStr")
            val obj = JSONObject(dataStr)
            val pcId = obj.optString("pcId")
            val state = obj.optString("state")

            if (!validateEvent(pcId = pcId, requiredActive = true)) return

            WebRtcRuntimeManager.handleIceGatheringStateChanged(
                tabId = tabId,
                origin = currentOrigin,
                pcId = pcId,
                state = state
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing onIceGatheringStateChanged: ${e.message}")
        }
    }

    @JavascriptInterface
    fun onSignalingStateChanged(dataStr: String) {
        try {
            Log.d(TAG, "onSignalingStateChanged: $dataStr")
            val obj = JSONObject(dataStr)
            val pcId = obj.optString("pcId")
            val state = obj.optString("state")

            if (!validateEvent(pcId = pcId, requiredActive = true)) return

            WebRtcRuntimeManager.handleSignalingStateChanged(
                tabId = tabId,
                origin = currentOrigin,
                pcId = pcId,
                state = state
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing onSignalingStateChanged: ${e.message}")
        }
    }

    @JavascriptInterface
    fun onPeerConnectionAddTrack(dataStr: String) {
        try {
            Log.d(TAG, "onPeerConnectionAddTrack: $dataStr")
            val obj = JSONObject(dataStr)
            val pcId = obj.optString("pcId")
            val trackId = obj.optString("trackId")

            if (!validateEvent(pcId = pcId, trackId = trackId, requiredActive = true)) return

            // Routed successfully but optionally logged/noop
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing onPeerConnectionAddTrack: ${e.message}")
        }
    }

    @JavascriptInterface
    fun onPeerConnectionReplaceTrack(dataStr: String) {
        try {
            Log.d(TAG, "onPeerConnectionReplaceTrack: $dataStr")
            val obj = JSONObject(dataStr)
            val pcId = obj.optString("pcId")
            val oldTrackId = obj.optString("oldTrackId")
            val newTrackId = obj.optString("newTrackId")

            val finalOldTrackId = if (oldTrackId.isNullOrBlank()) null else oldTrackId
            val finalNewTrackId = if (newTrackId.isNullOrBlank()) null else newTrackId

            if (!validateEvent(pcId = pcId, requiredActive = true)) return
            if (finalOldTrackId != null && !validateEvent(trackId = finalOldTrackId, requiredActive = true)) return

            WebRtcRuntimeManager.handleReplaceTrack(
                tabId = tabId,
                origin = currentOrigin,
                pcId = pcId,
                oldTrackId = finalOldTrackId,
                newTrackId = finalNewTrackId
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing onPeerConnectionReplaceTrack: ${e.message}")
        }
    }

    @JavascriptInterface
    fun onStatsUpdated(dataStr: String) {
        try {
            val obj = JSONObject(dataStr)
            val pcId = obj.optString("pcId")
            val rtt = if (obj.isNull("rtt")) null else obj.optLong("rtt")
            val packetsLost = if (obj.isNull("packetsLost")) null else obj.optLong("packetsLost")
            val bytesSent = if (obj.isNull("bytesSent")) null else obj.optLong("bytesSent")
            val bytesReceived = if (obj.isNull("bytesReceived")) null else obj.optLong("bytesReceived")
            val candidatePairState = if (obj.isNull("candidatePairState")) null else obj.optString("candidatePairState")

            if (!validateEvent(pcId = pcId, requiredActive = true)) return

            WebRtcRuntimeManager.handleStatsUpdated(
                tabId = tabId,
                origin = currentOrigin,
                pcId = pcId,
                rtt = rtt,
                packetsLost = packetsLost,
                bytesSent = bytesSent,
                bytesReceived = bytesReceived,
                candidatePairState = candidatePairState
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing onStatsUpdated: ${e.message}")
        }
    }

    @JavascriptInterface
    fun onDeviceSwitchSuccess(tabId: String, kind: String, deviceId: String, operationId: String) {
        try {
            Log.d(TAG, "onDeviceSwitchSuccess: tabId=$tabId, kind=$kind, deviceId=$deviceId, operationId=$operationId")
            if (tabId != this.tabId) {
                Log.w(TAG, "onDeviceSwitchSuccess rejected: tabId mismatch")
                return
            }
            if (!validateEvent(requiredActive = true)) return

            WebMediaSourceManager.handleDeviceSwitchSuccess(tabId, kind, deviceId, operationId)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling onDeviceSwitchSuccess: ${e.message}")
        }
    }

    @JavascriptInterface
    fun onDeviceSwitchFailure(tabId: String, kind: String, deviceId: String, error: String, operationId: String) {
        try {
            Log.e(TAG, "onDeviceSwitchFailure: tabId=$tabId, kind=$kind, deviceId=$deviceId, error=$error, operationId=$operationId")
            if (tabId != this.tabId) {
                Log.w(TAG, "onDeviceSwitchFailure rejected: tabId mismatch")
                return
            }
            if (!validateEvent(requiredActive = true)) return

            WebMediaSourceManager.handleDeviceSwitchFailure(tabId, kind, deviceId, error, operationId)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling onDeviceSwitchFailure: ${e.message}")
        }
    }
}
