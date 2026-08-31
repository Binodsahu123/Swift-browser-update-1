package com.swift.browser.browserengine.webrtc

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.swift.browser.browserengine.WebMediaCompatibilityEngine
import org.json.JSONObject

enum class WebMediaCapabilityStatus {
    SUPPORTED,
    PARTIAL,
    UNSUPPORTED_BY_WEBVIEW,
    UNSUPPORTED_BY_ANDROID,
    UNSUPPORTED_BY_RUNTIME,
    SECURE_CONTEXT_REQUIRED,
    PERMISSION_REQUIRED,
    REQUIRES_NATIVE_BRIDGE,
    RUNTIME_ERROR
}

data class WebMediaCapabilityMatrix(
    val mediaDevices: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val getUserMedia: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val enumerateDevices: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val mediaStream: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val mediaStreamTrack: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val rtcPeerConnection: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val rtcDataChannel: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val rtcRtpSender: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val replaceTrack: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val mediaRecorder: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val getDisplayMedia: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val webGl: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val webAudio: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val audioContext: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val secureContext: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val deviceEnumeration: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val camera: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val microphone: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val screenCapture: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val audioOutputSelection: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val fullscreen: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val fileUpload: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val clipboard: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    
    // New fine-grained WebRTC & Web API runtime specifications
    val rtcRtpReceiver: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val rtcRtpTransceiver: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val rtcIceCandidate: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val rtcSessionDescription: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val restartIce: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val getStats: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val mediaStreamTrackStop: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val mediaStreamTrackClone: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val mediaStreamTrackGetSettings: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val mediaStreamTrackGetCapabilities: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val serviceWorker: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val indexedDb: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
    val webSocket: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW
) {

    fun toMap(): Map<String, String> {
        return mapOf(
            "mediaDevices" to mediaDevices.name,
            "getUserMedia" to getUserMedia.name,
            "enumerateDevices" to enumerateDevices.name,
            "mediaStream" to mediaStream.name,
            "mediaStreamTrack" to mediaStreamTrack.name,
            "rtcPeerConnection" to rtcPeerConnection.name,
            "rtcDataChannel" to rtcDataChannel.name,
            "rtcRtpSender" to rtcRtpSender.name,
            "replaceTrack" to replaceTrack.name,
            "mediaRecorder" to mediaRecorder.name,
            "getDisplayMedia" to getDisplayMedia.name,
            "webGl" to webGl.name,
            "webAudio" to webAudio.name,
            "audioContext" to audioContext.name,
            "secureContext" to secureContext.name,
            "deviceEnumeration" to deviceEnumeration.name,
            "camera" to camera.name,
            "microphone" to microphone.name,
            "screenCapture" to screenCapture.name,
            "audioOutputSelection" to audioOutputSelection.name,
            "fullscreen" to fullscreen.name,
            "fileUpload" to fileUpload.name,
            "clipboard" to clipboard.name,
            
            // New fine-grained fields
            "rtcRtpReceiver" to rtcRtpReceiver.name,
            "rtcRtpTransceiver" to rtcRtpTransceiver.name,
            "rtcIceCandidate" to rtcIceCandidate.name,
            "rtcSessionDescription" to rtcSessionDescription.name,
            "restartIce" to restartIce.name,
            "getStats" to getStats.name,
            "mediaStreamTrackStop" to mediaStreamTrackStop.name,
            "mediaStreamTrackClone" to mediaStreamTrackClone.name,
            "mediaStreamTrackGetSettings" to mediaStreamTrackGetSettings.name,
            "mediaStreamTrackGetCapabilities" to mediaStreamTrackGetCapabilities.name,
            "serviceWorker" to serviceWorker.name,
            "indexedDb" to indexedDb.name,
            "webSocket" to webSocket.name
        )
    }

    companion object {
        private const val TAG = "WebMediaCapMatrix"

        fun getJsProbeScript(): String {
            return """
                (function() {
                    try {
                        var results = {};
                        function check(exists) {
                            return exists ? "SUPPORTED" : "UNSUPPORTED_BY_WEBVIEW";
                        }
                        results.mediaDevices = check(!!(navigator && navigator.mediaDevices));
                        results.getUserMedia = check(!!(navigator && navigator.mediaDevices && typeof navigator.mediaDevices.getUserMedia === 'function'));
                        results.enumerateDevices = check(!!(navigator && navigator.mediaDevices && typeof navigator.mediaDevices.enumerateDevices === 'function'));
                        results.getDisplayMedia = check(!!(navigator && navigator.mediaDevices && typeof navigator.mediaDevices.getDisplayMedia === 'function'));
                        results.mediaStream = check(typeof window.MediaStream !== 'undefined');
                        results.mediaStreamTrack = check(typeof window.MediaStreamTrack !== 'undefined');
                        results.rtcPeerConnection = check(!!(window.RTCPeerConnection || window.webkitRTCPeerConnection));
                        results.rtcDataChannel = check(typeof window.RTCDataChannel !== 'undefined' || (window.RTCPeerConnection && 'createDataChannel' in window.RTCPeerConnection.prototype));
                        results.rtcRtpSender = check(typeof window.RTCRtpSender !== 'undefined');
                        results.replaceTrack = check(window.RTCRtpSender && 'replaceTrack' in window.RTCRtpSender.prototype);
                        results.mediaRecorder = check(typeof window.MediaRecorder !== 'undefined');
                        results.webGl = check(typeof window.WebGLRenderingContext !== 'undefined');
                        results.webAudio = check(typeof window.AudioContext !== 'undefined' || typeof window.webkitAudioContext !== 'undefined');
                        results.audioContext = check(typeof window.AudioContext !== 'undefined');
                        results.secureContext = check(!!window.isSecureContext);
                        results.deviceEnumeration = check(!!(navigator && navigator.mediaDevices && typeof navigator.mediaDevices.enumerateDevices === 'function'));
                        
                        // Fine-grained WebRTC runtime specifications
                        results.rtcRtpReceiver = check(typeof window.RTCRtpReceiver !== 'undefined');
                        results.rtcRtpTransceiver = check(typeof window.RTCRtpTransceiver !== 'undefined');
                        results.rtcIceCandidate = check(typeof window.RTCIceCandidate !== 'undefined');
                        results.rtcSessionDescription = check(typeof window.RTCSessionDescription !== 'undefined');
                        results.restartIce = check(window.RTCPeerConnection && 'restartIce' in window.RTCPeerConnection.prototype);
                        results.getStats = check(window.RTCPeerConnection && 'getStats' in window.RTCPeerConnection.prototype);
                        results.mediaStreamTrackStop = check(window.MediaStreamTrack && 'stop' in window.MediaStreamTrack.prototype);
                        results.mediaStreamTrackClone = check(window.MediaStreamTrack && 'clone' in window.MediaStreamTrack.prototype);
                        results.mediaStreamTrackGetSettings = check(window.MediaStreamTrack && 'getSettings' in window.MediaStreamTrack.prototype);
                        results.mediaStreamTrackGetCapabilities = check(window.MediaStreamTrack && 'getCapabilities' in window.MediaStreamTrack.prototype);

                        // Web API runtime probes
                        results.serviceWorker = check(!!(navigator && 'serviceWorker' in navigator));
                        results.indexedDb = check(typeof window.indexedDB !== 'undefined' || typeof window.webkitIndexedDB !== 'undefined');
                        results.webSocket = check(typeof window.WebSocket !== 'undefined');

                        var doc = window.document;
                        results.fullscreen = check(!!(doc && (doc.fullscreenEnabled || doc.webkitFullscreenEnabled || doc.mozFullScreenEnabled || doc.msFullscreenEnabled)));
                        
                        var fileInput = document.createElement('input');
                        fileInput.type = 'file';
                        results.fileUpload = check(fileInput.type === 'file');
                        
                        results.clipboard = check(!!(navigator && navigator.clipboard));
                        
                        var tempAudio = document.createElement('audio');
                        results.audioOutputSelection = check(typeof tempAudio.setSinkId === 'function');
                        
                        return JSON.stringify(results);
                    } catch (e) {
                        return JSON.stringify({ error: e.toString() });
                    }
                })();
            """.trimIndent()
        }

        fun build(context: Context, jsResultJson: String?): WebMediaCapabilityMatrix {
            var isJsError = false
            val json = try {
                if (jsResultJson != null && jsResultJson != "null" && jsResultJson.isNotEmpty()) {
                    var clean = jsResultJson
                    if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length >= 2) {
                        clean = org.json.JSONTokener(clean).nextValue().toString()
                    }
                    val parsedObj = JSONObject(clean)
                    if (parsedObj.has("error")) {
                        isJsError = true
                        null
                    } else {
                        parsedObj
                    }
                } else {
                    isJsError = true
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse jsResultJson: ${e.message}")
                isJsError = true
                null
            }

            fun resolve(key: String, defaultValue: WebMediaCapabilityStatus = WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW): WebMediaCapabilityStatus {
                if (isJsError) return WebMediaCapabilityStatus.RUNTIME_ERROR
                val jsStatusStr = json?.optString(key, null) ?: return defaultValue
                return when (jsStatusStr) {
                    "SUPPORTED" -> WebMediaCapabilityStatus.SUPPORTED
                    "PARTIAL" -> WebMediaCapabilityStatus.PARTIAL
                    "RUNTIME_ERROR" -> WebMediaCapabilityStatus.RUNTIME_ERROR
                    else -> WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW
                }
            }

            // Android Hardware checks
            val hasCamera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
            val hasMicrophone = context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
            val hasScreenCaptureSupportedOnAndroid = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP

            // Build individual capabilities blending runtime capabilities
            val secureContextVal = resolve("secureContext")
            val isSecure = secureContextVal == WebMediaCapabilityStatus.SUPPORTED

            val mediaDevicesVal = resolve("mediaDevices")
            val getUserMediaVal = resolve("getUserMedia")
            val enumerateDevicesVal = resolve("enumerateDevices")
            val mediaStreamVal = resolve("mediaStream")
            val mediaStreamTrackVal = resolve("mediaStreamTrack")
            val rtcPeerConnectionVal = resolve("rtcPeerConnection")
            val rtcDataChannelVal = resolve("rtcDataChannel")
            val rtcRtpSenderVal = resolve("rtcRtpSender")
            val replaceTrackVal = resolve("replaceTrack")
            val mediaRecorderVal = resolve("mediaRecorder")
            val getDisplayMediaVal = resolve("getDisplayMedia")
            val webGlVal = resolve("webGl")
            val webAudioVal = resolve("webAudio")
            val audioContextVal = resolve("audioContext")
            val fullscreenVal = resolve("fullscreen")
            val fileUploadVal = resolve("fileUpload")
            val clipboardVal = resolve("clipboard")
            val audioOutputSelectionVal = resolve("audioOutputSelection")
            
            // Resolve fine-grained capabilities
            val rtcRtpReceiverVal = resolve("rtcRtpReceiver")
            val rtcRtpTransceiverVal = resolve("rtcRtpTransceiver")
            val rtcIceCandidateVal = resolve("rtcIceCandidate")
            val rtcSessionDescriptionVal = resolve("rtcSessionDescription")
            val restartIceVal = resolve("restartIce")
            val getStatsVal = resolve("getStats")
            val mediaStreamTrackStopVal = resolve("mediaStreamTrackStop")
            val mediaStreamTrackCloneVal = resolve("mediaStreamTrackClone")
            val mediaStreamTrackGetSettingsVal = resolve("mediaStreamTrackGetSettings")
            val mediaStreamTrackGetCapabilitiesVal = resolve("mediaStreamTrackGetCapabilities")

            val serviceWorkerVal = resolve("serviceWorker")
            val indexedDbVal = resolve("indexedDb")
            val webSocketVal = resolve("webSocket")

            // Smart logic resolving based on OS vs Web restrictions:
            
            // camera resolution
            val cameraStatus = when {
                isJsError -> WebMediaCapabilityStatus.RUNTIME_ERROR
                !hasCamera -> WebMediaCapabilityStatus.UNSUPPORTED_BY_RUNTIME
                getUserMediaVal != WebMediaCapabilityStatus.SUPPORTED -> WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW
                !isSecure -> WebMediaCapabilityStatus.SECURE_CONTEXT_REQUIRED
                !WebMediaCompatibilityEngine.isCameraPermissionGranted(context) -> WebMediaCapabilityStatus.PERMISSION_REQUIRED
                else -> WebMediaCapabilityStatus.REQUIRES_NATIVE_BRIDGE
            }

            // microphone resolution
            val microphoneStatus = when {
                isJsError -> WebMediaCapabilityStatus.RUNTIME_ERROR
                !hasMicrophone -> WebMediaCapabilityStatus.UNSUPPORTED_BY_RUNTIME
                getUserMediaVal != WebMediaCapabilityStatus.SUPPORTED -> WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW
                !isSecure -> WebMediaCapabilityStatus.SECURE_CONTEXT_REQUIRED
                !WebMediaCompatibilityEngine.isMicrophonePermissionGranted(context) -> WebMediaCapabilityStatus.PERMISSION_REQUIRED
                else -> WebMediaCapabilityStatus.REQUIRES_NATIVE_BRIDGE
            }

            // screenCapture / getDisplayMedia resolution
            val screenCaptureStatus = when {
                isJsError -> WebMediaCapabilityStatus.RUNTIME_ERROR
                !hasScreenCaptureSupportedOnAndroid -> WebMediaCapabilityStatus.UNSUPPORTED_BY_ANDROID
                getDisplayMediaVal == WebMediaCapabilityStatus.SUPPORTED -> WebMediaCapabilityStatus.REQUIRES_NATIVE_BRIDGE
                else -> WebMediaCapabilityStatus.REQUIRES_NATIVE_BRIDGE
            }

            // deviceEnumeration
            val deviceEnumStatus = when {
                isJsError -> WebMediaCapabilityStatus.RUNTIME_ERROR
                enumerateDevicesVal == WebMediaCapabilityStatus.SUPPORTED -> WebMediaCapabilityStatus.REQUIRES_NATIVE_BRIDGE
                else -> WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW
            }

            return WebMediaCapabilityMatrix(
                mediaDevices = mediaDevicesVal,
                getUserMedia = getUserMediaVal,
                enumerateDevices = enumerateDevicesVal,
                mediaStream = mediaStreamVal,
                mediaStreamTrack = mediaStreamTrackVal,
                rtcPeerConnection = rtcPeerConnectionVal,
                rtcDataChannel = rtcDataChannelVal,
                rtcRtpSender = rtcRtpSenderVal,
                replaceTrack = replaceTrackVal,
                mediaRecorder = mediaRecorderVal,
                getDisplayMedia = getDisplayMediaVal,
                webGl = webGlVal,
                webAudio = webAudioVal,
                audioContext = audioContextVal,
                secureContext = secureContextVal,
                deviceEnumeration = deviceEnumStatus,
                camera = cameraStatus,
                microphone = microphoneStatus,
                screenCapture = screenCaptureStatus,
                audioOutputSelection = if (audioOutputSelectionVal == WebMediaCapabilityStatus.SUPPORTED) WebMediaCapabilityStatus.SUPPORTED else WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW,
                fullscreen = fullscreenVal,
                fileUpload = fileUploadVal,
                clipboard = if (clipboardVal == WebMediaCapabilityStatus.SUPPORTED) WebMediaCapabilityStatus.SUPPORTED else WebMediaCapabilityStatus.REQUIRES_NATIVE_BRIDGE,
                
                rtcRtpReceiver = rtcRtpReceiverVal,
                rtcRtpTransceiver = rtcRtpTransceiverVal,
                rtcIceCandidate = rtcIceCandidateVal,
                rtcSessionDescription = rtcSessionDescriptionVal,
                restartIce = restartIceVal,
                getStats = getStatsVal,
                mediaStreamTrackStop = mediaStreamTrackStopVal,
                mediaStreamTrackClone = mediaStreamTrackCloneVal,
                mediaStreamTrackGetSettings = mediaStreamTrackGetSettingsVal,
                mediaStreamTrackGetCapabilities = mediaStreamTrackGetCapabilitiesVal,

                serviceWorker = serviceWorkerVal,
                indexedDb = indexedDbVal,
                webSocket = webSocketVal
            )
        }
    }
}
