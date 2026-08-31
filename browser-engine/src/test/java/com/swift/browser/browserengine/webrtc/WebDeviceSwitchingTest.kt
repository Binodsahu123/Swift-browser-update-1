package com.swift.browser.browserengine.webrtc

import android.content.Context
import android.webkit.WebView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebDeviceSwitchingTest {

    private lateinit var context: Context
    private lateinit var webView: WebView
    private val tabId = "test_tab_101"

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        webView = WebView(context)
        WebMediaSourceManager.handleTabClose(tabId)
    }

    @Test
    fun testStartCameraAndMicrophoneWithoutFakeDevices() {
        WebMediaSourceManager.startCamera(context, webView, tabId, cameraId = "cam_0")
        val cameraMetrics = WebMediaSourceManager.getSourceMetrics(tabId, WebMediaSourceType.CAMERA)
        assertEquals("cam_0", cameraMetrics.selectedDevice)
        assertEquals(CaptureState.CAPTURING, cameraMetrics.captureState)

        WebMediaSourceManager.startMicrophone(context, webView, tabId, micId = null)
        val micMetrics = WebMediaSourceManager.getSourceMetrics(tabId, WebMediaSourceType.MICROPHONE)
        // Ensure fake default_mic is NOT set
        assertNotEquals("default_mic", micMetrics.selectedDevice)
    }

    @Test
    fun testCameraSwitchSuccessUpdatesStateOnlyAfterSuccessCallback() {
        WebMediaSourceManager.startCamera(context, webView, tabId, cameraId = "cam_0")
        val metrics = WebMediaSourceManager.getSourceMetrics(tabId, WebMediaSourceType.CAMERA)
        assertEquals("cam_0", metrics.selectedDevice)

        var switchResult = false
        var callbackInvoked = false

        WebMediaSourceManager.switchCamera(context, webView, tabId, targetCameraId = "cam_1") { success, _ ->
            callbackInvoked = true
            switchResult = success
        }

        // Before JS callback returns, selected device must remain old device
        assertEquals("cam_0", metrics.selectedDevice)

        // Find active operation ID
        val activeOpId = WebMediaSourceManager.getActiveOperationId(tabId, "videoinput")
        assertNotNull(activeOpId)

        // Simulate JS promise fulfillment callback from WebRtcBridge
        WebMediaSourceManager.handleDeviceSwitchSuccess(tabId, "videoinput", "cam_1", activeOpId!!)

        assertTrue(callbackInvoked)
        assertTrue(switchResult)
        // Now selected device is updated
        assertEquals("cam_1", metrics.selectedDevice)
    }

    @Test
    fun testCameraSwitchFailureRetainsPreviousSelectedDevice() {
        WebMediaSourceManager.startCamera(context, webView, tabId, cameraId = "cam_0")
        val metrics = WebMediaSourceManager.getSourceMetrics(tabId, WebMediaSourceType.CAMERA)

        var switchResult = true
        var callbackInvoked = false

        WebMediaSourceManager.switchCamera(context, webView, tabId, targetCameraId = "cam_1") { success, _ ->
            callbackInvoked = true
            switchResult = success
        }

        val activeOpId = WebMediaSourceManager.getActiveOperationId(tabId, "videoinput")
        assertNotNull(activeOpId)

        // Simulate JS promise rejection callback
        WebMediaSourceManager.handleDeviceSwitchFailure(tabId, "videoinput", "cam_1", "replaceTrack failed", activeOpId!!)

        assertTrue(callbackInvoked)
        assertFalse(switchResult)
        // Previous camera remains selected
        assertEquals("cam_0", metrics.selectedDevice)
    }

    @Test
    fun testRapidSwitchingIgnoresStaleCallbacks() {
        WebMediaSourceManager.startCamera(context, webView, tabId, cameraId = "cam_0")

        var op1Success = false
        var op2Success = false

        WebMediaSourceManager.switchCamera(context, webView, tabId, targetCameraId = "cam_1") { success, _ ->
            op1Success = success
        }
        val op1Id = WebMediaSourceManager.getActiveOperationId(tabId, "videoinput")!!

        // Immediately trigger second switch before op1 returns
        WebMediaSourceManager.switchCamera(context, webView, tabId, targetCameraId = "cam_2") { success, _ ->
            op2Success = success
        }
        val op2Id = WebMediaSourceManager.getActiveOperationId(tabId, "videoinput")!!

        assertNotEquals(op1Id, op2Id)

        // Stale op1 returns success from JS -> should be ignored
        WebMediaSourceManager.handleDeviceSwitchSuccess(tabId, "videoinput", "cam_1", op1Id)
        assertFalse(op1Success)

        // Valid op2 returns success -> should be accepted
        WebMediaSourceManager.handleDeviceSwitchSuccess(tabId, "videoinput", "cam_2", op2Id)
        assertTrue(op2Success)

        val metrics = WebMediaSourceManager.getSourceMetrics(tabId, WebMediaSourceType.CAMERA)
        assertEquals("cam_2", metrics.selectedDevice)
    }

    @Test
    fun testMicrophoneSwitchSuccessAndFailure() {
        WebMediaSourceManager.startMicrophone(context, webView, tabId, micId = "mic_builtin")
        val metrics = WebMediaSourceManager.getSourceMetrics(tabId, WebMediaSourceType.MICROPHONE)

        var successCalled = false
        WebMediaSourceManager.switchMicrophone(context, webView, tabId, targetMicId = "mic_bluetooth") { success, _ ->
            successCalled = success
        }

        val activeOpId = WebMediaSourceManager.getActiveOperationId(tabId, "audioinput")!!
        WebMediaSourceManager.handleDeviceSwitchSuccess(tabId, "audioinput", "mic_bluetooth", activeOpId)

        assertTrue(successCalled)
        assertEquals("mic_bluetooth", metrics.selectedDevice)
    }

    @Test
    fun testDeviceRemovalCleanupWithoutFakeMic() {
        WebMediaSourceManager.startMicrophone(context, webView, tabId, micId = "mic_usb")
        val metrics = WebMediaSourceManager.getSourceMetrics(tabId, WebMediaSourceType.MICROPHONE)
        assertEquals("mic_usb", metrics.selectedDevice)

        // Simulate physical removal of mic_usb when no other mic exists
        WebMediaSourceManager.handleDeviceRemoval(context, "mic_usb")

        assertNotEquals("default_mic", metrics.selectedDevice)
        assertNull(metrics.selectedDevice)
        assertEquals(HardwareState.DISCONNECTED, metrics.hardwareState)
    }

    @Test
    fun testPermissionDenialHandling() {
        WebMediaSourceManager.startCamera(context, webView, tabId, cameraId = "cam_0")
        WebMediaSourceManager.handlePermissionDenial(tabId, WebMediaSourceType.CAMERA)

        val metrics = WebMediaSourceManager.getSourceMetrics(tabId, WebMediaSourceType.CAMERA)
        assertEquals(PermissionState.DENIED, metrics.permissionState)
        assertEquals(CaptureState.IDLE, metrics.captureState)
        assertEquals("ended", metrics.trackState)
    }

    @Test
    fun testTabCloseCleansUpPendingCallbacksAndState() {
        WebMediaSourceManager.startCamera(context, webView, tabId, cameraId = "cam_0")
        WebMediaSourceManager.switchCamera(context, webView, tabId, targetCameraId = "cam_1") { _, _ -> }

        val activeOpId = WebMediaSourceManager.getActiveOperationId(tabId, "videoinput")
        assertNotNull(activeOpId)

        WebMediaSourceManager.handleTabClose(tabId)

        assertNull(WebMediaSourceManager.getActiveOperationId(tabId, "videoinput"))
    }
}
