package com.swift.browser.browserengine

import android.content.Context
import com.swift.browser.browserengine.webrtc.GenericWebMediaCompatibilityEngine
import com.swift.browser.browserengine.webrtc.WebMediaCapabilityMatrix
import com.swift.browser.browserengine.webrtc.WebMediaCapabilityStatus
import com.swift.browser.browserengine.webrtc.WebMediaRuntimeSession
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GenericWebMediaCompatibilityLayerTest {

    @Test
    fun testWebMediaCapabilityMatrixJsProbeScript() {
        val js = WebMediaCapabilityMatrix.getJsProbeScript()
        assertTrue(js.contains("navigator.mediaDevices"))
        assertTrue(js.contains("RTCPeerConnection"))
        assertTrue(js.contains("AudioContext"))
        assertTrue(js.contains("fullscreen"))
        assertTrue(js.contains("clipboard"))
    }

    @Test
    fun testCapabilityMatrixBuildingWithSupportedJson() {
        val context = RuntimeEnvironment.getApplication()
        val shadowApp = org.robolectric.Shadows.shadowOf(context)
        shadowApp.grantPermissions(android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO)

        val shadowPackageManager = org.robolectric.Shadows.shadowOf(context.packageManager)
        shadowPackageManager.setSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY, true)
        shadowPackageManager.setSystemFeature(android.content.pm.PackageManager.FEATURE_MICROPHONE, true)

        val mockJsJson = """
            {
                "mediaDevices": "SUPPORTED",
                "getUserMedia": "SUPPORTED",
                "enumerateDevices": "SUPPORTED",
                "mediaStream": "SUPPORTED",
                "mediaStreamTrack": "SUPPORTED",
                "rtcPeerConnection": "SUPPORTED",
                "rtcDataChannel": "SUPPORTED",
                "rtcRtpSender": "SUPPORTED",
                "replaceTrack": "SUPPORTED",
                "mediaRecorder": "SUPPORTED",
                "getDisplayMedia": "SUPPORTED",
                "webGl": "SUPPORTED",
                "webAudio": "SUPPORTED",
                "audioContext": "SUPPORTED",
                "secureContext": "SUPPORTED",
                "deviceEnumeration": "SUPPORTED",
                "fullscreen": "SUPPORTED",
                "fileUpload": "SUPPORTED",
                "clipboard": "SUPPORTED",
                "audioOutputSelection": "SUPPORTED"
            }
        """.trimIndent()

        val matrix = WebMediaCapabilityMatrix.build(context, mockJsJson)
        
        assertEquals(WebMediaCapabilityStatus.SUPPORTED, matrix.mediaDevices)
        assertEquals(WebMediaCapabilityStatus.SUPPORTED, matrix.getUserMedia)
        assertEquals(WebMediaCapabilityStatus.SUPPORTED, matrix.webGl)
        assertEquals(WebMediaCapabilityStatus.SUPPORTED, matrix.webAudio)
        assertEquals(WebMediaCapabilityStatus.SUPPORTED, matrix.audioContext)

        // Camera & Mic resolve to REQUIRES_NATIVE_BRIDGE because the hardware is available on the device, Web supports getUserMedia, and isSecureContext is true
        assertEquals(WebMediaCapabilityStatus.REQUIRES_NATIVE_BRIDGE, matrix.camera)
        assertEquals(WebMediaCapabilityStatus.REQUIRES_NATIVE_BRIDGE, matrix.microphone)
        assertEquals(WebMediaCapabilityStatus.REQUIRES_NATIVE_BRIDGE, matrix.deviceEnumeration)
    }

    @Test
    fun testCapabilityMatrixBuildingWithUnsupportedJson() {
        val context = RuntimeEnvironment.getApplication()
        val shadowPackageManager = org.robolectric.Shadows.shadowOf(context.packageManager)
        shadowPackageManager.setSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY, true)
        shadowPackageManager.setSystemFeature(android.content.pm.PackageManager.FEATURE_MICROPHONE, true)

        val mockJsJson = """
            {
                "mediaDevices": "UNSUPPORTED_BY_WEBVIEW",
                "getUserMedia": "UNSUPPORTED_BY_WEBVIEW",
                "secureContext": "UNSUPPORTED_BY_WEBVIEW"
            }
        """.trimIndent()

        val matrix = WebMediaCapabilityMatrix.build(context, mockJsJson)
        assertEquals(WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW, matrix.mediaDevices)
        assertEquals(WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW, matrix.getUserMedia)
        assertEquals(WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW, matrix.camera)
        assertEquals(WebMediaCapabilityStatus.UNSUPPORTED_BY_WEBVIEW, matrix.microphone)
    }

    @Test
    fun testWebMediaRuntimeSessionSerialization() {
        val matrix = WebMediaCapabilityMatrix()
        val session = WebMediaRuntimeSession(
            sessionId = "test_sess_99",
            url = "https://example.com/rtc",
            origin = "https://example.com",
            webViewVersion = "Chromium/120.0.0.0",
            chromiumVersion = "120.0.0.0",
            isDesktopMode = false,
            isSecureContext = true,
            mediaCapabilities = matrix,
            deviceAvailability = mapOf("camera" to true, "microphone" to true)
        )

        val jsonStr = session.toDiagnosticJson()
        val parsed = org.json.JSONObject(jsonStr)
        assertEquals("test_sess_99", parsed.getString("sessionId"))
        assertEquals("https://example.com/rtc", parsed.getString("url"))
        assertEquals("Chromium/120.0.0.0", parsed.getString("webViewVersion"))
        assertNotNull(parsed.getJSONObject("capabilities"))
        assertNotNull(parsed.getJSONObject("deviceAvailability"))
    }
}
