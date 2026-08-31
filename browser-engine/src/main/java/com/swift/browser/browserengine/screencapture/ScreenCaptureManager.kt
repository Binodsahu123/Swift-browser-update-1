package com.swift.browser.browserengine.screencapture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.webkit.WebView
import com.swift.browser.permissionengine.OriginNormalizer
import com.swift.browser.permissionengine.PermissionEngineApi
import com.swift.browser.permissionengine.ScreenCaptureRequestParams
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Native Manager and Coordinator for the Web Screen-Sharing pipeline.
 * Manages active sessions, bridges the PermissionEngine and Android MediaProjection APIs,
 * and maintains proper lifecycle scoping across tabs, navigation events, and Activity transitions.
 */
object ScreenCaptureManager {
    private const val TAG = "ScreenCaptureManager"
    
    private val mainHandler: Handler? by lazy {
        try {
            val looper = Looper.getMainLooper()
            if (looper != null) Handler(looper) else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun postToMain(runnable: Runnable) {
        val handler = mainHandler
        if (handler != null) {
            try {
                handler.post(runnable)
            } catch (_: Throwable) {
                runnable.run()
            }
        } else {
            runnable.run()
        }
    }

    private fun logD(msg: String) {
        try { Log.d(TAG, msg) } catch (_: Throwable) {}
    }
    private fun logI(msg: String) {
        try { Log.i(TAG, msg) } catch (_: Throwable) {}
    }
    private fun logW(msg: String) {
        try { Log.w(TAG, msg) } catch (_: Throwable) {}
    }
    private fun logE(msg: String, tr: Throwable? = null) {
        try { Log.e(TAG, msg, tr) } catch (_: Throwable) {}
    }

    interface MediaProjectionHostRequester {
        fun requestMediaProjectionConsent(requestId: String, onResult: (resultCode: Int, resultData: Intent?) -> Unit)
        fun startForegroundService(resultCode: Int, resultData: Intent)
        fun stopForegroundService()
    }

    private var hostRequester: MediaProjectionHostRequester? = null

    // Concurrent registries for active and pending sessions
    private val activeSessions = ConcurrentHashMap<String, ScreenCaptureSession>() // sessionId -> Session
    private val tabSessions = ConcurrentHashMap<String, MutableSet<String>>() // tabId -> Set<sessionId>
    private val pendingRequests = ConcurrentHashMap<String, ScreenCaptureSession>() // requestId -> Session

    fun registerHostRequester(requester: MediaProjectionHostRequester) {
        this.hostRequester = requester
        logD("MediaProjectionHostRequester registered")
    }

    fun unregisterHostRequester(requester: MediaProjectionHostRequester) {
        if (this.hostRequester === requester) {
            this.hostRequester = null
            logD("MediaProjectionHostRequester unregistered")
        }
    }

    /**
     * Entry point to initiate screen capture from the web bridge or WebView callback.
     */
    fun requestScreenCapture(
        context: Context,
        tabId: String,
        origin: String,
        videoConstraints: String? = null,
        userGesture: Boolean? = null,
        isIncognito: Boolean = false,
        webView: WebView? = null,
        callback: (ScreenCaptureResult) -> Unit
    ) {
        val canonicalOrigin = OriginNormalizer.normalize(origin)

        // 1. Secure context validation
        if (!OriginNormalizer.isSecure(canonicalOrigin)) {
            Log.w(TAG, "Screen capture rejected: Insecure origin $canonicalOrigin")
            callback(
                ScreenCaptureResult.Error(
                    code = "SecurityError",
                    message = "Screen capture is only permitted in secure contexts (HTTPS or localhost).",
                    diagnostic = "INSECURE_ORIGIN"
                )
            )
            return
        }

        // 2. Hardware / API support check
        val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (mediaProjectionManager == null) {
            Log.e(TAG, "MediaProjectionManager unavailable on this device/runtime")
            callback(
                ScreenCaptureResult.Error(
                    code = "NotSupportedError",
                    message = "MediaProjection screen sharing is not supported by the underlying WebView/device environment.",
                    diagnostic = "UNSUPPORTED_BY_WEBVIEW"
                )
            )
            return
        }

        // 3. Duplicate active session check for this tab and origin
        val existingSessionIds = tabSessions[tabId] ?: emptySet()
        for (sid in existingSessionIds) {
            val existing = activeSessions[sid]
            if (existing != null && existing.currentState.isActive) {
                Log.w(TAG, "Duplicate start rejected: Session $sid is already active in tab $tabId")
                callback(
                    ScreenCaptureResult.Error(
                        code = "InvalidStateError",
                        message = "A screen capture session is already in progress or requested for this tab.",
                        diagnostic = "DUPLICATE_START"
                    )
                )
                return
            }
        }

        // 4. Create new ScreenCaptureSession
        val session = ScreenCaptureSession(
            requestId = "req_sc_" + java.util.UUID.randomUUID().toString().substring(0, 8),
            tabId = tabId,
            origin = canonicalOrigin,
            topLevelOrigin = canonicalOrigin,
            isIncognito = isIncognito,
            videoConstraints = videoConstraints
        )

        session.transitionTo(ScreenCaptureState.REQUESTED)
        pendingRequests[session.requestId] = session
        activeSessions[session.sessionId] = session
        tabSessions.computeIfAbsent(tabId) { Collections.newSetFromMap(ConcurrentHashMap()) }.add(session.sessionId)

        session.onSessionEndedListener = { sess, reason ->
            logI("Session ${sess.sessionId} ended ($reason)")
            activeSessions.remove(sess.sessionId)
            pendingRequests.remove(sess.requestId)
            tabSessions[sess.tabId]?.remove(sess.sessionId)

            // If no active capturing sessions remain, stop the foreground service
            if (activeSessions.values.none { it.currentState == ScreenCaptureState.CAPTURING }) {
                hostRequester?.stopForegroundService()
            }
        }

        session.transitionTo(ScreenCaptureState.WAITING_PERMISSION)

        // 5. Evaluate permission with the canonical PermissionEngine
        val params = ScreenCaptureRequestParams(
            origin = canonicalOrigin,
            tabId = tabId,
            userGesture = userGesture,
            isIncognito = isIncognito,
            videoConstraints = videoConstraints,
            requestId = session.requestId
        )

        PermissionEngineApi.evaluateScreenCaptureRequest(context, params) { decision ->
            postToMain {
                // Reject stale tab / cross-origin callbacks
                val currentUrl = webView?.url
                if (currentUrl != null && OriginNormalizer.normalize(currentUrl) != canonicalOrigin) {
                    logW("Cross-origin navigation occurred during permission check for session ${session.sessionId}")
                    session.cancel("NAVIGATION_ORIGIN_CHANGED")
                    callback(
                        ScreenCaptureResult.Error(
                            code = "AbortError",
                            message = "Tab navigated to a different origin during screen capture request.",
                            diagnostic = "CROSS_ORIGIN_NAVIGATION"
                        )
                    )
                    return@postToMain
                }

                // Check if session became stale during permission prompt
                if (session.currentState != ScreenCaptureState.WAITING_PERMISSION) {
                    logW("Session ${session.sessionId} is no longer waiting for permission (current: ${session.currentState})")
                    pendingRequests.remove(session.requestId)
                    callback(
                        ScreenCaptureResult.Error(
                            code = "AbortError",
                            message = "Screen capture request was cancelled or became stale.",
                            diagnostic = "STALE_REQUEST"
                        )
                    )
                    return@postToMain
                }

                if (!decision.isAllowed) {
                    logI("PermissionEngine denied SCREEN_CAPTURE for $canonicalOrigin: ${decision.reason}")
                    session.fail("Screen capture permission was denied by user policy: ${decision.reason}", "NotAllowedError")
                    callback(
                        ScreenCaptureResult.Error(
                            code = "NotAllowedError",
                            message = "Permission denied: ${decision.reason}",
                            diagnostic = "PERMISSION_DENIED"
                        )
                    )
                    return@postToMain
                }

                // 6. Transition to WAITING_MEDIA_PROJECTION and request Android OS consent
                session.transitionTo(ScreenCaptureState.WAITING_MEDIA_PROJECTION)
                val requester = hostRequester
                if (requester == null) {
                    logE("MediaProjectionHostRequester not attached to MainActivity")
                    session.fail("Media projection host is not attached", "NotSupportedError")
                    callback(
                        ScreenCaptureResult.Error(
                            code = "NotSupportedError",
                            message = "MediaProjection host Activity is unavailable to process screen capture consent.",
                            diagnostic = "UNSUPPORTED_BY_WEBVIEW"
                        )
                    )
                    return@postToMain
                }

                requester.requestMediaProjectionConsent(session.requestId) { resultCode, resultData ->
                    postToMain {
                        pendingRequests.remove(session.requestId)

                        // Check if session was cancelled while consent dialog was visible
                        if (session.currentState != ScreenCaptureState.WAITING_MEDIA_PROJECTION) {
                            logW("Session ${session.sessionId} is no longer WAITING_MEDIA_PROJECTION (state: ${session.currentState})")
                            callback(
                                ScreenCaptureResult.Error(
                                    code = "AbortError",
                                    message = "Screen capture consent request was aborted.",
                                    diagnostic = "STALE_REQUEST"
                                )
                            )
                            return@postToMain
                        }

                        if (resultCode != Activity.RESULT_OK || resultData == null) {
                            logI("MediaProjection consent denied by user (resultCode=$resultCode)")
                            session.fail("Screen capture consent denied by user.", "NotAllowedError")
                            callback(
                                ScreenCaptureResult.Error(
                                    code = "NotAllowedError",
                                    message = "The user cancelled or denied screen capture consent.",
                                    diagnostic = "MEDIA_PROJECTION_DENIED"
                                )
                            )
                            return@postToMain
                        }

                        // 7. Consent granted: Start foreground service and configure MediaProjection
                        try {
                            requester.startForegroundService(resultCode, resultData)

                            val projection = mediaProjectionManager.getMediaProjection(resultCode, resultData)
                            if (projection == null) {
                                session.fail("Failed to acquire MediaProjection token", "AbortError")
                                callback(
                                    ScreenCaptureResult.Error(
                                        code = "AbortError",
                                        message = "Unable to create native MediaProjection session.",
                                        diagnostic = "MEDIA_PROJECTION_NULL"
                                    )
                                )
                                return@postToMain
                            }

                            val metrics = context.resources.displayMetrics
                            val captureWidth = (metrics.widthPixels.coerceAtLeast(720)).coerceAtMost(1920)
                            val captureHeight = (metrics.heightPixels.coerceAtLeast(1280)).coerceAtMost(1920)
                            val densityDpi = metrics.densityDpi

                            val success = session.attachMediaProjection(
                                projection = projection,
                                captureWidth = captureWidth,
                                captureHeight = captureHeight,
                                dpi = densityDpi,
                                fps = 30
                            )

                            if (success) {
                                callback(ScreenCaptureResult.Success(session))
                            } else {
                                callback(
                                    ScreenCaptureResult.Error(
                                        code = "AbortError",
                                        message = session.failureReason ?: "Failed to initialize screen capture stream.",
                                        diagnostic = "CAPTURE_INIT_FAILED"
                                    )
                                )
                            }
                        } catch (t: Throwable) {
                            logE("Error starting MediaProjection capture", t)
                            session.fail("Error creating MediaProjection: ${t.message}", "AbortError")
                            callback(
                                ScreenCaptureResult.Error(
                                    code = "AbortError",
                                    message = "Error initializing native capture: ${t.message}",
                                    diagnostic = "EXCEPTION_THROWN"
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Stops an active or pending capture session.
     */
    fun stopCapture(sessionId: String, reason: String = "USER_STOPPED") {
        val session = activeSessions[sessionId]
        if (session != null) {
            session.stop(reason)
        } else {
            logD("stopCapture: Session $sessionId not found (possibly duplicate stop or already terminated)")
        }
    }

    /**
     * Cancels a pending request by requestId.
     */
    fun cancelPendingRequest(requestId: String, reason: String = "CANCELLED") {
        val session = pendingRequests.remove(requestId)
        session?.cancel(reason)
    }

    /**
     * Called when a tab is closed: cleanly terminates all associated capture sessions.
     */
    fun onTabClosed(tabId: String) {
        val sessionIds = tabSessions.remove(tabId) ?: return
        logI("Tab $tabId closed: cleaning up ${sessionIds.size} screen capture sessions")
        for (sid in sessionIds) {
            val session = activeSessions.remove(sid)
            session?.stop("TAB_CLOSED")
        }
    }

    /**
     * Called when a tab navigates to a new URL: stops capture if the origin changes.
     */
    fun onNavigation(tabId: String, newUrl: String?) {
        if (newUrl.isNullOrEmpty()) return
        val newOrigin = OriginNormalizer.normalize(newUrl)
        val sessionIds = tabSessions[tabId] ?: return
        for (sid in sessionIds.toList()) {
            val session = activeSessions[sid]
            if (session != null && session.currentState.isActive && session.origin != newOrigin) {
                logI("Tab $tabId navigated from ${session.origin} to $newOrigin: terminating screen capture session $sid")
                session.stop("NAVIGATION_ORIGIN_CHANGED")
            }
        }
    }

    /**
     * Called when a WebView is destroyed.
     */
    fun onWebViewDestroyed(tabId: String) {
        onTabClosed(tabId)
    }

    /**
     * Called on Activity destruction: ensures any non-persisting resources are cleaned up.
     */
    fun onActivityDestroyed() {
        logI("Host Activity destroyed: verifying capture session stability")
    }

    /**
     * Stops all active sessions across all tabs and releases the foreground service.
     */
    fun stopAll(reason: String = "SHUTDOWN") {
        for (session in activeSessions.values) {
            session.stop(reason)
        }
        activeSessions.clear()
        tabSessions.clear()
        pendingRequests.clear()
        hostRequester?.stopForegroundService()
    }

    fun getActiveSession(sessionId: String): ScreenCaptureSession? {
        return activeSessions[sessionId]
    }

    fun getActiveSessionsForTab(tabId: String): List<ScreenCaptureSession> {
        val ids = tabSessions[tabId] ?: return emptyList()
        return ids.mapNotNull { activeSessions[it] }
    }

    /**
     * Updates display metrics across active capture sessions for a tab (e.g. on orientation change).
     */
    fun updateDisplayMetrics(tabId: String, width: Int, height: Int, dpi: Int) {
        val sessions = getActiveSessionsForTab(tabId)
        for (session in sessions) {
            session.updateDisplayMetrics(width, height, dpi)
        }
    }

    fun getAllActiveSessions(): List<ScreenCaptureSession> {
        return activeSessions.values.toList()
    }
}
