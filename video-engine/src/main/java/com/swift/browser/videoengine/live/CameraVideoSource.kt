package com.swift.browser.videoengine.live

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface

class CameraVideoSource(private val context: Context) : VideoSource {
    companion object {
        private const val TAG = "CameraVideoSource"
    }

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var outputSurface: Surface? = null
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
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
            return cameraThread != null
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
        get() = LiveVideoSourceType.CAMERA

    @SuppressLint("MissingPermission")
    override fun startCapture(config: LiveStreamConfig) {
        synchronized(this) {
            this.activeConfig = config
            if (cameraThread != null) return

            Log.i(TAG, "Starting Camera capture")
            cameraThread = HandlerThread("CameraSourceThread").apply { start() }
            cameraHandler = Handler(cameraThread!!.looper)

            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            if (cameraManager == null) {
                Log.e(TAG, "CameraManager not available")
                return
            }

            try {
                val cameraIdList = cameraManager.cameraIdList
                if (cameraIdList.isEmpty()) {
                    Log.w(TAG, "No cameras available on this device")
                    return
                }
                
                // Pick first available front/back camera
                val cameraId = cameraIdList.firstOrNull() ?: "0"

                cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        synchronized(this@CameraVideoSource) {
                            cameraDevice = camera
                            createCameraSession()
                        }
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        synchronized(this@CameraVideoSource) {
                            Log.w(TAG, "Camera disconnected")
                            stopCapture()
                        }
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        synchronized(this@CameraVideoSource) {
                            Log.e(TAG, "Camera open error: $error")
                            stopCapture()
                        }
                    }
                }, cameraHandler)

            } catch (e: Exception) {
                Log.e(TAG, "Error opening camera: ${e.message}", e)
            }
        }
    }

    private fun createCameraSession() {
        val device = cameraDevice ?: return
        val surface = outputSurface ?: run {
            Log.e(TAG, "No output surface configured for camera preview/encode")
            return
        }

        try {
            val surfaces = listOf(surface)
            device.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    synchronized(this@CameraVideoSource) {
                        captureSession = session
                        try {
                            val requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                                addTarget(surface)
                            }
                            session.setRepeatingRequest(requestBuilder.build(), null, cameraHandler)
                            Log.i(TAG, "Camera capture session configured and running")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error setting camera repeating request: ${e.message}", e)
                        }
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Camera capture session configuration failed")
                }
            }, cameraHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating capture session: ${e.message}", e)
        }
    }

    override fun stopCapture() {
        synchronized(this) {
            Log.i(TAG, "Stopping Camera capture")
            try {
                captureSession?.stopRepeating()
                captureSession?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing capture session: ${e.message}")
            } finally {
                captureSession = null
            }

            try {
                cameraDevice?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing camera device: ${e.message}")
            } finally {
                cameraDevice = null
            }

            cameraThread?.quitSafely()
            cameraThread = null
            cameraHandler = null
        }
    }

    override fun release() {
        stopCapture()
        outputSurface = null
    }
}
