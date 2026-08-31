package com.swift.browser.videoengine.live

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.util.Log
import android.view.Surface

class ScreenVideoSource(
    private val context: Context,
    private val mediaProjection: MediaProjection
) : VideoSource {
    companion object {
        private const val TAG = "ScreenVideoSource"
    }

    private var virtualDisplay: VirtualDisplay? = null
    private var outputSurface: Surface? = null
    private var activeConfig: LiveStreamConfig? = null

    override fun setOutputSurface(surface: Surface?) {
        synchronized(this) {
            this.outputSurface = surface
        }
    }

    override fun start(surface: Surface) {
        setOutputSurface(surface)
        val config = activeConfig ?: LiveStreamConfig("", "")
        startCapture(config)
    }

    override fun stop() {
        stopCapture()
    }

    override fun isRunning(): Boolean {
        synchronized(this) {
            return virtualDisplay != null
        }
    }

    override val width: Int
        get() = activeConfig?.width ?: 1280

    override val height: Int
        get() = activeConfig?.height ?: 720

    override val fps: Int
        get() = activeConfig?.fps ?: 30

    override val rotation: Int
        get() = activeConfig?.rotation ?: 0

    override val sourceType: LiveVideoSourceType
        get() = LiveVideoSourceType.SCREEN

    override fun startCapture(config: LiveStreamConfig) {
        synchronized(this) {
            this.activeConfig = config
            val surface = outputSurface
            if (surface == null) {
                Log.e(TAG, "Cannot start screen capture: No output surface configured")
                return
            }

            if (virtualDisplay != null) {
                Log.w(TAG, "Screen capture already active")
                return
            }

            try {
                Log.i(TAG, "Starting Screen capture using MediaProjection into Surface: $surface")
                val metrics = context.resources.displayMetrics
                val dpi = metrics.densityDpi
                
                virtualDisplay = mediaProjection.createVirtualDisplay(
                    "LiveStreamScreenCapture",
                    config.width,
                    config.height,
                    dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    surface,
                    null,
                    null
                )
                Log.i(TAG, "VirtualDisplay created successfully for screen live-streaming")
            } catch (e: Exception) {
                Log.e(TAG, "Error creating virtual display: ${e.message}", e)
            }
        }
    }

    override fun stopCapture() {
        synchronized(this) {
            Log.i(TAG, "Stopping Screen capture virtual display")
            try {
                virtualDisplay?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing virtual display: ${e.message}")
            } finally {
                virtualDisplay = null
            }
        }
    }

    override fun release() {
        stopCapture()
        outputSurface = null
    }
}
