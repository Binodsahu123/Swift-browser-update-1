package com.swift.browser.browserengine.webrtc

import android.content.Context
import android.webkit.WebView
import com.swift.browser.browserengine.WebMediaCompatibilityEngine
import java.util.UUID

object GenericWebMediaCompatibilityEngine {
    private val activeSessions = java.util.concurrent.ConcurrentHashMap<String, WebMediaRuntimeSession>()
    private val compatibilitySessions = java.util.concurrent.ConcurrentHashMap<String, WebCompatibilitySession>()

    /**
     * Checks if a physical camera is available on the device.
     */
    fun isCameraAvailable(context: Context): Boolean {
        return WebMediaCompatibilityEngine.isCameraAvailable(context)
    }

    /**
     * Checks if a physical microphone is available on the device.
     */
    fun isMicrophoneAvailable(context: Context): Boolean {
        return WebMediaCompatibilityEngine.isMicrophoneAvailable(context)
    }

    /**
     * Invalidates compatibility session cache for a given tab.
     * This is invoked when the WebView is recreated, desktop mode changes, or origin is navigated.
     */
    fun invalidateSession(tabId: String) {
        activeSessions.remove(tabId)
        compatibilitySessions.remove(tabId)
    }

    /**
     * Retrieves the cached compatibility session for a specific tab.
     */
    fun getCompatibilitySession(tabId: String): WebCompatibilitySession? {
        return compatibilitySessions[tabId]
    }

    /**
     * Executes the capability probe on the given WebView and builds a comprehensive WebMediaRuntimeSession and WebCompatibilitySession.
     */
    fun runCompatibilityDiagnostics(
        webView: WebView,
        tabId: String,
        isDesktopMode: Boolean,
        onDiagnosticsComplete: (WebMediaRuntimeSession) -> Unit
    ) {
        runCompatibilityDiagnosticsEx(webView, tabId, isDesktopMode) { _, runtimeSession ->
            onDiagnosticsComplete(runtimeSession)
        }
    }

    fun runCompatibilityDiagnosticsEx(
        webView: WebView,
        tabId: String,
        isDesktopMode: Boolean,
        onDiagnosticsComplete: (WebCompatibilitySession, WebMediaRuntimeSession) -> Unit
    ) {
        val context = webView.context
        val url = webView.url ?: ""
        val origin = com.swift.browser.permissionengine.OriginNormalizer.normalize(url)
        val isSecure = WebMediaCompatibilityEngine.isSecureContext(url)
        
        // Extract versions
        val webViewVer = WebMediaCompatibilityEngine.getWebViewVersion(context)
        val chromiumVer = webViewVer.replace("Chromium/", "")

        val deviceAvailability = mapOf(
            "camera" to isCameraAvailable(context),
            "microphone" to isMicrophoneAvailable(context),
            "screen_share" to (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP)
        )

        webView.evaluateJavascript(WebMediaCapabilityMatrix.getJsProbeScript()) { result ->
            val matrix = WebMediaCapabilityMatrix.build(context, result)
            
            // Map the media capability status ("FULL", "PARTIAL", "LIMITED", "UNSUPPORTED")
            val hasWebRtc = matrix.rtcPeerConnection == WebMediaCapabilityStatus.SUPPORTED
            val hasCapture = matrix.getUserMedia == WebMediaCapabilityStatus.SUPPORTED
            val hasDisplay = matrix.getDisplayMedia == WebMediaCapabilityStatus.SUPPORTED || matrix.screenCapture == WebMediaCapabilityStatus.REQUIRES_NATIVE_BRIDGE
            
            val mediaCap = when {
                hasWebRtc && hasCapture && hasDisplay -> "FULL"
                hasWebRtc && hasCapture -> "PARTIAL"
                hasWebRtc -> "LIMITED"
                else -> "UNSUPPORTED"
            }

            val runtimeSession = WebMediaRuntimeSession(
                sessionId = "web_media_compat_${UUID.randomUUID().toString().substring(0, 8)}",
                url = url,
                origin = origin,
                webViewVersion = webViewVer,
                chromiumVersion = chromiumVer,
                isDesktopMode = isDesktopMode,
                isSecureContext = isSecure,
                mediaCapabilities = matrix,
                deviceAvailability = deviceAvailability
            )

            val compSession = WebCompatibilitySession(
                tabId = tabId,
                sessionId = runtimeSession.sessionId,
                origin = origin,
                webViewVersion = webViewVer,
                desktopMode = isDesktopMode,
                featureMatrix = matrix,
                secureContext = isSecure,
                mediaCapability = mediaCap,
                lastNavigation = url,
                lastCompatibilityProbeTime = System.currentTimeMillis()
            )
            
            // Record locally
            activeSessions[tabId] = runtimeSession
            compatibilitySessions[tabId] = compSession
            onDiagnosticsComplete(compSession, runtimeSession)
        }
    }

    /**
     * Retrieves the recorded diagnostics for a specific tab.
     */
    fun getSessionForTab(tabId: String): WebMediaRuntimeSession? {
        return activeSessions[tabId]
    }

    /**
     * Retrieves all recorded diagnostic sessions.
     */
    fun getAllSessions(): List<WebMediaRuntimeSession> {
        return activeSessions.values.toList()
    }

    /**
     * Retrieves all cached compatibility sessions.
     */
    fun getCompatibilitySessions(): List<WebCompatibilitySession> {
        return compatibilitySessions.values.toList()
    }

    /**
     * Clears all session data.
     */
    fun clear() {
        activeSessions.clear()
        compatibilitySessions.clear()
    }
}
