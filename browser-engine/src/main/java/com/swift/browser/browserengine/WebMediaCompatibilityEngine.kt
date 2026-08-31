package com.swift.browser.browserengine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.webkit.URLUtil
import android.webkit.WebView
import androidx.core.content.ContextCompat
import org.json.JSONObject
import org.json.JSONTokener

data class JsMediaCapabilities(
    val isSecureContext: Boolean = false,
    val hasMediaDevices: Boolean = false,
    val hasGetUserMedia: Boolean = false,
    val hasRtcPeerConnection: Boolean = false,
    val hasMediaRecorder: Boolean = false,
    val hasMediaStream: Boolean = false,
    val hasEnumerateDevices: Boolean = false,
    val rawJson: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class MediaCompatibilityDiagnostics(
    val isCameraPresent: Boolean,
    val isMicrophonePresent: Boolean,
    val isCameraPermissionGranted: Boolean,
    val isMicrophonePermissionGranted: Boolean,
    val isWebRtcSupported: Boolean,
    val isGetUserMediaSupported: Boolean,
    val isMediaRecorderSupported: Boolean,
    val isMediaStreamSupported: Boolean,
    val isEnumerateDevicesSupported: Boolean,
    val isMediaDevicesSupported: Boolean,
    val isSecureContext: Boolean,
    val webViewVersion: String,
    val jsCapabilities: JsMediaCapabilities? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toSummaryString(): String {
        return "CameraPresent=$isCameraPresent, MicPresent=$isMicrophonePresent, " +
               "CameraPermGranted=$isCameraPermissionGranted, MicPermGranted=$isMicrophonePermissionGranted, " +
               "WebRTCSupported=$isWebRtcSupported, GetUserMediaSupported=$isGetUserMediaSupported, " +
               "MediaRecorderSupported=$isMediaRecorderSupported, SecureContext=$isSecureContext, " +
               "WebViewVersion=$webViewVersion"
    }
}

object WebMediaCompatibilityEngine {
    private const val TAG = "WebMediaCompatEngine"

    fun isCameraAvailable(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    fun isMicrophoneAvailable(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
    }

    fun isCameraPermissionGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    fun isMicrophonePermissionGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    fun getWebViewVersion(context: Context): String {
        return try {
            com.swift.browser.desktopengine.useragent.WebViewVersionDetector.detect(context).first
        } catch (e: Exception) {
            "Chromium/Unknown"
        }
    }

    fun isSecureContext(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val uri = try { Uri.parse(url) } catch (e: Exception) { null } ?: return false
        val scheme = uri.scheme?.lowercase() ?: ""
        
        if (scheme == "https" || scheme == "wss" || scheme == "file" || scheme == "about" || scheme == "swift") {
            return true
        }
        
        val host = uri.host ?: ""
        if (host.equals("localhost", ignoreCase = true) || host == "[::1]") {
            return true
        }
        
        // Check loopback IPs (127.0.0.0/8 range)
        if (host.matches(Regex("^127\\.\\d+\\.\\d+\\.\\d+$"))) {
            return true
        }
        
        return false
    }

    fun isWebRtcSupported(context: Context): Boolean {
        val (version, major) = try {
            com.swift.browser.desktopengine.useragent.WebViewVersionDetector.detect(context)
        } catch (e: Exception) {
            Pair("", 0)
        }
        val hasWebViewPackage = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WebView.getCurrentWebViewPackage() != null
            } else {
                true
            }
        } catch (e: Exception) {
            true
        }
        return hasWebViewPackage && version.isNotEmpty() && major >= 36
    }

    fun isMediaDevicesSupported(context: Context): Boolean {
        return isWebRtcSupported(context)
    }

    fun isGetUserMediaSupported(context: Context): Boolean {
        return isWebRtcSupported(context)
    }

    fun isRtcPeerConnectionSupported(context: Context): Boolean {
        return isWebRtcSupported(context)
    }

    fun isMediaRecorderSupported(context: Context): Boolean {
        return isWebRtcSupported(context)
    }

    fun isMediaStreamSupported(context: Context): Boolean {
        return isWebRtcSupported(context)
    }

    fun isEnumerateDevicesSupported(context: Context): Boolean {
        return isWebRtcSupported(context)
    }

    fun getCapabilityProbeJs(): String {
        return """
            (function() {
                try {
                    var caps = {
                        isSecureContext: !!window.isSecureContext,
                        hasMediaDevices: !!(navigator && navigator.mediaDevices),
                        hasGetUserMedia: !!(navigator && navigator.mediaDevices && typeof navigator.mediaDevices.getUserMedia === 'function'),
                        hasRtcPeerConnection: !!(window.RTCPeerConnection || window.webkitRTCPeerConnection),
                        hasMediaRecorder: typeof window.MediaRecorder !== 'undefined',
                        hasMediaStream: typeof window.MediaStream !== 'undefined',
                        hasEnumerateDevices: !!(navigator && navigator.mediaDevices && typeof navigator.mediaDevices.enumerateDevices === 'function')
                    };
                    window.__SWIFT_MEDIA_CAPS__ = caps;
                    return JSON.stringify(caps);
                } catch(e) {
                    return JSON.stringify({ error: e.toString() });
                }
            })();
        """.trimIndent()
    }

    fun injectCapabilityProbe(webView: WebView?, onResults: ((JsMediaCapabilities) -> Unit)? = null) {
        if (webView == null) return
        val js = getCapabilityProbeJs()
        webView.evaluateJavascript(js) { resultJson ->
            val parsed = parseJsCapabilities(resultJson)
            if (parsed != null) {
                Log.d(TAG, "JS Media Capabilities Probe Result: $parsed")
                onResults?.invoke(parsed)
            }
        }
    }

    fun parseJsCapabilities(jsonString: String?): JsMediaCapabilities? {
        if (jsonString.isNullOrBlank() || jsonString == "null") return null
        try {
            var unescaped = jsonString
            if (unescaped.startsWith("\"") && unescaped.endsWith("\"") && unescaped.length >= 2) {
                unescaped = JSONTokener(unescaped).nextValue().toString()
            }
            val obj = JSONObject(unescaped)
            if (obj.has("error")) {
                Log.w(TAG, "JS Probe error: ${obj.getString("error")}")
                return null
            }
            return JsMediaCapabilities(
                isSecureContext = obj.optBoolean("isSecureContext", false),
                hasMediaDevices = obj.optBoolean("hasMediaDevices", false),
                hasGetUserMedia = obj.optBoolean("hasGetUserMedia", false),
                hasRtcPeerConnection = obj.optBoolean("hasRtcPeerConnection", false),
                hasMediaRecorder = obj.optBoolean("hasMediaRecorder", false),
                hasMediaStream = obj.optBoolean("hasMediaStream", false),
                hasEnumerateDevices = obj.optBoolean("hasEnumerateDevices", false),
                rawJson = unescaped
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JS capabilities JSON: ${e.message}")
            return null
        }
    }

    fun getMediaCompatibilityDiagnostics(context: Context, url: String? = null, jsCaps: JsMediaCapabilities? = null): MediaCompatibilityDiagnostics {
        val isWebRtcSupportedVal = if (jsCaps != null) {
            jsCaps.hasRtcPeerConnection && jsCaps.hasMediaDevices
        } else {
            isWebRtcSupported(context)
        }
        val isGetUserMediaSupportedVal = if (jsCaps != null) {
            jsCaps.hasGetUserMedia
        } else {
            isGetUserMediaSupported(context)
        }
        val isMediaRecorderSupportedVal = if (jsCaps != null) {
            jsCaps.hasMediaRecorder
        } else {
            isMediaRecorderSupported(context)
        }
        val isMediaStreamSupportedVal = if (jsCaps != null) {
            jsCaps.hasMediaStream
        } else {
            isMediaStreamSupported(context)
        }
        val isEnumerateDevicesSupportedVal = if (jsCaps != null) {
            jsCaps.hasEnumerateDevices
        } else {
            isEnumerateDevicesSupported(context)
        }
        val isMediaDevicesSupportedVal = if (jsCaps != null) {
            jsCaps.hasMediaDevices
        } else {
            isMediaDevicesSupported(context)
        }

        return MediaCompatibilityDiagnostics(
            isCameraPresent = isCameraAvailable(context),
            isMicrophonePresent = isMicrophoneAvailable(context),
            isCameraPermissionGranted = isCameraPermissionGranted(context),
            isMicrophonePermissionGranted = isMicrophonePermissionGranted(context),
            isWebRtcSupported = isWebRtcSupportedVal,
            isGetUserMediaSupported = isGetUserMediaSupportedVal,
            isMediaRecorderSupported = isMediaRecorderSupportedVal,
            isMediaStreamSupported = isMediaStreamSupportedVal,
            isEnumerateDevicesSupported = isEnumerateDevicesSupportedVal,
            isMediaDevicesSupported = isMediaDevicesSupportedVal,
            isSecureContext = isSecureContext(url),
            webViewVersion = getWebViewVersion(context),
            jsCapabilities = jsCaps
        )
    }

    fun logDiagnostics(context: Context, url: String? = null, jsCaps: JsMediaCapabilities? = null): MediaCompatibilityDiagnostics {
        val diag = getMediaCompatibilityDiagnostics(context, url, jsCaps)
        Log.i(TAG, "=== WEB MEDIA COMPATIBILITY DIAGNOSTICS ===")
        Log.i(TAG, "URL: ${url ?: "Unknown"}")
        Log.i(TAG, "Secure Context: ${diag.isSecureContext}")
        Log.i(TAG, "Camera Present: ${diag.isCameraPresent} | Perm Granted: ${diag.isCameraPermissionGranted}")
        Log.i(TAG, "Microphone Present: ${diag.isMicrophonePresent} | Perm Granted: ${diag.isMicrophonePermissionGranted}")
        Log.i(TAG, "WebRTC Supported: ${diag.isWebRtcSupported}")
        Log.i(TAG, "getUserMedia Supported: ${diag.isGetUserMediaSupported}")
        Log.i(TAG, "MediaRecorder Supported: ${diag.isMediaRecorderSupported}")
        Log.i(TAG, "WebView Version: ${diag.webViewVersion}")
        if (diag.jsCapabilities != null) {
            Log.i(TAG, "JS Probe - SecureContext: ${diag.jsCapabilities.isSecureContext}, " +
                       "MediaDevices: ${diag.jsCapabilities.hasMediaDevices}, " +
                       "GetUserMedia: ${diag.jsCapabilities.hasGetUserMedia}, " +
                       "RTCPeerConnection: ${diag.jsCapabilities.hasRtcPeerConnection}, " +
                       "MediaRecorder: ${diag.jsCapabilities.hasMediaRecorder}, " +
                       "MediaStream: ${diag.jsCapabilities.hasMediaStream}, " +
                       "EnumerateDevices: ${diag.jsCapabilities.hasEnumerateDevices}")
        }
        Log.i(TAG, "==========================================")

        DiagnosticCenter.logEvent(
            engineName = "webmedia_compat_engine",
            module = "WebMediaCompatibilityEngine",
            function = "logDiagnostics",
            reason = diag.toSummaryString()
        )

        try {
            com.swift.browser.permissionengine.PermissionDiagnostics.updateEngineState(
                engineId = "webmedia_compat_engine",
                state = "PASS",
                health = 100,
                lastCallback = "logDiagnostics",
                lastSuccess = diag.toSummaryString()
            )
        } catch (e: Exception) {
            // Ignore if PermissionDiagnostics is unavailable
        }

        return diag
    }
}
