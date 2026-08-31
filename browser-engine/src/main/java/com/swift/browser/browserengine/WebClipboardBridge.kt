package com.swift.browser.browserengine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.swift.browser.permissionengine.ClipboardRequestParams
import com.swift.browser.permissionengine.OriginNormalizer
import com.swift.browser.permissionengine.PermissionEngineApi
import org.json.JSONObject

/**
 * Production-ready runtime bridge between the Web Async Clipboard API (navigator.clipboard)
 * and the native PermissionEngine / ClipboardManager.
 *
 * Enforces browser security semantics:
 * - Secure context (HTTPS or localhost) mandatory for clipboard access
 * - Separate evaluation for CLIPBOARD_READ (user prompt) vs CLIPBOARD_WRITE (policy)
 * - Incognito isolation and tab scoping
 * - Asynchronous Promise resolution/rejection in JavaScript
 */
class WebClipboardBridge(
    private val webView: WebView,
    private val context: Context,
    val tabId: String,
    val isIncognito: Boolean
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val TAG = "WebClipboardBridge"

    companion object {
        const val INTERFACE_NAME = "AndroidClipboardBridge"

        /**
         * Returns the JavaScript polyfill that defines navigator.clipboard
         * and routes calls into this AndroidClipboardBridge interface.
         */
        fun getPolyfillJs(): String {
            return """
                (function() {
                    if (!window.AndroidClipboardBridge) return;
                    if (window.__swift_clipboard_initialized) return;
                    window.__swift_clipboard_initialized = true;

                    var _clipboardRequests = {};

                    var swiftClipboard = {
                        readText: function() {
                            return new Promise(function(resolve, reject) {
                                var reqId = 'req_clip_read_' + Math.random().toString(36).substring(2, 10) + '_' + Date.now();
                                _clipboardRequests[reqId] = { resolve: resolve, reject: reject };
                                window.AndroidClipboardBridge.readText(reqId, window.location.origin);
                            });
                        },
                        writeText: function(text) {
                            return new Promise(function(resolve, reject) {
                                var reqId = 'req_clip_write_' + Math.random().toString(36).substring(2, 10) + '_' + Date.now();
                                _clipboardRequests[reqId] = { resolve: resolve, reject: reject };
                                window.AndroidClipboardBridge.writeText(reqId, String(text !== undefined && text !== null ? text : ''), window.location.origin);
                            });
                        },
                        read: function() {
                            return swiftClipboard.readText().then(function(text) {
                                if (typeof Blob === 'undefined') return [];
                                var blob = new Blob([text], { type: 'text/plain' });
                                return [{
                                    getType: function(type) {
                                        return Promise.resolve(blob);
                                    },
                                    types: ['text/plain']
                                }];
                            });
                        },
                        write: function(data) {
                            return swiftClipboard.writeText(String(data || ''));
                        }
                    };

                    window.__swift_clipboard_onReadResponse = function(reqId, text, error) {
                        var pending = _clipboardRequests[reqId];
                        if (pending) {
                            delete _clipboardRequests[reqId];
                            if (error) {
                                var err = new Error(error);
                                err.name = 'NotAllowedError';
                                pending.reject(err);
                            } else {
                                pending.resolve(text || '');
                            }
                        }
                    };

                    window.__swift_clipboard_onWriteResponse = function(reqId, success, error) {
                        var pending = _clipboardRequests[reqId];
                        if (pending) {
                            delete _clipboardRequests[reqId];
                            if (error || !success) {
                                var err = new Error(error || 'Clipboard write permission was denied.');
                                err.name = 'NotAllowedError';
                                pending.reject(err);
                            } else {
                                pending.resolve();
                            }
                        }
                    };

                    try {
                        Object.defineProperty(navigator, 'clipboard', {
                            get: function() { return swiftClipboard; },
                            configurable: true,
                            enumerable: true
                        });
                    } catch (e) {
                        try {
                            navigator.clipboard = swiftClipboard;
                        } catch (ex) {}
                    }
                    console.log('SwiftBrowser: Async Clipboard API successfully initialized for ' + window.location.origin);
                })();
            """.trimIndent()
        }
    }

    /**
     * Entry point for JavaScript navigator.clipboard.readText()
     */
    @JavascriptInterface
    fun readText(reqId: String, jsOrigin: String) {
        mainHandler.post {
            val currentUrl = webView.url.orEmpty()
            val effectiveOrigin = if (jsOrigin.isNotBlank()) {
                OriginNormalizer.normalize(jsOrigin)
            } else {
                OriginNormalizer.normalize(currentUrl)
            }

            // Verify secure origin requirement
            if (!OriginNormalizer.isSecure(effectiveOrigin)) {
                Log.w(TAG, "Rejecting clipboard readText from insecure origin: $effectiveOrigin")
                dispatchReadError(reqId, "NotAllowedError: Clipboard API requires a secure context (HTTPS).")
                return@post
            }

            val params = ClipboardRequestParams(
                origin = effectiveOrigin,
                operation = "READ",
                tabId = tabId,
                userGesture = null,
                isIncognito = isIncognito,
                requestId = reqId
            )

            PermissionEngineApi.evaluateClipboardRequest(context, params) { decision ->
                mainHandler.post {
                    if (decision.isAllowed) {
                        try {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clip = clipboard?.primaryClip
                            val text = if (clip != null && clip.itemCount > 0) {
                                clip.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                            } else {
                                ""
                            }
                            dispatchReadSuccess(reqId, text)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error reading from ClipboardManager", e)
                            dispatchReadError(reqId, "NotAllowedError: Failed to read from clipboard: ${e.message}")
                        }
                    } else {
                        Log.w(TAG, "Clipboard readText denied for $effectiveOrigin, reason: ${decision.reason}")
                        dispatchReadError(reqId, "NotAllowedError: Clipboard read permission was denied.")
                    }
                }
            }
        }
    }

    /**
     * Entry point for JavaScript navigator.clipboard.writeText(text)
     */
    @JavascriptInterface
    fun writeText(reqId: String, text: String, jsOrigin: String) {
        mainHandler.post {
            val currentUrl = webView.url.orEmpty()
            val effectiveOrigin = if (jsOrigin.isNotBlank()) {
                OriginNormalizer.normalize(jsOrigin)
            } else {
                OriginNormalizer.normalize(currentUrl)
            }

            // Verify secure origin requirement
            if (!OriginNormalizer.isSecure(effectiveOrigin)) {
                Log.w(TAG, "Rejecting clipboard writeText from insecure origin: $effectiveOrigin")
                dispatchWriteError(reqId, "NotAllowedError: Clipboard API requires a secure context (HTTPS).")
                return@post
            }

            val params = ClipboardRequestParams(
                origin = effectiveOrigin,
                operation = "WRITE",
                tabId = tabId,
                userGesture = null,
                isIncognito = isIncognito,
                requestId = reqId
            )

            PermissionEngineApi.evaluateClipboardRequest(context, params) { decision ->
                mainHandler.post {
                    if (decision.isAllowed) {
                        try {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clip = ClipData.newPlainText("web_copy", text)
                            clipboard?.setPrimaryClip(clip)
                            dispatchWriteSuccess(reqId)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error writing to ClipboardManager", e)
                            dispatchWriteError(reqId, "NotAllowedError: Failed to write to clipboard: ${e.message}")
                        }
                    } else {
                        Log.w(TAG, "Clipboard writeText denied for $effectiveOrigin, reason: ${decision.reason}")
                        dispatchWriteError(reqId, "NotAllowedError: Clipboard write permission was denied.")
                    }
                }
            }
        }
    }

    private fun dispatchReadSuccess(reqId: String, text: String) {
        val quoted = JSONObject.quote(text)
        val js = "if (window.__swift_clipboard_onReadResponse) window.__swift_clipboard_onReadResponse('${escapeJs(reqId)}', $quoted, null);"
        webView.evaluateJavascript(js, null)
    }

    private fun dispatchReadError(reqId: String, errorMsg: String) {
        val quoted = JSONObject.quote(errorMsg)
        val js = "if (window.__swift_clipboard_onReadResponse) window.__swift_clipboard_onReadResponse('${escapeJs(reqId)}', null, $quoted);"
        webView.evaluateJavascript(js, null)
    }

    private fun dispatchWriteSuccess(reqId: String) {
        val js = "if (window.__swift_clipboard_onWriteResponse) window.__swift_clipboard_onWriteResponse('${escapeJs(reqId)}', true, null);"
        webView.evaluateJavascript(js, null)
    }

    private fun dispatchWriteError(reqId: String, errorMsg: String) {
        val quoted = JSONObject.quote(errorMsg)
        val js = "if (window.__swift_clipboard_onWriteResponse) window.__swift_clipboard_onWriteResponse('${escapeJs(reqId)}', false, $quoted);"
        webView.evaluateJavascript(js, null)
    }

    private fun escapeJs(str: String): String {
        return str.replace("\\", "\\\\").replace("'", "\\'")
    }
}
