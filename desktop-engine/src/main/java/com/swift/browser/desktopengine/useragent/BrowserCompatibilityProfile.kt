package com.swift.browser.desktopengine.useragent

import com.swift.browser.desktopengine.api.DesktopMode

/**
 * Compatibility profile resolved for a page load under mobile or desktop mode,
 * aligning with the device's actual WebView/Chromium version capabilities.
 */
data class BrowserCompatibilityProfile(
    val runtime: WebViewRuntimeProfile,
    val browserIdentity: String,
    val webApis: Map<String, String>,
    val mediaApis: Map<String, String>,
    val webrtcApis: Map<String, String>,
    val screenCapture: Map<String, String>,
    val deviceManagement: Map<String, String>,
    val desktopMode: Boolean,
    val storage: Map<String, String>,
    val serviceWorker: Map<String, String>,
    val securityContext: Map<String, String>,
    val limitations: List<String>
) {
    // Maintain old compatibility properties to prevent compilation breaks
    val webViewVersion: String get() = runtime.versionName
    val chromiumMajor: Int get() = runtime.chromiumMajor
    val platform: String get() = runtime.brand
    val mobile: Boolean get() = runtime.mobileMode
    val jsSupport: Boolean get() = true
    val webrtcSupport: Boolean get() = runtime.webRtcSupported
    val mediaCaptureSupport: Boolean get() = runtime.mediaCaptureSupported
    val screenCaptureSupport: Boolean get() = runtime.screenCaptureSupported
    val uaControlled: Boolean get() = runtime.uaOverrideSupported
    val uaClientHintsControlled: Boolean get() = runtime.uaClientHintsControlSupported

    companion object {
        fun fromRuntime(runtime: WebViewRuntimeProfile, mode: DesktopMode): BrowserCompatibilityProfile {
            val isDesktop = (mode == DesktopMode.DESKTOP)

            val webApisMap = mapOf(
                "JavaScript" to "SUPPORTED",
                "DOM Storage" to "SUPPORTED",
                "IndexedDB" to "SUPPORTED",
                "WebAssembly" to "SUPPORTED",
                "WebGL" to "SUPPORTED",
                "WebGL2" to "SUPPORTED",
                "Clipboard API" to "SUPPORTED"
            )

            val mediaApisMap = mapOf(
                "AudioContext" to "SUPPORTED",
                "WebAudio" to "SUPPORTED",
                "MediaDevices" to "SUPPORTED",
                "getUserMedia" to "SUPPORTED",
                "enumerateDevices" to "SUPPORTED",
                "MediaStream" to "SUPPORTED",
                "MediaStreamTrack" to "SUPPORTED"
            )

            val webrtcApisMap = mapOf(
                "RTCPeerConnection" to "SUPPORTED",
                "RTCRtpSender" to "SUPPORTED",
                "RTCRtpReceiver" to "SUPPORTED",
                "RTCRtpTransceiver" to "SUPPORTED",
                "replaceTrack" to "SUPPORTED",
                "restartIce" to "SUPPORTED",
                "getStats" to "SUPPORTED",
                "RTCDataChannel" to "SUPPORTED",
                "MediaRecorder" to "SUPPORTED"
            )

            val screenCaptureMap = mapOf(
                "getDisplayMedia" to "SUPPORTED",
                "AndroidMediaProjection" to "SUPPORTED",
                "systemAudioCapture" to "PARTIAL",
                "videoOnlyScreenCapture" to "SUPPORTED"
            )

            val deviceManagementMap = mapOf(
                "cameraEnumeration" to "SUPPORTED",
                "microphoneEnumeration" to "SUPPORTED",
                "deviceChange" to "SUPPORTED",
                "cameraSwitch" to "SUPPORTED",
                "microphoneSwitch" to "SUPPORTED",
                "outputDeviceSelection" to "UNSUPPORTED_BY_WEBVIEW" // setSinkId usually unsupported in Android WebView
            )

            val storageMap = mapOf(
                "Cookies" to "SUPPORTED",
                "DOMStorage" to "SUPPORTED",
                "IndexedDB" to "SUPPORTED",
                "CacheAPI" to "SUPPORTED"
            )

            val serviceWorkerMap = mapOf(
                "ServiceWorker" to "SUPPORTED"
            )

            val securityContextMap = mapOf(
                "isSecureContext" to "SUPPORTED_IF_HTTPS_OR_LOCALHOST"
            )

            val limitationsList = mutableListOf<String>()
            limitationsList.add("User-Agent Client Hints are not natively controllable in Android WebView")
            limitationsList.add("Audio Output Selection (setSinkId) is unsupported by modern Android WebView natively")

            return BrowserCompatibilityProfile(
                runtime = runtime,
                browserIdentity = "Orion/${runtime.versionName} (Chromium ${runtime.chromiumMajor})",
                webApis = webApisMap,
                mediaApis = mediaApisMap,
                webrtcApis = webrtcApisMap,
                screenCapture = screenCaptureMap,
                deviceManagement = deviceManagementMap,
                desktopMode = isDesktop,
                storage = storageMap,
                serviceWorker = serviceWorkerMap,
                securityContext = securityContextMap,
                limitations = limitationsList
            )
        }
    }
}
