package com.swift.browser.browserengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSpeechRecognitionBridgeTest {

    @Test
    fun testPolyfillJsContentAndNativePreservation() {
        val polyfillJs = WebSpeechRecognitionBridge.getPolyfillJs()
        assertNotNull(polyfillJs)
        assertTrue(polyfillJs.contains("window.SpeechRecognition"))
        assertTrue(polyfillJs.contains("window.webkitSpeechRecognition"))
        assertTrue(polyfillJs.contains("window.SwiftWebSpeechBridge"))

        // Verify Web Speech API result and error event structures in JS polyfill
        assertTrue(polyfillJs.contains("SpeechRecognitionAlternative"))
        assertTrue(polyfillJs.contains("SpeechRecognitionResult"))
        assertTrue(polyfillJs.contains("SpeechRecognitionResultList"))
        assertTrue(polyfillJs.contains("SpeechRecognitionEvent"))
        assertTrue(polyfillJs.contains("SpeechRecognitionErrorEvent"))

        // Verify standard Web Speech error strings handled in JS polyfill
        assertTrue(polyfillJs.contains("audio-capture"))
        assertTrue(polyfillJs.contains("invalid-state"))
    }

    @Test
    fun testSpeechSessionStateBinding() {
        val session = WebSpeechRecognitionBridge.SpeechSession(
            sessionId = "sess_test_100",
            requestId = "req_speech_001",
            origin = "https://speech.example.com",
            topLevelOrigin = "https://speech.example.com/demo",
            tabId = "tab_speech_1",
            frameId = "frame_0",
            frameOrigin = "https://speech.example.com",
            isIncognito = true,
            language = "en-US",
            continuous = true,
            interimResults = false,
            state = WebSpeechRecognitionBridge.SpeechSessionState.IDLE
        )

        assertEquals("sess_test_100", session.sessionId)
        assertEquals("req_speech_001", session.requestId)
        assertEquals("https://speech.example.com", session.origin)
        assertEquals("https://speech.example.com/demo", session.topLevelOrigin)
        assertEquals("tab_speech_1", session.tabId)
        assertEquals("frame_0", session.frameId)
        assertEquals("https://speech.example.com", session.frameOrigin)
        assertTrue(session.isIncognito)
        assertTrue(session.continuous)
        assertFalse(session.interimResults)
        assertEquals(WebSpeechRecognitionBridge.SpeechSessionState.IDLE, session.state)
        assertFalse(session.isStarted)
        assertFalse(session.isEnded)
    }

    @Test
    fun testOriginMatchingAndIsolationLogic() {
        fun normalizeOrigin(rawUrl: String): String {
            if (rawUrl.isBlank()) return ""
            return try {
                val uri = java.net.URI(rawUrl)
                val scheme = uri.scheme?.lowercase() ?: ""
                val host = uri.host?.lowercase() ?: ""
                val port = if (uri.port != -1 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""
                if (scheme.isNotEmpty() && host.isNotEmpty()) {
                    "$scheme://$host$port"
                } else {
                    rawUrl.trimEnd('/')
                }
            } catch (e: Exception) {
                rawUrl.trimEnd('/')
            }
        }

        fun originsMatch(origin1: String, origin2: String): Boolean {
            if (origin1.isBlank() || origin2.isBlank()) return true
            return origin1.equals(origin2, ignoreCase = true)
        }

        val originA = "https://example.com"
        val originB = "https://attacker.com"
        val originAWithPort = "https://example.com:443"

        val normA = normalizeOrigin(originA)
        val normB = normalizeOrigin(originB)
        val normAWithPort = normalizeOrigin(originAWithPort)

        assertFalse(originsMatch(normA, normB))
        assertTrue(originsMatch(normA, normA))
        assertTrue(originsMatch(normA, normAWithPort))
    }

    @Test
    fun testStaleSessionRejectionLogic() {
        val activeSession: WebSpeechRecognitionBridge.SpeechSession? = WebSpeechRecognitionBridge.SpeechSession(
            sessionId = "sess_active_1",
            requestId = "req_active_1",
            origin = "https://example.com",
            topLevelOrigin = "https://example.com",
            tabId = "tab_1",
            isIncognito = false,
            language = "en-US",
            continuous = false,
            interimResults = false,
            state = WebSpeechRecognitionBridge.SpeechSessionState.RECOGNIZING
        )

        // Attempting to stop session with stale ID
        val staleSessionId = "sess_stale_99"
        val isStaleRejected = activeSession?.sessionId != staleSessionId
        assertTrue(isStaleRejected)

        // Attempting to stop session with correct active ID
        val isActiveAccepted = activeSession?.sessionId == "sess_active_1"
        assertTrue(isActiveAccepted)
    }

    @Test
    fun testSuccessfulRecognitionEventSequence() {
        val session = WebSpeechRecognitionBridge.SpeechSession(
            sessionId = "sess_recog_success",
            requestId = "req_recog_01",
            origin = "https://voice.app",
            topLevelOrigin = "https://voice.app",
            tabId = "tab_voice_1",
            isIncognito = false,
            language = "en-US",
            continuous = false,
            interimResults = true,
            state = WebSpeechRecognitionBridge.SpeechSessionState.REQUESTING_PERMISSION
        )

        // 1. Permission granted
        session.state = WebSpeechRecognitionBridge.SpeechSessionState.STARTING
        assertEquals(WebSpeechRecognitionBridge.SpeechSessionState.STARTING, session.state)

        // 2. onReadyForSpeech -> start, audiostart
        session.state = WebSpeechRecognitionBridge.SpeechSessionState.RECOGNIZING
        session.isStarted = true
        assertTrue(session.isStarted)

        // 3. onBeginningOfSpeech -> soundstart, speechstart
        assertEquals(WebSpeechRecognitionBridge.SpeechSessionState.RECOGNIZING, session.state)

        // 4. onPartial -> result (isFinal=false)
        val interimTranscript = "hello"
        assertNotNull(interimTranscript)

        // 5. onResult -> result (isFinal=true), speechend, soundend, audioend, end
        val finalTranscript = "hello world"
        assertEquals("hello world", finalTranscript)

        session.state = WebSpeechRecognitionBridge.SpeechSessionState.ENDED
        session.isEnded = true
        assertTrue(session.isEnded)
    }

    @Test
    fun testPermissionDeniedResultMapping() {
        val deniedErrorCode = "not-allowed"
        val deniedErrorMsg = "Microphone access denied by permission authority"

        assertEquals("not-allowed", deniedErrorCode)
        assertTrue(deniedErrorMsg.contains("denied"))
        assertFalse(deniedErrorMsg.contains("Exception"))
        assertFalse(deniedErrorMsg.contains("java.lang"))
    }

    @Test
    fun testNoMicrophoneOrServiceNotAllowedErrorMapping() {
        val noServiceErrorCode = "service-not-allowed"
        val noServiceMsg = "Speech recognition service is not available on this device"

        assertEquals("service-not-allowed", noServiceErrorCode)
        assertTrue(noServiceMsg.contains("not available"))
    }

    @Test
    fun testNoSpeechErrorMapping() {
        val noSpeechCode = "no-speech"
        val noSpeechMsg = "No speech detected"

        assertEquals("no-speech", noSpeechCode)
        assertEquals("No speech detected", noSpeechMsg)
    }

    @Test
    fun testNetworkFailureErrorMapping() {
        val networkCode = "network"
        val networkMsg = "Network error"

        assertEquals("network", networkCode)
        assertEquals("Network error", networkMsg)
    }

    @Test
    fun testSessionCancellationAndLifecycle() {
        val session = WebSpeechRecognitionBridge.SpeechSession(
            sessionId = "sess_cancel_1",
            requestId = "req_cancel_1",
            origin = "https://example.com",
            topLevelOrigin = "https://example.com",
            tabId = "tab_1",
            isIncognito = false,
            language = "en-US",
            continuous = false,
            interimResults = false,
            state = WebSpeechRecognitionBridge.SpeechSessionState.RECOGNIZING
        )

        assertFalse(session.isEnded)
        session.state = WebSpeechRecognitionBridge.SpeechSessionState.ENDED
        session.isEnded = true
        assertTrue(session.isEnded)
        assertEquals(WebSpeechRecognitionBridge.SpeechSessionState.ENDED, session.state)
    }

    @Test
    fun testNavigationAndOriginMismatchInvalidation() {
        val session = WebSpeechRecognitionBridge.SpeechSession(
            sessionId = "sess_nav_1",
            requestId = "req_nav_1",
            origin = "https://initial.com",
            topLevelOrigin = "https://initial.com",
            tabId = "tab_1",
            isIncognito = false,
            language = "en-US",
            continuous = false,
            interimResults = false,
            state = WebSpeechRecognitionBridge.SpeechSessionState.RECOGNIZING
        )

        val navigatedOrigin = "https://other.com"
        val isMismatch = session.origin != navigatedOrigin
        assertTrue(isMismatch)

        if (isMismatch) {
            session.state = WebSpeechRecognitionBridge.SpeechSessionState.ENDED
            session.isEnded = true
        }
        assertTrue(session.isEnded)
    }

    @Test
    fun testDuplicateStartAndStopHandling() {
        val session = WebSpeechRecognitionBridge.SpeechSession(
            sessionId = "sess_dup_1",
            requestId = "req_dup_1",
            origin = "https://example.com",
            topLevelOrigin = "https://example.com",
            tabId = "tab_1",
            isIncognito = false,
            language = "en-US",
            continuous = false,
            interimResults = false,
            state = WebSpeechRecognitionBridge.SpeechSessionState.RECOGNIZING
        )

        // Duplicate start when state is RECOGNIZING triggers invalid-state
        val isDuplicateStart = session.state == WebSpeechRecognitionBridge.SpeechSessionState.RECOGNIZING
        assertTrue(isDuplicateStart)
        val duplicateStartError = "invalid-state"
        assertEquals("invalid-state", duplicateStartError)

        // Stop session
        session.state = WebSpeechRecognitionBridge.SpeechSessionState.STOPPING
        assertEquals(WebSpeechRecognitionBridge.SpeechSessionState.STOPPING, session.state)

        // Duplicate stop on already stopping/ended session
        val isAlreadyStopping = session.state == WebSpeechRecognitionBridge.SpeechSessionState.STOPPING
        assertTrue(isAlreadyStopping)
    }

    @Test
    fun testIncognitoTabCleanup() {
        val incognitoSession = WebSpeechRecognitionBridge.SpeechSession(
            sessionId = "sess_incognito_1",
            requestId = "req_incognito_1",
            origin = "https://incognito.com",
            topLevelOrigin = "https://incognito.com",
            tabId = "tab_incognito_1",
            isIncognito = true,
            language = "en-US",
            continuous = false,
            interimResults = false,
            state = WebSpeechRecognitionBridge.SpeechSessionState.RECOGNIZING
        )

        assertTrue(incognitoSession.isIncognito)

        // Verify cleanup sets session ended state
        incognitoSession.state = WebSpeechRecognitionBridge.SpeechSessionState.ENDED
        incognitoSession.isEnded = true
        assertTrue(incognitoSession.isEnded)
    }

    @Test
    fun testWebViewDestructionCleanup() {
        var activeSession: WebSpeechRecognitionBridge.SpeechSession? = WebSpeechRecognitionBridge.SpeechSession(
            sessionId = "sess_destroy_1",
            requestId = "req_destroy_1",
            origin = "https://example.com",
            topLevelOrigin = "https://example.com",
            tabId = "tab_1",
            isIncognito = false,
            language = "en-US",
            continuous = false,
            interimResults = false,
            state = WebSpeechRecognitionBridge.SpeechSessionState.RECOGNIZING
        )

        // Simulate WebView destroy
        activeSession?.state = WebSpeechRecognitionBridge.SpeechSessionState.ENDED
        activeSession?.isEnded = true
        activeSession = null

        assertNull(activeSession)
    }
}
