package com.swift.browser.browserengine.screencapture

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * Encapsulates the lifecycle, native state, display resources, and security context
 * of a web screen-sharing session initiated via navigator.mediaDevices.getDisplayMedia().
 */
class ScreenCaptureSession(
    val sessionId: String = "sc_session_" + UUID.randomUUID().toString().substring(0, 8),
    val requestId: String,
    val tabId: String,
    val origin: String,
    val topLevelOrigin: String = origin,
    val isIncognito: Boolean = false,
    val videoConstraints: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    private val TAG = "ScreenCaptureSession"
    private val stateRef = AtomicReference(ScreenCaptureState.IDLE)
    
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

    var mediaProjection: MediaProjection? = null
        private set
    var virtualDisplay: VirtualDisplay? = null
        private set
    var imageReader: ImageReader? = null
        private set
    private var projectionCallback: MediaProjection.Callback? = null

    var width: Int = 1280
        private set
    var height: Int = 720
        private set
    var densityDpi: Int = 320
        private set
    var frameRate: Int = 30
        private set

    var failureReason: String? = null
        private set
    var failureCode: String? = null
        private set

    var onStateChangedListener: ((session: ScreenCaptureSession, oldState: ScreenCaptureState, newState: ScreenCaptureState) -> Unit)? = null
    var onSessionEndedListener: ((session: ScreenCaptureSession, reason: String) -> Unit)? = null

    val currentState: ScreenCaptureState
        get() = stateRef.get()

    /**
     * Atomically transitions session to targetState.
     */
    fun transitionTo(targetState: ScreenCaptureState): Boolean {
        while (true) {
            val current = stateRef.get()
            if (current == targetState) return true
            if (current.isTerminal && !targetState.isTerminal) {
                logW("Cannot transition from terminal state $current to $targetState for session $sessionId")
                return false
            }
            if (stateRef.compareAndSet(current, targetState)) {
                logD("Session $sessionId transitioned: $current -> $targetState")
                postToMain {
                    onStateChangedListener?.invoke(this, current, targetState)
                }
                return true
            }
        }
    }

    /**
     * Configures and starts native VirtualDisplay capture using the provided MediaProjection token.
     */
    fun attachMediaProjection(
        projection: MediaProjection,
        captureWidth: Int = 1280,
        captureHeight: Int = 720,
        dpi: Int = 320,
        fps: Int = 30
    ): Boolean {
        if (!transitionTo(ScreenCaptureState.CAPTURING)) {
            logW("Failed to transition to CAPTURING for session $sessionId")
            return false
        }

        this.mediaProjection = projection
        this.width = captureWidth
        this.height = captureHeight
        this.densityDpi = dpi
        this.frameRate = fps

        try {
            // Register MediaProjection callback to handle system stop events
            val callbackHandler = mainHandler
            val callback = object : MediaProjection.Callback() {
                override fun onStop() {
                    logI("Native MediaProjection onStop callback triggered for session $sessionId")
                    stop("SYSTEM_PROJECTION_STOPPED")
                    com.swift.browser.browserengine.webrtc.WebRtcRuntimeManager.handleScreenCaptureEnded(tabId, origin)
                }
            }
            this.projectionCallback = callback
            if (callbackHandler != null) {
                projection.registerCallback(callback, callbackHandler)
            } else {
                projection.registerCallback(callback, null)
            }

            // Setup ImageReader and VirtualDisplay for screen capture
            val reader = ImageReader.newInstance(
                captureWidth,
                captureHeight,
                android.graphics.PixelFormat.RGBA_8888,
                2
            )
            this.imageReader = reader

            val vDisplay = projection.createVirtualDisplay(
                "SwiftScreenCapture_$sessionId",
                captureWidth,
                captureHeight,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                callbackHandler
            )
            this.virtualDisplay = vDisplay

            logI("Screen capture pipeline actively capturing for origin $origin (tab $tabId, session $sessionId, ${captureWidth}x${captureHeight})")
            return true
        } catch (t: Throwable) {
            logE("Error initializing virtual display capture for session $sessionId", t)
            fail("Failed to initialize virtual display: ${t.message}", "AbortError")
            return false
        }
    }

    /**
     * Dynamically updates display metrics for screen orientation changes.
     */
    fun updateDisplayMetrics(newWidth: Int, newHeight: Int, newDpi: Int) {
        if (currentState == ScreenCaptureState.CAPTURING) {
            this.width = newWidth
            this.height = newHeight
            this.densityDpi = newDpi
            try {
                virtualDisplay?.resize(newWidth, newHeight, newDpi)
                logI("Resized VirtualDisplay for session $sessionId to ${newWidth}x${newHeight} ($newDpi dpi)")
            } catch (e: Exception) {
                logW("Error resizing virtual display: ${e.message}")
            }
        }
    }

    /**
     * Gracefully stops the active capture session and releases all hardware/virtual display resources.
     */
    fun stop(reason: String = "USER_STOPPED") {
        val current = stateRef.get()
        if (current.isTerminal || current == ScreenCaptureState.STOPPING) {
            return
        }

        transitionTo(ScreenCaptureState.STOPPING)
        cleanup()
        transitionTo(ScreenCaptureState.STOPPED)

        postToMain {
            onSessionEndedListener?.invoke(this, reason)
        }
    }

    /**
     * Cancels a pending request (before capture is active).
     */
    fun cancel(reason: String = "CANCELLED") {
        stop(reason)
    }

    /**
     * Fails the session with an error code and message.
     */
    fun fail(reason: String, code: String = "NotAllowedError") {
        this.failureReason = reason
        this.failureCode = code

        cleanup()
        transitionTo(ScreenCaptureState.FAILED)

        postToMain {
            onSessionEndedListener?.invoke(this, reason)
        }
    }

    /**
     * Safe resource cleanup. Never throws exceptions.
     */
    fun cleanup() {
        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
            logW("Error releasing virtual display: ${e.message}")
        } finally {
            virtualDisplay = null
        }

        try {
            imageReader?.surface?.release()
        } catch (_: Exception) {}

        try {
            imageReader?.close()
        } catch (e: Exception) {
            logW("Error closing image reader: ${e.message}")
        } finally {
            imageReader = null
        }

        try {
            projectionCallback?.let { cb ->
                mediaProjection?.unregisterCallback(cb)
            }
        } catch (e: Exception) {
            logW("Error unregistering MediaProjection callback: ${e.message}")
        } finally {
            projectionCallback = null
        }

        try {
            mediaProjection?.stop()
        } catch (e: Exception) {
            logW("Error stopping media projection: ${e.message}")
        } finally {
            mediaProjection = null
        }
    }
}
