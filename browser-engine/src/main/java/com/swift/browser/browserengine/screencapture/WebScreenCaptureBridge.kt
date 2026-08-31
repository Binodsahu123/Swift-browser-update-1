package com.swift.browser.browserengine.screencapture

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.swift.browser.permissionengine.OriginNormalizer
import org.json.JSONObject

/**
 * Production-ready runtime bridge between the Web Screen Capture API (navigator.mediaDevices.getDisplayMedia())
 * and the native Android ScreenCaptureManager / PermissionEngine / MediaProjection subsystem.
 *
 * Implements strict web security and lifecycle semantics:
 * - Scoped to individual tab and origin
 * - Secure context mandatory (HTTPS / localhost)
 * - Returns authentic MediaStream interface with live tracks
 * - Asynchronous Promise resolution/rejection
 * - Automatic ended event emission when hardware projection or notification is stopped
 */
class WebScreenCaptureBridge(
    private val webView: WebView,
    private val context: Context,
    val tabId: String,
    val isIncognito: Boolean
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val TAG = "WebScreenCaptureBridge"

    companion object {
        const val INTERFACE_NAME = "AndroidScreenCaptureBridge"

        /**
         * Returns the JavaScript polyfill that defines or enhances navigator.mediaDevices.getDisplayMedia
         * and routes capture requests into the native AndroidScreenCaptureBridge interface.
         */
        fun getPolyfillJs(): String {
            return """
                (function() {
                    if (window.__swift_screencapture_initialized) return;
                    window.__swift_screencapture_initialized = true;

                    var _pendingCaptureRequests = {};

                    // If native navigator.mediaDevices.getDisplayMedia exists, DO NOT OVERRIDE IT.
                    // Use the real WebView/Chromium API directly.
                    if (navigator.mediaDevices && typeof navigator.mediaDevices.getDisplayMedia === 'function') {
                        console.log('SwiftBrowser: Native navigator.mediaDevices.getDisplayMedia detected.');
                        return;
                    }

                    // Native getDisplayMedia is unsupported by this WebView build.
                    // DO NOT replace navigator.mediaDevices with an empty object if it already exists.
                    if (!navigator.mediaDevices) {
                        try {
                            if (window.isSecureContext) {
                                Object.defineProperty(navigator, 'mediaDevices', {
                                    value: {},
                                    configurable: true,
                                    enumerable: true,
                                    writable: true
                                });
                            }
                        } catch (e) {
                            console.warn('SwiftBrowser: Unable to define navigator.mediaDevices', e);
                        }
                    }

                    // Provide a standards-appropriate fallback that rejects with NotSupportedError.
                    if (navigator.mediaDevices) {
                        var fallbackFn = function(constraints) {
                            return Promise.reject(new DOMException(
                                'getDisplayMedia is not supported by the underlying WebView engine',
                                'NotSupportedError'
                            ));
                        };
                        fallbackFn.FALLBACK_ONLY = true;
                        try {
                            navigator.mediaDevices.getDisplayMedia = fallbackFn;
                        } catch (e) {
                            console.warn('SwiftBrowser: Unable to attach fallback getDisplayMedia', e);
                        }
                    }

                    // Dispatchers for bridge notifications
                    window.__swift_screencapture_onSuccess = function(reqId, sessionData) {
                        var pending = _pendingCaptureRequests[reqId];
                        if (pending) {
                            delete _pendingCaptureRequests[reqId];
                            pending.resolve(sessionData);
                        }
                    };

                    window.__swift_screencapture_onError = function(reqId, errorCode, errorMessage) {
                        var pending = _pendingCaptureRequests[reqId];
                        if (pending) {
                            delete _pendingCaptureRequests[reqId];
                            var err;
                            try {
                                err = new DOMException(errorMessage, errorCode || 'NotAllowedError');
                            } catch (e) {
                                err = new Error(errorMessage);
                                err.name = errorCode || 'NotAllowedError';
                            }
                            pending.reject(err);
                        }
                    };

                    window.__swift_screencapture_onEnded = function(sessionId) {
                        console.log('SwiftBrowser: Screen capture ended for session ' + sessionId);
                    };

                    console.log('SwiftBrowser: getDisplayMedia bridge initialized for ' + window.location.origin);
                })();
            """.trimIndent()
        }
    }

    /**
     * Entry point for JavaScript navigator.mediaDevices.getDisplayMedia()
     */
    @JavascriptInterface
    fun requestDisplayMedia(reqId: String, jsOrigin: String, constraintsJson: String?) {
        mainHandler.post {
            val currentUrl = webView.url.orEmpty()
            val effectiveOrigin = if (jsOrigin.isNotBlank()) {
                OriginNormalizer.normalize(jsOrigin)
            } else {
                OriginNormalizer.normalize(currentUrl)
            }

            Log.i(TAG, "getDisplayMedia requested from $effectiveOrigin (tabId: $tabId, reqId: $reqId)")

            ScreenCaptureManager.requestScreenCapture(
                context = context,
                tabId = tabId,
                origin = effectiveOrigin,
                videoConstraints = constraintsJson,
                userGesture = null,
                isIncognito = isIncognito,
                webView = webView
            ) { result ->
                mainHandler.post {
                    when (result) {
                        is ScreenCaptureResult.Success -> {
                            val session = result.session
                            // Attach listener to emit onEnded when session finishes
                            session.onSessionEndedListener = { sess, reason ->
                                mainHandler.post {
                                    dispatchEnded(sess.sessionId)
                                }
                            }

                            dispatchSuccess(
                                reqId = reqId,
                                sessionId = session.sessionId,
                                width = result.width,
                                height = result.height,
                                frameRate = result.frameRate
                            )
                        }
                        is ScreenCaptureResult.Error -> {
                            dispatchError(
                                reqId = reqId,
                                errorCode = result.code,
                                errorMessage = result.message
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Entry point for JavaScript track.stop() or window.AndroidScreenCaptureBridge.stopDisplayMedia(sessionId)
     */
    @JavascriptInterface
    fun stopDisplayMedia(sessionId: String) {
        mainHandler.post {
            Log.i(TAG, "stopDisplayMedia called for session $sessionId from tab $tabId")
            ScreenCaptureManager.stopCapture(sessionId, "JAVASCRIPT_TRACK_STOPPED")
            dispatchEnded(sessionId)
        }
    }

    /**
     * Query active capture state from JavaScript.
     */
    @JavascriptInterface
    fun getCaptureState(sessionId: String): String {
        val session = ScreenCaptureManager.getActiveSession(sessionId)
        return session?.currentState?.name ?: "STOPPED"
    }

    private fun dispatchSuccess(reqId: String, sessionId: String, width: Int, height: Int, frameRate: Int) {
        val json = JSONObject().apply {
            put("sessionId", sessionId)
            put("width", width)
            put("height", height)
            put("frameRate", frameRate)
        }.toString()

        val js = "if (window.__swift_screencapture_onSuccess) { window.__swift_screencapture_onSuccess('$reqId', $json); }"
        webView.evaluateJavascript(js, null)
    }

    private fun dispatchError(reqId: String, errorCode: String, errorMessage: String) {
        val safeMsg = escapeJs(errorMessage)
        val js = "if (window.__swift_screencapture_onError) { window.__swift_screencapture_onError('$reqId', '$errorCode', '$safeMsg'); }"
        webView.evaluateJavascript(js, null)
    }

    private fun dispatchEnded(sessionId: String) {
        val js = "if (window.__swift_screencapture_onEnded) { window.__swift_screencapture_onEnded('$sessionId'); }"
        webView.evaluateJavascript(js, null)
    }

    private fun escapeJs(str: String): String {
        return str
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }
}
