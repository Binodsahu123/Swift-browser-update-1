package com.swift.browser.browserengine

import com.swift.browser.browserengine.webrtc.WebRtcDiagnostics
import com.swift.browser.browserengine.webrtc.WebRtcRuntimeManager
import com.swift.browser.browserengine.webrtc.WebRtcRuntimeSession
import com.swift.browser.browserengine.webrtc.WebRtcSessionState
import com.swift.browser.browserengine.webrtc.WebMediaDeviceManager
import com.swift.browser.browserengine.webrtc.WebRtcRecoveryCoordinator
import com.swift.browser.networkcore.WebRtcNetworkType
import com.swift.browser.networkcore.WebRtcNetworkState
import com.swift.browser.networkstatsengine.WebRtcConnectionDiagnostics
import com.swift.browser.networkstatsengine.TraceRepository
import com.swift.browser.networkstatsengine.RecoveryTraceModel
import com.swift.browser.networkstatsengine.WebRtcTraceModel
import android.content.Context
import android.webkit.WebView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebRtcRuntimeTest {

    @Before
    fun setUp() {
        // Since WebRtcRuntimeManager is a singleton, clean up any tracked sessions between tests
        WebRtcRuntimeManager.onTabClosed("tab_101")
        WebRtcRuntimeManager.onTabClosed("tab_102")
        WebRtcRuntimeManager.onTabClosed("tab_103")
    }

    @Test
    fun testWebRtcSessionStateProperties() {
        val sessionIdle = WebRtcRuntimeSession(
            sessionId = "sess_1",
            tabId = "tab_101",
            origin = "https://example.com",
            topLevelOrigin = "https://example.com",
            connectionState = WebRtcSessionState.IDLE
        )
        assertFalse(sessionIdle.isActive)

        val sessionConnecting = WebRtcRuntimeSession(
            sessionId = "sess_2",
            tabId = "tab_101",
            origin = "https://example.com",
            topLevelOrigin = "https://example.com",
            connectionState = WebRtcSessionState.CONNECTING
        )
        assertTrue(sessionConnecting.isActive)

        val sessionCapturing = WebRtcRuntimeSession(
            sessionId = "sess_3",
            tabId = "tab_101",
            origin = "https://example.com",
            topLevelOrigin = "https://example.com",
            cameraState = "CAPTURING",
            connectionState = WebRtcSessionState.CAPTURING
        )
        assertTrue(sessionCapturing.isActive)
    }

    @Test
    fun testGetOrCreateSessionAndTracking() {
        val session1 = WebRtcRuntimeManager.getOrCreateSession(
            tabId = "tab_101",
            origin = "https://webrtc.org",
            topLevelOrigin = "https://webrtc.org"
        )
        assertNotNull(session1)
        assertEquals("tab_101", session1.tabId)
        assertEquals("https://webrtc.org", session1.origin)

        // Retrieve existing session on second call with same params
        val session2 = WebRtcRuntimeManager.getOrCreateSession(
            tabId = "tab_101",
            origin = "https://webrtc.org",
            topLevelOrigin = "https://webrtc.org"
        )
        assertEquals(session1.sessionId, session2.sessionId)

        // Different origin under same tab should produce a new session
        val sessionDifferentOrigin = WebRtcRuntimeManager.getOrCreateSession(
            tabId = "tab_101",
            origin = "https://meet.google.com",
            topLevelOrigin = "https://meet.google.com"
        )
        assertNotEquals(session1.sessionId, sessionDifferentOrigin.sessionId)
    }

    @Test
    fun testTabClosureCleanup() {
        val s1 = WebRtcRuntimeManager.getOrCreateSession("tab_101", "https://site1.com", "https://site1.com")
        val s2 = WebRtcRuntimeManager.getOrCreateSession("tab_102", "https://site2.com", "https://site2.com")

        // Track both
        assertEquals(1, WebRtcRuntimeManager.getSessionsForTab("tab_101").size)
        assertEquals(1, WebRtcRuntimeManager.getSessionsForTab("tab_102").size)

        // Close tab 101
        WebRtcRuntimeManager.onTabClosed("tab_101")

        // tab_101 sessions should be stopped and cleared
        assertTrue(WebRtcRuntimeManager.getSessionsForTab("tab_101").isEmpty())
        // tab_102 should be unaffected
        assertEquals(1, WebRtcRuntimeManager.getSessionsForTab("tab_102").size)
    }

    @Test
    fun testNavigationOriginIsolation() {
        // Session created under meet.com
        WebRtcRuntimeManager.getOrCreateSession("tab_101", "https://meet.com", "https://meet.com")
        assertEquals(1, WebRtcRuntimeManager.getSessionsForTab("tab_101").size)

        // Navigate to different page on the same origin (should preserve session)
        WebRtcRuntimeManager.onNavigation("tab_101", "https://meet.com/room-abc")
        assertEquals(1, WebRtcRuntimeManager.getSessionsForTab("tab_101").size)

        // Navigate to different origin (should destroy old session)
        WebRtcRuntimeManager.onNavigation("tab_101", "https://news.ycombinator.com")
        assertTrue(WebRtcRuntimeManager.getSessionsForTab("tab_101").isEmpty())
    }

    @Test
    fun testNetworkTransitionFlows() {
        val session = WebRtcRuntimeManager.getOrCreateSession("tab_101", "https://meet.google.com", "https://meet.google.com")
        // Mock connection state to CONNECTED
        WebRtcRuntimeManager.handleConnectionStateChanged("tab_101", "https://meet.google.com", "pc_abc", "connected")

        val connectedSession = WebRtcRuntimeManager.getSessionsForTab("tab_101").first()
        assertEquals(WebRtcSessionState.CONNECTED, connectedSession.connectionState)

        // Network goes down
        WebRtcRuntimeManager.handleNetworkTransition(false)
        val disconnectedSession = WebRtcRuntimeManager.getSessionsForTab("tab_101").first()
        assertEquals(WebRtcSessionState.DISCONNECTED, disconnectedSession.connectionState)
        assertNotNull(disconnectedSession.lastError)

        // Network recovers
        WebRtcRuntimeManager.handleNetworkTransition(true)
        val reconnectingSession = WebRtcRuntimeManager.getSessionsForTab("tab_101").first()
        assertEquals(WebRtcSessionState.RECONNECTING, reconnectingSession.connectionState)
        assertNull(reconnectingSession.lastError)
    }

    @Test
    fun testDiagnosticsAndRecommendations() {
        val session = WebRtcRuntimeSession(
            sessionId = "sess_diagnostics",
            tabId = "tab_101",
            origin = "https://meet.google.com",
            topLevelOrigin = "https://meet.google.com",
            connectionState = WebRtcSessionState.FAILED,
            lastError = "Connection timeout"
        )

        val summary = WebRtcDiagnostics.getDiagnosticSummary(session)
        assertTrue(summary.contains("sess_diagnostics") || summary.contains("tab_101"))
        assertTrue(summary.contains("FAILED"))
        assertTrue(summary.contains("Connection timeout"))

        val recommendation = WebRtcDiagnostics.getRecommendation(session)
        assertTrue(recommendation.contains("reload") || recommendation.contains("firewall") || recommendation.contains("network"))
    }

    @Test
    fun testPolyfillJsContent() {
        val polyfill = WebRtcRuntimeManager.getPolyfillJs("tab_101")
        assertTrue(polyfill.contains("AndroidWebRtcBridge"))
        assertTrue(polyfill.contains("navigator.mediaDevices.getUserMedia"))
        assertTrue(polyfill.contains("RTCPeerConnection"))
        assertTrue(polyfill.contains("onGetUserMediaRequested"))
        assertTrue(polyfill.contains("onGetUserMediaSuccess"))
        assertTrue(polyfill.contains("onGetUserMediaFailure"))
        assertTrue(polyfill.contains("onTrackAdded"))
        assertTrue(polyfill.contains("onPeerConnectionCreated"))
        assertTrue(polyfill.contains("connectionstatechange"))
        assertTrue(polyfill.contains("AndroidWebRtcBridge_switchDevice"))
        assertTrue(polyfill.contains("enumerateDevices"))
    }

    @Test
    fun testDeviceEnumerationAndSelection() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        WebMediaDeviceManager.initialize(context)

        // Enumerate video devices
        val videoDevices = WebMediaDeviceManager.enumerateVideoDevices(context)
        assertNotNull(videoDevices)

        // Enumerate audio devices (returns physical devices only, no fake default_mic)
        val audioDevices = WebMediaDeviceManager.enumerateAudioDevices(context)
        assertNotNull(audioDevices)

        // Test selected device memory
        val tabId = "tab_test_devices"
        WebMediaDeviceManager.setSelectedDeviceId(tabId, "videoinput", "camera_0")
        WebMediaDeviceManager.setSelectedDeviceId(tabId, "audioinput", "mic_1")

        assertEquals("camera_0", WebMediaDeviceManager.getSelectedDeviceId(tabId, "videoinput"))
        assertEquals("mic_1", WebMediaDeviceManager.getSelectedDeviceId(tabId, "audioinput"))

        // JSON format check
        val jsonStr = WebMediaDeviceManager.getDevicesJson(context)
        assertTrue(jsonStr.startsWith("["))
        assertTrue(jsonStr.endsWith("]"))
    }

    @Test
    fun testDeviceChangeEvents() {
        var notified = false
        val listenerId = "test_listener_id"
        
        WebMediaDeviceManager.addDeviceChangeListener(listenerId) {
            notified = true
        }

        val context = org.robolectric.RuntimeEnvironment.getApplication()
        WebMediaDeviceManager.initialize(context)
        
        val tabId = "tab_disconnect_test"
        WebMediaDeviceManager.setSelectedDeviceId(tabId, "audioinput", "mic_99")
        
        WebMediaDeviceManager.removeDeviceChangeListener(listenerId)
    }

    @Test
    fun testActiveTrackReplacement() {
        val session = WebRtcRuntimeManager.getOrCreateSession("tab_track_replace", "https://site.com", "https://site.com")
        
        // Mock active media tracks
        WebRtcRuntimeManager.handleTrackAdded("tab_track_replace", "https://site.com", "track_1", "video", "Main Cam", "live", true)
        val activeSession = WebRtcRuntimeManager.getSessionsForTab("tab_track_replace").first()
        assertEquals("live", activeSession.videoTrackState)

        // Replace track callback tracking
        WebRtcRuntimeManager.handleReplaceTrack("tab_track_replace", "https://site.com", "pc_123", "track_1", "track_2")
    }

    @Test
    fun testPermissionDenial() {
        val tabId = "tab_permission_deny"
        val session = WebRtcRuntimeManager.getOrCreateSession(tabId, "https://secure-site.com", "https://secure-site.com")
        
        // Simulate permission engine/user denying the getUserMedia prompt
        WebRtcRuntimeManager.handleGetUserMediaFailure(tabId, "https://secure-site.com", "PermissionDeniedError")
        
        val updatedSession = WebRtcRuntimeManager.getSessionsForTab(tabId).first()
        assertEquals(WebRtcSessionState.FAILED, updatedSession.connectionState)
        assertTrue(updatedSession.lastError?.contains("PermissionDeniedError") == true)
        assertEquals("DISABLED", updatedSession.cameraState)
        assertEquals("DISABLED", updatedSession.microphoneState)
    }

    @Test
    fun testTabDestructionAndUnregistration() {
        val tabId = "tab_destruction_test"
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        WebMediaDeviceManager.initialize(context)

        // Register tab selection
        WebMediaDeviceManager.setSelectedDeviceId(tabId, "videoinput", "camera_toggle")
        assertEquals("camera_toggle", WebMediaDeviceManager.getSelectedDeviceId(tabId, "videoinput"))

        // Destroy the WebRTC session
        WebRtcRuntimeManager.onWebViewDestroyed(tabId)

        // Ensure session is cleaned up
        assertTrue(WebRtcRuntimeManager.getSessionsForTab(tabId).isEmpty())
        
        // Ensure device choice memory is cleared
        assertNull(WebMediaDeviceManager.getSelectedDeviceId(tabId, "videoinput"))
    }

    @Test
    fun testWifiToMobileTransition() {
        val tabId = "tab_wifi_to_mobile"
        val session = WebRtcRuntimeManager.getOrCreateSession(tabId, "https://meet.google.com", "https://meet.google.com")
        WebRtcRuntimeManager.handleConnectionStateChanged(tabId, "https://meet.google.com", "pc_wifi", "connected")

        // Trigger network change to WiFi
        WebRtcRecoveryCoordinator.onNetworkChanged(WebRtcNetworkType.WiFi, WebRtcNetworkState.CONNECTED)
        
        // Transition to mobile (CELLULAR)
        WebRtcRecoveryCoordinator.onNetworkChanged(WebRtcNetworkType.CELLULAR, WebRtcNetworkState.CONNECTED)

        // Verify diagnostic was saved
        val diagnostics = WebRtcConnectionDiagnostics.getLatestDiagnostic(tabId)
        assertNotNull(diagnostics)
        assertEquals("CONNECTED", diagnostics?.connectionState)

        // Verify traces contain network change message
        val traces = TraceRepository.traces.value
        val hasWifiToMobileTrace = traces.any { it.message.contains("Network interface updated to transport: CELLULAR") }
        assertTrue(hasWifiToMobileTrace)
    }

    @Test
    fun testTemporaryNetworkLossAndRecovery() {
        val tabId = "tab_loss_recovery"
        val session = WebRtcRuntimeManager.getOrCreateSession(tabId, "https://meet.google.com", "https://meet.google.com")
        WebRtcRuntimeManager.handleConnectionStateChanged(tabId, "https://meet.google.com", "pc_loss", "connected")

        // 1. Loss: CONNECTED -> DISCONNECTED
        WebRtcRecoveryCoordinator.onNetworkChanged(WebRtcNetworkType.NONE, WebRtcNetworkState.DISCONNECTED)
        val lostSession = WebRtcRuntimeManager.getSessionsForTab(tabId).first()
        assertEquals(WebRtcSessionState.DISCONNECTED, lostSession.connectionState)

        // 2. Recovery: DISCONNECTED -> CONNECTED (WiFi)
        WebRtcRecoveryCoordinator.onNetworkChanged(WebRtcNetworkType.WiFi, WebRtcNetworkState.CONNECTED)
        val recSession = WebRtcRuntimeManager.getSessionsForTab(tabId).first()
        assertEquals(WebRtcSessionState.RECONNECTING, recSession.connectionState)
    }

    @Test
    fun testNoActiveWebRtcSession() {
        // Trigger a transition with no active WebRTC sessions
        WebRtcRuntimeManager.onTabClosed("tab_101")
        WebRtcRuntimeManager.onTabClosed("tab_wifi_to_mobile")
        WebRtcRuntimeManager.onTabClosed("tab_loss_recovery")

        WebRtcRecoveryCoordinator.onNetworkChanged(WebRtcNetworkType.WiFi, WebRtcNetworkState.CONNECTED)
        
        // Traces should still register the event, but no session specific diagnostic is stored
        val traces = TraceRepository.traces.value
        val hasNetworkTrace = traces.any { it.message.contains("Network interface updated to transport: WiFi") }
        assertTrue(hasNetworkTrace)
    }

    @Test
    fun testMultipleTabsRecovery() {
        val tab1 = "tab_multi_1"
        val tab2 = "tab_multi_2"
        WebRtcRuntimeManager.getOrCreateSession(tab1, "https://meet.1.com", "https://meet.1.com")
        WebRtcRuntimeManager.getOrCreateSession(tab2, "https://meet.2.com", "https://meet.2.com")

        WebRtcRuntimeManager.handleConnectionStateChanged(tab1, "https://meet.1.com", "pc_multi1", "connected")
        WebRtcRuntimeManager.handleConnectionStateChanged(tab2, "https://meet.2.com", "pc_multi2", "connected")

        // Disconnect
        WebRtcRecoveryCoordinator.onNetworkChanged(WebRtcNetworkType.NONE, WebRtcNetworkState.DISCONNECTED)
        assertEquals(WebRtcSessionState.DISCONNECTED, WebRtcRuntimeManager.getSessionsForTab(tab1).first().connectionState)
        assertEquals(WebRtcSessionState.DISCONNECTED, WebRtcRuntimeManager.getSessionsForTab(tab2).first().connectionState)

        // Recover
        WebRtcRecoveryCoordinator.onNetworkChanged(WebRtcNetworkType.WiFi, WebRtcNetworkState.CONNECTED)
        assertEquals(WebRtcSessionState.RECONNECTING, WebRtcRuntimeManager.getSessionsForTab(tab1).first().connectionState)
        assertEquals(WebRtcSessionState.RECONNECTING, WebRtcRuntimeManager.getSessionsForTab(tab2).first().connectionState)
    }

    @Test
    fun testStatsDiagnosticsLogging() {
        val tabId = "tab_stats_test"
        val origin = "https://meet.google.com"
        WebRtcRuntimeManager.getOrCreateSession(tabId, origin, origin)

        // Mock stats callback update from JavaScript
        WebRtcRuntimeManager.handleStatsUpdated(
            tabId = tabId,
            origin = origin,
            pcId = "pc_stats_123",
            rtt = 42L,
            packetsLost = 5L,
            bytesSent = 2048L,
            bytesReceived = 4096L,
            candidatePairState = "succeeded"
        )

        // Retrieve recorded diagnostics
        val diagnostics = WebRtcConnectionDiagnostics.getLatestDiagnostic(tabId)
        assertNotNull(diagnostics)
        assertEquals(42L, diagnostics?.rtt)
        assertEquals(5L, diagnostics?.packetLoss)
        assertEquals(2048L, diagnostics?.bytesSent)
        assertEquals(4096L, diagnostics?.bytesReceived)
        assertEquals("succeeded", diagnostics?.candidatePairState)
    }

    @Test
    fun testWebDeviceIdentityManagerIsolation() {
        val tab1 = "tab_1"
        val tab2 = "tab_2"
        val originA = "https://origin-a.com"
        val originB = "https://origin-b.com"
        val physicalCameraId = "camera_back_0"

        // Generates different opaque ID for different origins under same tab
        val idTab1OriginA = com.swift.browser.browserengine.webrtc.WebDeviceIdentityManager.getOpaqueId(tab1, originA, physicalCameraId)
        val idTab1OriginB = com.swift.browser.browserengine.webrtc.WebDeviceIdentityManager.getOpaqueId(tab1, originB, physicalCameraId)
        assertNotEquals(idTab1OriginA, idTab1OriginB)

        // Generates different opaque ID for same origin under different tabs
        val idTab2OriginA = com.swift.browser.browserengine.webrtc.WebDeviceIdentityManager.getOpaqueId(tab2, originA, physicalCameraId)
        assertNotEquals(idTab1OriginA, idTab2OriginA)

        // Resolves correctly back to the physical device ID
        assertEquals(physicalCameraId, com.swift.browser.browserengine.webrtc.WebDeviceIdentityManager.getPhysicalId(tab1, originA, idTab1OriginA))
        assertEquals(physicalCameraId, com.swift.browser.browserengine.webrtc.WebDeviceIdentityManager.getPhysicalId(tab1, originB, idTab1OriginB))
        assertEquals(physicalCameraId, com.swift.browser.browserengine.webrtc.WebDeviceIdentityManager.getPhysicalId(tab2, originA, idTab2OriginA))

        // Invalidation removes the physical device's mappings
        com.swift.browser.browserengine.webrtc.WebDeviceIdentityManager.invalidatePhysicalDevice(physicalCameraId)
        assertNull(com.swift.browser.browserengine.webrtc.WebDeviceIdentityManager.getPhysicalId(tab1, originA, idTab1OriginA))
    }

    @Test
    fun testSwitchConcurrencyAndStaleSuppression() {
        val tabId = "tab_concurrency_test"
        val kind = "videoinput"
        val op1 = "operation_1"
        val op2 = "operation_2"

        // WebMediaSourceManager state initialization
        val metrics = com.swift.browser.browserengine.webrtc.WebMediaSourceManager.getSourceMetrics(tabId, com.swift.browser.browserengine.webrtc.WebMediaSourceType.CAMERA)
        metrics.captureState = com.swift.browser.browserengine.webrtc.CaptureState.CAPTURING
        metrics.selectedDevice = "camera_old"

        var callback1Called = false
        var callback1Success = false
        var callback2Called = false
        var callback2Success = false

        // Start Operation 1
        com.swift.browser.browserengine.webrtc.WebMediaSourceManager.handleDeviceSwitchSuccess(tabId, kind, "camera_new_1", op1)
        // Set up active operations using internal reflection/calls
        // Simulate completion of Operation 1 after Operation 2 has already superseded it
        // We will directly trigger handleDeviceSwitchSuccess callbacks
        
        com.swift.browser.browserengine.webrtc.WebMediaSourceManager.switchCamera(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            webView = WebView(org.robolectric.RuntimeEnvironment.getApplication()),
            tabId = tabId,
            targetCameraId = "camera_target_1",
            callback = { success, _ ->
                callback1Called = true
                callback1Success = success
            }
        )

        // Switch 2 is requested immediately, superseding Switch 1
        com.swift.browser.browserengine.webrtc.WebMediaSourceManager.switchCamera(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            webView = WebView(org.robolectric.RuntimeEnvironment.getApplication()),
            tabId = tabId,
            targetCameraId = "camera_target_2",
            callback = { success, _ ->
                callback2Called = true
                callback2Success = success
            }
        )

        // Try to trigger success for outdated Switch 1. It must be ignored!
        com.swift.browser.browserengine.webrtc.WebMediaSourceManager.handleDeviceSwitchSuccess(tabId, kind, "camera_target_1", op1)
        assertFalse(callback1Called) // Outdated callback ignored

        // Check selection was not prematurely mutated to camera_target_1
        assertNotEquals("camera_target_1", metrics.selectedDevice)
    }
}
