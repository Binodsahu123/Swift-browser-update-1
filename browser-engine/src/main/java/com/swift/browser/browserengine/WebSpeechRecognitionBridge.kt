package com.swift.browser.browserengine

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.speech.SpeechRecognizer
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.swift.browser.permissionengine.PermissionEngineApi
import com.swift.browser.permissionengine.SpeechRecognitionRequestParams
import com.swift.browser.searchengine.SpeechRecognitionManager
import java.util.UUID

class WebSpeechRecognitionBridge(
    private val webView: WebView,
    private val context: Context,
    val tabId: String,
    val isIncognito: Boolean
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var activeSession: SpeechSession? = null

    enum class SpeechSessionState {
        IDLE,
        REQUESTING_PERMISSION,
        STARTING,
        RECOGNIZING,
        STOPPING,
        ENDED
    }

    data class SpeechSession(
        val sessionId: String,
        val requestId: String,
        val origin: String,
        val topLevelOrigin: String,
        val tabId: String,
        val frameId: String? = null,
        val frameOrigin: String? = null,
        val isIncognito: Boolean,
        val language: String,
        val continuous: Boolean,
        val interimResults: Boolean,
        val timestamp: Long = System.currentTimeMillis(),
        @Volatile var state: SpeechSessionState = SpeechSessionState.IDLE,
        @Volatile var isStarted: Boolean = false,
        @Volatile var isEnded: Boolean = false,
        @Volatile var speechManager: SpeechRecognitionManager? = null
    )

    @JavascriptInterface
    fun isRecognitionAvailable(): Boolean {
        return try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (e: Exception) {
            false
        }
    }

    @JavascriptInterface
    fun startListening(
        sessionId: String,
        lang: String,
        continuous: Boolean,
        interimResults: Boolean,
        jsOrigin: String
    ) {
        startListeningWithFrame(
            sessionId = sessionId,
            lang = lang,
            continuous = continuous,
            interimResults = interimResults,
            jsOrigin = jsOrigin,
            frameId = null,
            frameOrigin = null
        )
    }

    @JavascriptInterface
    fun startListeningWithFrame(
        sessionId: String,
        lang: String,
        continuous: Boolean,
        interimResults: Boolean,
        jsOrigin: String,
        frameId: String?,
        frameOrigin: String?
    ) {
        mainHandler.post {
            handleStartListening(
                sessionId = sessionId.ifBlank { "sess_" + UUID.randomUUID().toString().substring(0, 8) },
                lang = lang.ifBlank { "en-US" },
                continuous = continuous,
                interimResults = interimResults,
                jsOrigin = jsOrigin,
                frameId = frameId,
                frameOrigin = frameOrigin
            )
        }
    }

    @JavascriptInterface
    fun startListening(lang: String) {
        mainHandler.post {
            val currentUrl = webView.url ?: ""
            val generatedSessionId = "sess_legacy_" + UUID.randomUUID().toString().substring(0, 8)
            handleStartListening(
                sessionId = generatedSessionId,
                lang = lang,
                continuous = false,
                interimResults = false,
                jsOrigin = currentUrl,
                frameId = null,
                frameOrigin = null
            )
        }
    }

    @JavascriptInterface
    fun stopListening(sessionId: String, jsOrigin: String) {
        mainHandler.post {
            handleStopOrAbort(sessionId, jsOrigin, isAbort = false)
        }
    }

    @JavascriptInterface
    fun stopListening() {
        mainHandler.post {
            val currentSession = activeSession
            if (currentSession != null) {
                handleStopOrAbort(currentSession.sessionId, currentSession.origin, isAbort = false)
            }
        }
    }

    @JavascriptInterface
    fun abortListening(sessionId: String, jsOrigin: String) {
        mainHandler.post {
            handleStopOrAbort(sessionId, jsOrigin, isAbort = true)
        }
    }

    private fun handleStartListening(
        sessionId: String,
        lang: String,
        continuous: Boolean,
        interimResults: Boolean,
        jsOrigin: String,
        frameId: String?,
        frameOrigin: String?
    ) {
        // Capability Check
        if (!isRecognitionAvailable()) {
            dispatchJsErrorEvent(sessionId, "service-not-allowed", "Speech recognition service is not available on this device")
            dispatchJsEvent(sessionId, "end")
            return
        }

        val currentUrl = webView.url ?: jsOrigin
        val normalizedJsOrigin = normalizeOrigin(jsOrigin)
        val normalizedCurrentOrigin = normalizeOrigin(currentUrl)

        if (normalizedJsOrigin.isNotBlank() && normalizedCurrentOrigin.isNotBlank() &&
            !originsMatch(normalizedJsOrigin, normalizedCurrentOrigin)
        ) {
            dispatchJsErrorEvent(sessionId, "not-allowed", "Cross-origin speech recognition request rejected")
            dispatchJsEvent(sessionId, "end")
            return
        }

        val effectiveOrigin = if (normalizedJsOrigin.isNotBlank()) normalizedJsOrigin else normalizedCurrentOrigin

        // Check if there is an active session
        val previousSession = activeSession
        if (previousSession != null && !previousSession.isEnded) {
            if (previousSession.sessionId == sessionId &&
                (previousSession.state == SpeechSessionState.STARTING || previousSession.state == SpeechSessionState.RECOGNIZING)
            ) {
                dispatchJsErrorEvent(sessionId, "invalid-state", "Recognition is already started")
                return
            }
            abortSession(previousSession, "Session superseded by new recognition request")
        }

        val requestId = "req_speech_" + UUID.randomUUID().toString().substring(0, 8)
        val newSession = SpeechSession(
            sessionId = sessionId,
            requestId = requestId,
            origin = effectiveOrigin,
            topLevelOrigin = currentUrl,
            tabId = tabId,
            frameId = frameId,
            frameOrigin = frameOrigin ?: effectiveOrigin,
            isIncognito = isIncognito,
            language = lang,
            continuous = continuous,
            interimResults = interimResults,
            state = SpeechSessionState.REQUESTING_PERMISSION
        )
        activeSession = newSession

        val params = SpeechRecognitionRequestParams(
            origin = effectiveOrigin,
            pageUrl = currentUrl,
            language = lang,
            continuous = continuous,
            interimResults = interimResults,
            tabId = tabId,
            userGesture = null,
            isIncognito = isIncognito,
            requestId = requestId
        )

        // Route through unified permission engine
        PermissionEngineApi.handleSpeechRecognitionRequest(context, params) { isAllowed ->
            mainHandler.post {
                if (activeSession != newSession || newSession.isEnded) {
                    return@post
                }

                if (!isAllowed) {
                    newSession.state = SpeechSessionState.ENDED
                    newSession.isEnded = true
                    dispatchJsErrorEvent(newSession.sessionId, "not-allowed", "Microphone access denied by permission authority")
                    dispatchJsEvent(newSession.sessionId, "end")
                    if (activeSession == newSession) {
                        activeSession = null
                    }
                    return@post
                }

                newSession.state = SpeechSessionState.STARTING

                val speechMgr = SpeechRecognitionManager(
                    context = context,
                    onReadyForSpeech = {
                        mainHandler.post {
                            if (activeSession == newSession && !newSession.isEnded) {
                                newSession.state = SpeechSessionState.RECOGNIZING
                                newSession.isStarted = true
                                dispatchJsEvent(newSession.sessionId, "start")
                                dispatchJsEvent(newSession.sessionId, "audiostart")
                            }
                        }
                    },
                    onBeginningOfSpeech = {
                        mainHandler.post {
                            if (activeSession == newSession && !newSession.isEnded) {
                                dispatchJsEvent(newSession.sessionId, "soundstart")
                                dispatchJsEvent(newSession.sessionId, "speechstart")
                            }
                        }
                    },
                    onPartial = { partialText ->
                        mainHandler.post {
                            if (activeSession == newSession && !newSession.isEnded) {
                                dispatchJsResultEvent(newSession.sessionId, partialText, confidence = 0.8f, isFinal = false)
                            }
                        }
                    },
                    onResult = { finalText ->
                        mainHandler.post {
                            if (activeSession == newSession && !newSession.isEnded) {
                                dispatchJsResultEvent(newSession.sessionId, finalText, confidence = 0.95f, isFinal = true)
                                dispatchJsEvent(newSession.sessionId, "speechend")
                                dispatchJsEvent(newSession.sessionId, "soundend")
                                dispatchJsEvent(newSession.sessionId, "audioend")
                                dispatchJsEvent(newSession.sessionId, "end")
                                newSession.state = SpeechSessionState.ENDED
                                newSession.isEnded = true
                                if (activeSession == newSession) {
                                    activeSession = null
                                }
                            }
                        }
                    },
                    onEndOfSpeech = {
                        mainHandler.post {
                            if (activeSession == newSession && !newSession.isEnded) {
                                dispatchJsEvent(newSession.sessionId, "speechend")
                                dispatchJsEvent(newSession.sessionId, "soundend")
                                dispatchJsEvent(newSession.sessionId, "audioend")
                            }
                        }
                    },
                    onErrorDetailed = { errorCode, errorMsg ->
                        mainHandler.post {
                            if (activeSession == newSession && !newSession.isEnded) {
                                dispatchJsErrorEvent(newSession.sessionId, errorCode, errorMsg)
                                dispatchJsEvent(newSession.sessionId, "end")
                                newSession.state = SpeechSessionState.ENDED
                                newSession.isEnded = true
                                if (activeSession == newSession) {
                                    activeSession = null
                                }
                            }
                        }
                    }
                )

                newSession.speechManager = speechMgr
                speechMgr.startListening(lang)
            }
        }
    }

    private fun handleStopOrAbort(sessionId: String, jsOrigin: String, isAbort: Boolean) {
        val currentSession = activeSession
        if (currentSession == null || currentSession.sessionId != sessionId) {
            // Reject stale or mismatched session ID
            return
        }

        val normalizedJsOrigin = normalizeOrigin(jsOrigin)
        if (normalizedJsOrigin.isNotBlank() && currentSession.origin.isNotBlank() &&
            !originsMatch(normalizedJsOrigin, currentSession.origin)
        ) {
            // Reject mismatched origin call
            return
        }

        if (isAbort) {
            abortSession(currentSession, "Recognition aborted by page request")
        } else {
            stopSession(currentSession)
        }
    }

    private fun stopSession(session: SpeechSession) {
        if (session.isEnded) return
        session.state = SpeechSessionState.STOPPING
        session.speechManager?.stopListening()
        session.speechManager = null
    }

    private fun abortSession(session: SpeechSession, reason: String) {
        if (session.isEnded) return
        session.state = SpeechSessionState.ENDED
        session.speechManager?.cancel()
        session.speechManager = null
        session.isEnded = true
        dispatchJsErrorEvent(session.sessionId, "aborted", reason)
        dispatchJsEvent(session.sessionId, "end")
        if (activeSession == session) {
            activeSession = null
        }
    }

    fun onPageStarted(newUrl: String) {
        mainHandler.post {
            val currentSession = activeSession ?: return@post
            val newOrigin = normalizeOrigin(newUrl)
            if (newOrigin.isNotBlank() && currentSession.origin.isNotBlank() &&
                !originsMatch(newOrigin, currentSession.origin)
            ) {
                abortSession(currentSession, "Origin changed due to navigation")
            }
        }
    }

    fun onNavigation(newUrl: String) {
        onPageStarted(newUrl)
    }

    fun onTabSwitched() {
        mainHandler.post {
            val currentSession = activeSession ?: return@post
            abortSession(currentSession, "Tab switched")
        }
    }

    fun onTabClosed() {
        mainHandler.post {
            val currentSession = activeSession ?: return@post
            abortSession(currentSession, "Tab closed")
        }
    }

    fun onIncognitoClosed() {
        mainHandler.post {
            val currentSession = activeSession ?: return@post
            if (currentSession.isIncognito) {
                abortSession(currentSession, "Incognito tab session ended")
            }
        }
    }

    fun onPermissionRevoked(revokedOrigin: String) {
        mainHandler.post {
            val currentSession = activeSession ?: return@post
            val normalizedRevoked = normalizeOrigin(revokedOrigin)
            if (normalizedRevoked.isBlank() || originsMatch(currentSession.origin, normalizedRevoked)) {
                abortSession(currentSession, "Microphone permission was revoked")
            }
        }
    }

    fun destroy() {
        mainHandler.post {
            val currentSession = activeSession
            if (currentSession != null && !currentSession.isEnded) {
                abortSession(currentSession, "WebView destroyed")
            }
            activeSession = null
        }
    }

    private fun dispatchJsEvent(sessionId: String, eventType: String) {
        val safeSessionId = escapeJs(sessionId)
        val safeEventType = escapeJs(eventType)
        val js = "if (window.__swiftSpeechEvent) window.__swiftSpeechEvent('$safeSessionId', '$safeEventType', null);"
        webView.evaluateJavascript(js, null)
    }

    private fun dispatchJsResultEvent(
        sessionId: String,
        transcript: String,
        confidence: Float,
        isFinal: Boolean
    ) {
        val safeSessionId = escapeJs(sessionId)
        val safeTranscript = escapeJs(transcript)
        val js = """
            if (window.__swiftSpeechEvent) {
                window.__swiftSpeechEvent('$safeSessionId', 'result', {
                    transcript: '$safeTranscript',
                    confidence: $confidence,
                    isFinal: $isFinal
                });
            }
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun dispatchJsErrorEvent(sessionId: String, errorCode: String, errorMsg: String) {
        val safeSessionId = escapeJs(sessionId)
        val safeErrorCode = escapeJs(errorCode)
        val safeErrorMsg = escapeJs(errorMsg)
        val js = """
            if (window.__swiftSpeechEvent) {
                window.__swiftSpeechEvent('$safeSessionId', 'error', {
                    error: '$safeErrorCode',
                    message: '$safeErrorMsg'
                });
            }
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun normalizeOrigin(rawUrl: String): String {
        if (rawUrl.isBlank()) return ""
        return try {
            val uri = Uri.parse(rawUrl)
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

    private fun originsMatch(origin1: String, origin2: String): Boolean {
        if (origin1.isBlank() || origin2.isBlank()) return true
        return origin1.equals(origin2, ignoreCase = true)
    }

    private fun escapeJs(text: String): String {
        return text.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
    }

    companion object {
        const val INTERFACE_NAME = "SwiftWebSpeechBridge"

        fun getPolyfillJs(): String {
            return """
                (function() {
                    if (!window.SpeechRecognition && !window.webkitSpeechRecognition && window.SwiftWebSpeechBridge) {
                        function SpeechRecognitionAlternative(transcript, confidence) {
                            this.transcript = transcript || '';
                            this.confidence = typeof confidence === 'number' ? confidence : 0.95;
                        }

                        function SpeechRecognitionResult(alternatives, isFinal) {
                            var list = alternatives || [];
                            for (var i = 0; i < list.length; i++) {
                                this[i] = list[i];
                            }
                            this.length = list.length;
                            this.isFinal = !!isFinal;
                        }
                        SpeechRecognitionResult.prototype.item = function(index) {
                            return this[index] || null;
                        };

                        function SpeechRecognitionResultList(resultsList) {
                            var list = resultsList || [];
                            for (var i = 0; i < list.length; i++) {
                                this[i] = list[i];
                            }
                            this.length = list.length;
                        }
                        SpeechRecognitionResultList.prototype.item = function(index) {
                            return this[index] || null;
                        };

                        function SpeechRecognitionEvent(type, initDict) {
                            this.type = type;
                            this.resultIndex = (initDict && typeof initDict.resultIndex === 'number') ? initDict.resultIndex : 0;
                            this.results = (initDict && initDict.results) ? initDict.results : new SpeechRecognitionResultList();
                        }

                        function SpeechRecognitionErrorEvent(type, initDict) {
                            this.type = type;
                            this.error = (initDict && initDict.error) ? initDict.error : 'audio-capture';
                            this.message = (initDict && initDict.message) ? initDict.message : '';
                        }

                        var WebSpeechPolyfill = function() {
                            this.grammars = null;
                            this.lang = 'en-US';
                            this.continuous = false;
                            this.interimResults = false;
                            this.maxAlternatives = 1;

                            this.onstart = null;
                            this.onaudiostart = null;
                            this.onsoundstart = null;
                            this.onspeechstart = null;
                            this.onresult = null;
                            this.onspeechend = null;
                            this.onsoundend = null;
                            this.onaudioend = null;
                            this.onend = null;
                            this.onerror = null;

                            this._sessionId = 'sess_' + Math.random().toString(36).substring(2, 10);
                            this._listeners = {};
                            this._active = false;
                            var self = this;

                            this.addEventListener = function(type, listener) {
                                if (!self._listeners[type]) self._listeners[type] = [];
                                self._listeners[type].push(listener);
                            };

                            this.removeEventListener = function(type, listener) {
                                if (!self._listeners[type]) return;
                                self._listeners[type] = self._listeners[type].filter(function(l) { return l !== listener; });
                            };

                            this._dispatchEvent = function(event) {
                                var handlerName = 'on' + event.type;
                                if (typeof self[handlerName] === 'function') {
                                    try { self[handlerName].call(self, event); } catch(e) {}
                                }
                                var list = self._listeners[event.type];
                                if (list) {
                                    list.forEach(function(l) {
                                        try { l.call(self, event); } catch(e) {}
                                    });
                                }
                            };

                            this.start = function() {
                                if (self._active) {
                                    try {
                                        var errEvt = new SpeechRecognitionErrorEvent('error', { error: 'invalid-state', message: 'Recognition already active' });
                                        self._dispatchEvent(errEvt);
                                    } catch(e) {}
                                    return;
                                }
                                self._active = true;
                                window.__swiftSpeechActiveSession = self;
                                var origin = window.location.origin || window.location.href;
                                window.SwiftWebSpeechBridge.startListening(
                                    self._sessionId,
                                    self.lang || 'en-US',
                                    !!self.continuous,
                                    !!self.interimResults,
                                    origin
                                );
                            };

                            this.stop = function() {
                                if (!self._active) return;
                                var origin = window.location.origin || window.location.href;
                                window.SwiftWebSpeechBridge.stopListening(self._sessionId, origin);
                            };

                            this.abort = function() {
                                if (!self._active) return;
                                var origin = window.location.origin || window.location.href;
                                window.SwiftWebSpeechBridge.abortListening(self._sessionId, origin);
                            };
                        };

                        window.SpeechRecognition = WebSpeechPolyfill;
                        window.webkitSpeechRecognition = WebSpeechPolyfill;

                        window.__swiftSpeechEvent = function(sessionId, eventType, data) {
                            var inst = window.__swiftSpeechActiveSession;
                            if (!inst || inst._sessionId !== sessionId) return;

                            if (eventType === 'start') {
                                inst._dispatchEvent({ type: 'start' });
                            } else if (eventType === 'audiostart') {
                                inst._dispatchEvent({ type: 'audiostart' });
                            } else if (eventType === 'soundstart') {
                                inst._dispatchEvent({ type: 'soundstart' });
                            } else if (eventType === 'speechstart') {
                                inst._dispatchEvent({ type: 'speechstart' });
                            } else if (eventType === 'result') {
                                var isFinal = !!(data && data.isFinal);
                                var transcript = (data && data.transcript) || '';
                                var confidence = (data && typeof data.confidence === 'number') ? data.confidence : 0.95;
                                var alt = new SpeechRecognitionAlternative(transcript, confidence);
                                var res = new SpeechRecognitionResult([alt], isFinal);
                                var resList = new SpeechRecognitionResultList([res]);
                                var evt = new SpeechRecognitionEvent('result', {
                                    resultIndex: 0,
                                    results: resList
                                });
                                inst._dispatchEvent(evt);
                            } else if (eventType === 'speechend') {
                                inst._dispatchEvent({ type: 'speechend' });
                            } else if (eventType === 'soundend') {
                                inst._dispatchEvent({ type: 'soundend' });
                            } else if (eventType === 'audioend') {
                                inst._dispatchEvent({ type: 'audioend' });
                            } else if (eventType === 'end') {
                                inst._active = false;
                                if (window.__swiftSpeechActiveSession === inst) {
                                    window.__swiftSpeechActiveSession = null;
                                }
                                inst._dispatchEvent({ type: 'end' });
                            } else if (eventType === 'error') {
                                var errCode = (data && data.error) || 'audio-capture';
                                var errMsg = (data && data.message) || 'Speech recognition error';
                                var errEvt = new SpeechRecognitionErrorEvent('error', { error: errCode, message: errMsg });
                                inst._dispatchEvent(errEvt);
                            }
                        };
                    }
                })();
            """.trimIndent()
        }
    }
}
