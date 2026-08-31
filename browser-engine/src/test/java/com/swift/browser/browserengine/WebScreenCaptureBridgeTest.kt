package com.swift.browser.browserengine

import com.swift.browser.browserengine.screencapture.ScreenCaptureManager
import com.swift.browser.browserengine.screencapture.ScreenCaptureSession
import com.swift.browser.browserengine.screencapture.ScreenCaptureState
import com.swift.browser.browserengine.screencapture.WebScreenCaptureBridge
import org.junit.Assert.*
import org.junit.Test

class WebScreenCaptureBridgeTest {

    @Test
    fun testInterfaceNameConstant() {
        assertEquals("AndroidScreenCaptureBridge", WebScreenCaptureBridge.INTERFACE_NAME)
    }

    @Test
    fun testPolyfillJsContentPreservesNative() {
        val polyfill = WebScreenCaptureBridge.getPolyfillJs()
        assertTrue("Polyfill must check for native navigator.mediaDevices.getDisplayMedia",
            polyfill.contains("navigator.mediaDevices && typeof navigator.mediaDevices.getDisplayMedia === 'function'"))
        assertTrue("Polyfill must not overwrite navigator.mediaDevices if it exists",
            polyfill.contains("DO NOT replace navigator.mediaDevices with an empty object"))
        assertTrue("Polyfill must attach NotSupportedError fallback for unsupported WebViews",
            polyfill.contains("NotSupportedError"))
        assertTrue("Polyfill must label fallback as FALLBACK_ONLY",
            polyfill.contains("FALLBACK_ONLY = true"))
        assertFalse("Polyfill must NOT create fake SwiftScreenMediaStream classes",
            polyfill.contains("function SwiftScreenMediaStream("))
    }

    @Test
    fun testScreenCaptureStateTransitions() {
        val session = ScreenCaptureSession(
            requestId = "req_test_1",
            tabId = "tab_101",
            origin = "https://meet.example.com"
        )

        assertEquals(ScreenCaptureState.IDLE, session.currentState)
        assertFalse(session.currentState.isTerminal)

        assertTrue(session.transitionTo(ScreenCaptureState.REQUESTED))
        assertEquals(ScreenCaptureState.REQUESTED, session.currentState)

        assertTrue(session.transitionTo(ScreenCaptureState.WAITING_PERMISSION))
        assertEquals(ScreenCaptureState.WAITING_PERMISSION, session.currentState)

        assertTrue(session.transitionTo(ScreenCaptureState.WAITING_MEDIA_PROJECTION))
        assertEquals(ScreenCaptureState.WAITING_MEDIA_PROJECTION, session.currentState)

        assertTrue(session.transitionTo(ScreenCaptureState.CAPTURING))
        assertEquals(ScreenCaptureState.CAPTURING, session.currentState)
        assertTrue(session.currentState.isActive)

        session.stop("USER_STOPPED")
        assertEquals(ScreenCaptureState.STOPPED, session.currentState)
        assertTrue(session.currentState.isTerminal)
        assertFalse(session.currentState.isActive)

        // Cannot transition from terminal state back to active
        assertFalse(session.transitionTo(ScreenCaptureState.CAPTURING))
    }

    @Test
    fun testPermissionDenial() {
        val session = ScreenCaptureSession(
            requestId = "req_test_fail",
            tabId = "tab_102",
            origin = "https://conference.example.com"
        )

        session.transitionTo(ScreenCaptureState.REQUESTED)
        session.fail("Consent denied by user", "NotAllowedError")

        assertEquals(ScreenCaptureState.FAILED, session.currentState)
        assertEquals("Consent denied by user", session.failureReason)
        assertEquals("NotAllowedError", session.failureCode)
        assertTrue(session.currentState.isTerminal)
    }

    @Test
    fun testMediaProjectionDenial() {
        val session = ScreenCaptureSession(
            requestId = "req_test_mp_denied",
            tabId = "tab_103",
            origin = "https://screen.example.com"
        )

        session.transitionTo(ScreenCaptureState.WAITING_MEDIA_PROJECTION)
        session.fail("Screen capture consent denied by user.", "NotAllowedError")

        assertEquals(ScreenCaptureState.FAILED, session.currentState)
        assertEquals("NotAllowedError", session.failureCode)
        assertTrue(session.currentState.isTerminal)
    }

    @Test
    fun testScreenCaptureSessionCancel() {
        val session = ScreenCaptureSession(
            requestId = "req_test_cancel",
            tabId = "tab_104",
            origin = "https://screen.example.com"
        )

        session.transitionTo(ScreenCaptureState.REQUESTED)
        session.cancel("User dismissed request")

        assertEquals(ScreenCaptureState.STOPPED, session.currentState)
        assertTrue(session.currentState.isTerminal)
    }

    @Test
    fun testTrackEndedCleanup() {
        val session = ScreenCaptureSession(
            requestId = "req_test_track_ended",
            tabId = "tab_105",
            origin = "https://screen.example.com"
        )

        session.transitionTo(ScreenCaptureState.CAPTURING)
        assertTrue(session.currentState.isActive)

        session.stop("JAVASCRIPT_TRACK_STOPPED")
        assertEquals(ScreenCaptureState.STOPPED, session.currentState)
        assertNull(session.virtualDisplay)
        assertNull(session.imageReader)
        assertNull(session.mediaProjection)
    }

    @Test
    fun testTabCloseAndWebViewDestroyCleanups() {
        val tabId = "tab_lifecycle_test"
        ScreenCaptureManager.stopAll("RESET")

        // Validate tab close cleanup
        ScreenCaptureManager.onTabClosed(tabId)
        assertTrue(ScreenCaptureManager.getActiveSessionsForTab(tabId).isEmpty())

        // Validate WebView destroy cleanup
        ScreenCaptureManager.onWebViewDestroyed(tabId)
        assertTrue(ScreenCaptureManager.getActiveSessionsForTab(tabId).isEmpty())

        // Validate navigation origin cleanup
        ScreenCaptureManager.onNavigation(tabId, "https://different-site.com")
        assertTrue(ScreenCaptureManager.getActiveSessionsForTab(tabId).isEmpty())
    }

    @Test
    fun testRotationDisplayMetricsUpdate() {
        val session = ScreenCaptureSession(
            requestId = "req_rotation_test",
            tabId = "tab_rotation",
            origin = "https://meet.example.com"
        )

        session.transitionTo(ScreenCaptureState.CAPTURING)
        assertEquals(1280, session.width)
        assertEquals(720, session.height)

        session.updateDisplayMetrics(1920, 1080, 420)
        assertEquals(1920, session.width)
        assertEquals(1080, session.height)
        assertEquals(420, session.densityDpi)
    }

    @Test
    fun testDuplicateStopIdempotency() {
        val session = ScreenCaptureSession(
            requestId = "req_dup_stop",
            tabId = "tab_dup_stop",
            origin = "https://example.com"
        )

        session.transitionTo(ScreenCaptureState.CAPTURING)
        session.stop("FIRST_STOP")
        assertEquals(ScreenCaptureState.STOPPED, session.currentState)

        // Second stop should be no-op and remain STOPPED
        session.stop("SECOND_STOP")
        assertEquals(ScreenCaptureState.STOPPED, session.currentState)
    }
}
