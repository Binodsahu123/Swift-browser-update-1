package com.swift.browser.browserengine

import android.content.Context
import com.swift.browser.browserengine.webrtc.GenericWebMediaCompatibilityEngine
import com.swift.browser.browserengine.webrtc.WebMediaCapabilityMatrix
import com.swift.browser.browserengine.webrtc.WebMediaCapabilityStatus
import com.swift.browser.desktopengine.api.DesktopMode
import com.swift.browser.desktopengine.useragent.UserAgentControlStatus
import com.swift.browser.desktopengine.useragent.UserAgentManager
import com.swift.browser.desktopengine.useragent.UserAgentMetadataPolicy
import com.swift.browser.desktopengine.useragent.WebCompatibilityMatrix
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GenericWebViewCompatibilityTest {

    @Test
    fun testUaGenerationDesktopAndMobileModes() {
        val desktopUa = WebCompatibilityMatrix.resolveUserAgent("google.com", DesktopMode.DESKTOP, "128.0.6613.120")
        assertTrue(desktopUa.contains("Windows NT 10.0"))
        assertTrue(desktopUa.contains("Chrome/128.0.6613.120"))
        assertFalse(desktopUa.contains("Mobile"))

        val mobileUa = WebCompatibilityMatrix.resolveUserAgent("google.com", DesktopMode.MOBILE, "128.0.6613.120")
        assertTrue(mobileUa.contains("Linux; Android 10"))
        assertTrue(mobileUa.contains("Chrome/128.0.6613.120"))
        assertTrue(mobileUa.contains("Mobile Safari"))
    }

    @Test
    fun testUaGenerationNoSiteHacks() {
        // Any standards-compliant website must use the same generic compatibility path without domain hacks
        val appleUa = WebCompatibilityMatrix.resolveUserAgent("developer.apple.com", DesktopMode.DESKTOP, "120.0.0.0")
        val youtubeUa = WebCompatibilityMatrix.resolveUserAgent("youtube.com", DesktopMode.DESKTOP, "120.0.0.0")
        val meetUa = WebCompatibilityMatrix.resolveUserAgent("meet.google.com", DesktopMode.DESKTOP, "120.0.0.0")

        assertEquals(appleUa, youtubeUa)
        assertEquals(youtubeUa, meetUa)
        assertTrue(appleUa.contains("Windows NT 10.0"))
        assertTrue(appleUa.contains("Chrome/120.0.0.0"))
    }

    @Test
    fun testOldAndNewWebViewVersions() {
        val oldUa = WebCompatibilityMatrix.resolveUserAgent("example.com", DesktopMode.MOBILE, "90.0.4430.210")
        assertTrue(oldUa.contains("Chrome/90.0.4430.210"))

        val newUa = WebCompatibilityMatrix.resolveUserAgent("example.com", DesktopMode.MOBILE, "130.0.6723.58")
        assertTrue(newUa.contains("Chrome/130.0.6723.58"))
    }

    @Test
    fun testUaChUnavailableStatusReported() {
        val context = RuntimeEnvironment.getApplication()
        val desktopProfile = UserAgentMetadataPolicy.getProfile(DesktopMode.DESKTOP, context)
        assertEquals(UserAgentControlStatus.UA_CONTROLLED, desktopProfile.uaControlStatus)
        assertEquals(UserAgentControlStatus.UA_CH_UNAVAILABLE, desktopProfile.uaChStatus)

        val mobileProfile = UserAgentMetadataPolicy.getProfile(DesktopMode.MOBILE, context)
        assertEquals(UserAgentControlStatus.UA_CONTROLLED, mobileProfile.uaControlStatus)
        assertEquals(UserAgentControlStatus.UA_CH_UNAVAILABLE, mobileProfile.uaChStatus)
    }

    @Test
    fun testJsProbeCapabilityMatrixParsingAllFeatures() {
        val context = RuntimeEnvironment.getApplication()
        val mockJsJson = """
            {
                "mediaDevices": "SUPPORTED",
                "getUserMedia": "SUPPORTED",
                "enumerateDevices": "SUPPORTED",
                "getDisplayMedia": "SUPPORTED",
                "rtcPeerConnection": "SUPPORTED",
                "rtcRtpSender": "SUPPORTED",
                "replaceTrack": "SUPPORTED",
                "restartIce": "SUPPORTED",
                "getStats": "SUPPORTED",
                "mediaRecorder": "SUPPORTED",
                "mediaStream": "SUPPORTED",
                "mediaStreamTrack": "SUPPORTED",
                "webAudio": "SUPPORTED",
                "webGl": "SUPPORTED",
                "serviceWorker": "SUPPORTED",
                "indexedDb": "SUPPORTED",
                "webSocket": "SUPPORTED",
                "secureContext": "SUPPORTED"
            }
        """.trimIndent()

        val matrix = WebMediaCapabilityMatrix.build(context, mockJsJson)

        assertEquals(WebMediaCapabilityStatus.SUPPORTED, matrix.mediaDevices)
        assertEquals(WebMediaCapabilityStatus.SUPPORTED, matrix.getUserMedia)
        assertEquals(WebMediaCapabilityStatus.SUPPORTED, matrix.getDisplayMedia)
        assertEquals(WebMediaCapabilityStatus.SUPPORTED, matrix.rtcPeerConnection)
        assertEquals(WebMediaCapabilityStatus.SUPPORTED, matrix.rtcRtpSender)
        assertEquals(WebMediaCapabilityStatus.SUPPORTED, matrix.replaceTrack)
        assertEquals(WebMediaCapabilityStatus.SUPPORTED, matrix.restartIce)
        assertEquals(WebMediaCapabilityStatus.SUPPORTED, matrix.getStats)
        assertEquals(WebMediaCapabilityStatus.SUPPORTED, matrix.mediaRecorder)
        assertEquals(WebMediaCapabilityStatus.SUPPORTED, matrix.mediaStream)
        assertEquals(WebMediaCapabilityStatus.SUPPORTED, matrix.mediaStreamTrack)
        assertEquals(WebMediaCapabilityStatus.SUPPORTED, matrix.webAudio)
        assertEquals(WebMediaCapabilityStatus.SUPPORTED, matrix.webGl)
        assertEquals(WebMediaCapabilityStatus.SUPPORTED, matrix.serviceWorker)
        assertEquals(WebMediaCapabilityStatus.SUPPORTED, matrix.indexedDb)
        assertEquals(WebMediaCapabilityStatus.SUPPORTED, matrix.webSocket)
    }

    @Test
    fun testMalformedJsResultReturnsRuntimeError() {
        val context = RuntimeEnvironment.getApplication()

        // 1. Invalid JSON string
        val malformedMatrix = WebMediaCapabilityMatrix.build(context, "NOT_VALID_JSON{{{")
        assertEquals(WebMediaCapabilityStatus.RUNTIME_ERROR, malformedMatrix.mediaDevices)
        assertEquals(WebMediaCapabilityStatus.RUNTIME_ERROR, malformedMatrix.getUserMedia)
        assertEquals(WebMediaCapabilityStatus.RUNTIME_ERROR, malformedMatrix.camera)

        // 2. JS Execution error JSON
        val jsErrorMatrix = WebMediaCapabilityMatrix.build(context, """{"error": "ReferenceError: navigator is not defined"}""")
        assertEquals(WebMediaCapabilityStatus.RUNTIME_ERROR, jsErrorMatrix.mediaDevices)
        assertEquals(WebMediaCapabilityStatus.RUNTIME_ERROR, jsErrorMatrix.rtcPeerConnection)
        assertEquals(WebMediaCapabilityStatus.RUNTIME_ERROR, jsErrorMatrix.microphone)
    }
}
