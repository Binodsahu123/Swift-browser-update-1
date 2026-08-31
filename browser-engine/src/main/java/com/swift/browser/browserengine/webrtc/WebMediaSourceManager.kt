package com.swift.browser.browserengine.webrtc

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
import android.webkit.WebView
import com.swift.browser.browserengine.screencapture.ScreenCaptureManager
import com.swift.browser.browserengine.screencapture.ScreenCaptureState
import java.util.concurrent.ConcurrentHashMap

enum class WebMediaSourceType {
    CAMERA,
    MICROPHONE,
    SCREEN
}

enum class PermissionState {
    NOT_REQUESTED,
    GRANTED,
    DENIED
}

enum class HardwareState {
    CONNECTED,
    DISCONNECTED,
    ERROR
}

enum class CaptureState {
    IDLE,
    CAPTURING,
    PAUSED
}

data class WebSourceMetrics(
    var availability: Boolean = false,
    var permissionState: PermissionState = PermissionState.NOT_REQUESTED,
    var hardwareState: HardwareState = HardwareState.CONNECTED,
    var captureState: CaptureState = CaptureState.IDLE,
    var trackState: String = "ended",
    var selectedDevice: String? = null,
    var width: Int = 1280,
    var height: Int = 720,
    var fps: Int = 30,
    var sampleRate: Int = 44100,
    var channels: Int = 2,
    var rotation: Int = 0
)

object WebMediaSourceManager {
    private const val TAG = "WebMediaSourceManager"

    // Tracks source state per tabId and source type
    private val tabSourceStates = ConcurrentHashMap<String, ConcurrentHashMap<WebMediaSourceType, WebSourceMetrics>>()

    // Active pending promise callbacks for device switches (tabId_kind_operationId -> callback)
    private val pendingSwitchCallbacks = ConcurrentHashMap<String, (Boolean, String?) -> Unit>()

    // Stores the target deviceId for a pending switch operation: tabId_kind_operationId -> targetDeviceId
    private val pendingTargetDevices = ConcurrentHashMap<String, String>()
    
    // Active operation ID: tabId_kind -> operationId
    private val activeOperationIds = ConcurrentHashMap<String, String>()

    fun getSourceMetrics(tabId: String, type: WebMediaSourceType): WebSourceMetrics {
        val tabMap = tabSourceStates.getOrPut(tabId) { ConcurrentHashMap() }
        return tabMap.getOrPut(type) { WebSourceMetrics() }
    }

    fun getActiveOperationId(tabId: String, kind: String): String? {
        return activeOperationIds["${tabId}_$kind"]
    }

    /**
     * Initializes and updates source availability based on actual hardware checks.
     */
    fun updateHardwareAvailability(context: Context, tabId: String) {
        val camMetrics = getSourceMetrics(tabId, WebMediaSourceType.CAMERA)
        val micMetrics = getSourceMetrics(tabId, WebMediaSourceType.MICROPHONE)
        val screenMetrics = getSourceMetrics(tabId, WebMediaSourceType.SCREEN)

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        val hasCamera = cameraManager?.cameraIdList?.isNotEmpty() ?: false
        camMetrics.availability = hasCamera
        camMetrics.hardwareState = if (hasCamera) HardwareState.CONNECTED else HardwareState.DISCONNECTED

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val hasMicrophone = audioManager?.getDevices(AudioManager.GET_DEVICES_INPUTS)?.isNotEmpty() ?: false
        micMetrics.availability = hasMicrophone
        micMetrics.hardwareState = if (hasMicrophone) HardwareState.CONNECTED else HardwareState.DISCONNECTED

        screenMetrics.availability = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP
        screenMetrics.hardwareState = HardwareState.CONNECTED
    }

    /**
     * Starts camera capture for a tab.
     */
    fun startCamera(
        context: Context,
        webView: WebView,
        tabId: String,
        cameraId: String?,
        width: Int = 1280,
        height: Int = 720,
        fps: Int = 30
    ) {
        Log.i(TAG, "Starting camera: tabId=$tabId, preferredCameraId=$cameraId")
        updateHardwareAvailability(context, tabId)
        
        val metrics = getSourceMetrics(tabId, WebMediaSourceType.CAMERA)
        metrics.permissionState = PermissionState.GRANTED
        metrics.captureState = CaptureState.CAPTURING
        metrics.trackState = "live"
        metrics.width = width
        metrics.height = height
        metrics.fps = fps

        // Enforce safe default camera if none specified
        val cameras = WebMediaDeviceManager.enumerateVideoDevices(context)
        val selectedId = cameraId ?: cameras.firstOrNull()?.deviceId
        metrics.selectedDevice = selectedId
        if (selectedId != null) {
            WebMediaDeviceManager.setSelectedDeviceId(tabId, "videoinput", selectedId)
        }
    }

    /**
     * Stops camera capture for a tab.
     */
    fun stopCamera(tabId: String) {
        Log.i(TAG, "Stopping camera: tabId=$tabId")
        val metrics = getSourceMetrics(tabId, WebMediaSourceType.CAMERA)
        metrics.captureState = CaptureState.IDLE
        metrics.trackState = "ended"
    }

    /**
     * Standard promise-propagating camera switch.
     */
    fun switchCamera(
        context: Context,
        webView: WebView,
        tabId: String,
        targetCameraId: String,
        callback: (Boolean, String?) -> Unit
    ) {
        Log.i(TAG, "Switching camera to $targetCameraId for tab $tabId")
        val metrics = getSourceMetrics(tabId, WebMediaSourceType.CAMERA)
        
        if (metrics.captureState != CaptureState.CAPTURING) {
            callback(false, "Camera is not currently capturing")
            return
        }

        // Validate target camera exists
        val availableCameras = WebMediaDeviceManager.enumerateVideoDevices(context)
        val targetDev = availableCameras.firstOrNull { it.deviceId == targetCameraId }
        if (targetDev == null && availableCameras.isNotEmpty()) {
            callback(false, "Requested camera device not found: $targetCameraId")
            return
        }

        val kind = "videoinput"
        val operationId = java.util.UUID.randomUUID().toString()
        val opKey = "${tabId}_$kind"
        activeOperationIds[opKey] = operationId

        val callbackKey = "${tabId}_${kind}_$operationId"
        pendingSwitchCallbacks[callbackKey] = callback
        pendingTargetDevices[callbackKey] = targetCameraId

        // Evaluate standard Javascript device switch returning a Promise
        val jsCmd = """
            (function() {
                var bridge = window.AndroidWebRtcBridge || window.WebRtcBridge;
                if (typeof window.AndroidWebRtcBridge_switchDevice === 'function') {
                    window.AndroidWebRtcBridge_switchDevice('$kind', '$targetCameraId', '$operationId')
                        .then(() => {
                            if (bridge && typeof bridge.onDeviceSwitchSuccess === 'function') {
                                bridge.onDeviceSwitchSuccess('$tabId', '$kind', '$targetCameraId', '$operationId');
                            }
                        })
                        .catch(err => {
                            if (bridge && typeof bridge.onDeviceSwitchFailure === 'function') {
                                bridge.onDeviceSwitchFailure('$tabId', '$kind', '$targetCameraId', err.toString(), '$operationId');
                            }
                        });
                } else {
                    if (bridge && typeof bridge.onDeviceSwitchFailure === 'function') {
                        bridge.onDeviceSwitchFailure('$tabId', '$kind', '$targetCameraId', 'Bridge function window.AndroidWebRtcBridge_switchDevice not found', '$operationId');
                    }
                }
            })();
        """.trimIndent()

        webView.post {
            webView.evaluateJavascript(jsCmd, null)
        }
    }

    /**
     * Toggles between front and back camera.
     */
    fun toggleCamera(
        context: Context,
        webView: WebView,
        tabId: String,
        callback: (Boolean, String?) -> Unit
    ) {
        val cameras = WebMediaDeviceManager.enumerateVideoDevices(context)
        if (cameras.isEmpty()) {
            callback(false, "No camera devices available")
            return
        }

        val cameraManager = context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        val currentSelected = getSourceMetrics(tabId, WebMediaSourceType.CAMERA).selectedDevice
            ?: WebMediaDeviceManager.getSelectedDeviceId(tabId, "videoinput")

        val currentFacing = currentSelected?.let { id ->
            try {
                cameraManager?.getCameraCharacteristics(id)?.get(CameraCharacteristics.LENS_FACING)
            } catch (_: Exception) {
                null
            }
        }

        val targetFacing = when (currentFacing) {
            CameraCharacteristics.LENS_FACING_FRONT -> CameraCharacteristics.LENS_FACING_BACK
            CameraCharacteristics.LENS_FACING_BACK -> CameraCharacteristics.LENS_FACING_FRONT
            else -> null
        }

        val targetCamera = if (targetFacing != null && cameraManager != null) {
            cameras.firstOrNull { dev ->
                try {
                    cameraManager.getCameraCharacteristics(dev.deviceId).get(CameraCharacteristics.LENS_FACING) == targetFacing
                } catch (_: Exception) {
                    false
                }
            } ?: cameras.firstOrNull { it.deviceId != currentSelected } ?: cameras.first()
        } else {
            cameras.firstOrNull { it.deviceId != currentSelected } ?: cameras.first()
        }

        switchCamera(context, webView, tabId, targetCamera.deviceId, callback)
    }

    /**
     * Starts microphone capture.
     */
    fun startMicrophone(
        context: Context,
        webView: WebView,
        tabId: String,
        micId: String?,
        sampleRate: Int = 44100,
        channels: Int = 2
    ) {
        Log.i(TAG, "Starting microphone: tabId=$tabId, preferredMicId=$micId")
        updateHardwareAvailability(context, tabId)

        val metrics = getSourceMetrics(tabId, WebMediaSourceType.MICROPHONE)
        metrics.permissionState = PermissionState.GRANTED
        metrics.captureState = CaptureState.CAPTURING
        metrics.trackState = "live"
        metrics.sampleRate = sampleRate
        metrics.channels = channels

        val mics = WebMediaDeviceManager.enumerateAudioDevices(context)
        val selectedId = micId ?: mics.firstOrNull()?.deviceId
        metrics.selectedDevice = selectedId
        if (selectedId != null) {
            WebMediaDeviceManager.setSelectedDeviceId(tabId, "audioinput", selectedId)
        }
    }

    /**
     * Stops microphone capture.
     */
    fun stopMicrophone(tabId: String) {
        Log.i(TAG, "Stopping microphone: tabId=$tabId")
        val metrics = getSourceMetrics(tabId, WebMediaSourceType.MICROPHONE)
        metrics.captureState = CaptureState.IDLE
        metrics.trackState = "ended"
    }

    /**
     * Standard promise-propagating microphone switch.
     */
    fun switchMicrophone(
        context: Context,
        webView: WebView,
        tabId: String,
        targetMicId: String,
        callback: (Boolean, String?) -> Unit
    ) {
        Log.i(TAG, "Switching microphone to $targetMicId for tab $tabId")
        val metrics = getSourceMetrics(tabId, WebMediaSourceType.MICROPHONE)

        if (metrics.captureState != CaptureState.CAPTURING) {
            callback(false, "Microphone is not currently capturing")
            return
        }

        // Validate target microphone exists
        val availableMics = WebMediaDeviceManager.enumerateAudioDevices(context)
        val targetDev = availableMics.firstOrNull { it.deviceId == targetMicId || it.deviceId == "mic_$targetMicId" }
        if (targetDev == null && availableMics.isNotEmpty()) {
            callback(false, "Requested microphone device not found: $targetMicId")
            return
        }

        val kind = "audioinput"
        val operationId = java.util.UUID.randomUUID().toString()
        val opKey = "${tabId}_$kind"
        activeOperationIds[opKey] = operationId

        val callbackKey = "${tabId}_${kind}_$operationId"
        pendingSwitchCallbacks[callbackKey] = callback
        pendingTargetDevices[callbackKey] = targetMicId

        // Evaluate standard Javascript device switch returning a Promise
        val jsCmd = """
            (function() {
                var bridge = window.AndroidWebRtcBridge || window.WebRtcBridge;
                if (typeof window.AndroidWebRtcBridge_switchDevice === 'function') {
                    window.AndroidWebRtcBridge_switchDevice('$kind', '$targetMicId', '$operationId')
                        .then(() => {
                            if (bridge && typeof bridge.onDeviceSwitchSuccess === 'function') {
                                bridge.onDeviceSwitchSuccess('$tabId', '$kind', '$targetMicId', '$operationId');
                            }
                        })
                        .catch(err => {
                            if (bridge && typeof bridge.onDeviceSwitchFailure === 'function') {
                                bridge.onDeviceSwitchFailure('$tabId', '$kind', '$targetMicId', err.toString(), '$operationId');
                            }
                        });
                } else {
                    if (bridge && typeof bridge.onDeviceSwitchFailure === 'function') {
                        bridge.onDeviceSwitchFailure('$tabId', '$kind', '$targetMicId', 'Bridge function window.AndroidWebRtcBridge_switchDevice not found', '$operationId');
                    }
                }
            })();
        """.trimIndent()

        webView.post {
            webView.evaluateJavascript(jsCmd, null)
        }
    }

    /**
     * Starts screen capture through native MediaProjection.
     */
    fun startScreenCapture(
        context: Context,
        webView: WebView,
        tabId: String,
        width: Int = 1280,
        height: Int = 720,
        fps: Int = 30,
        onResult: (Boolean, String?) -> Unit
    ) {
        Log.i(TAG, "Starting screen capture: tabId=$tabId")
        val metrics = getSourceMetrics(tabId, WebMediaSourceType.SCREEN)

        ScreenCaptureManager.requestScreenCapture(
            context = context,
            tabId = tabId,
            origin = webView.url ?: "https://localhost",
            userGesture = true,
            isIncognito = false,
            webView = webView
        ) { result ->
            when (result) {
                is com.swift.browser.browserengine.screencapture.ScreenCaptureResult.Success -> {
                    metrics.permissionState = PermissionState.GRANTED
                    metrics.captureState = CaptureState.CAPTURING
                    metrics.trackState = "live"
                    metrics.width = width
                    metrics.height = height
                    metrics.fps = fps
                    onResult(true, null)
                }
                is com.swift.browser.browserengine.screencapture.ScreenCaptureResult.Error -> {
                    metrics.permissionState = PermissionState.DENIED
                    metrics.captureState = CaptureState.IDLE
                    metrics.trackState = "ended"
                    onResult(false, result.message)
                }
            }
        }
    }

    /**
     * Stops screen capture session.
     */
    fun stopScreenCapture(tabId: String) {
        Log.i(TAG, "Stopping screen capture: tabId=$tabId")
        val metrics = getSourceMetrics(tabId, WebMediaSourceType.SCREEN)
        metrics.captureState = CaptureState.IDLE
        metrics.trackState = "ended"

        val activeSessions = ScreenCaptureManager.getActiveSessionsForTab(tabId)
        for (session in activeSessions) {
            ScreenCaptureManager.stopCapture(session.sessionId, "USER_STOPPED")
        }
    }

    /**
     * Callback from WebRtcBridge on successful promise resolution.
     */
    fun handleDeviceSwitchSuccess(tabId: String, kind: String, deviceId: String, operationId: String) {
        Log.i(TAG, "Device switch success callback: tabId=$tabId, kind=$kind, deviceId=$deviceId, operationId=$operationId")
        val opKey = "${tabId}_$kind"
        val activeOpId = activeOperationIds[opKey]
        if (activeOpId != operationId) {
            Log.w(TAG, "Ignoring stale switch success callback. activeOp=$activeOpId, callbackOp=$operationId")
            return
        }

        val callbackKey = "${tabId}_${kind}_$operationId"
        val targetDevice = pendingTargetDevices.remove(callbackKey) ?: deviceId
        
        // Update selection state ONLY after success!
        if (kind == "videoinput") {
            val metrics = getSourceMetrics(tabId, WebMediaSourceType.CAMERA)
            metrics.selectedDevice = targetDevice
            WebMediaDeviceManager.setSelectedDeviceId(tabId, "videoinput", targetDevice)
        } else if (kind == "audioinput") {
            val metrics = getSourceMetrics(tabId, WebMediaSourceType.MICROPHONE)
            metrics.selectedDevice = targetDevice
            WebMediaDeviceManager.setSelectedDeviceId(tabId, "audioinput", targetDevice)
        }

        val callback = pendingSwitchCallbacks.remove(callbackKey)
        callback?.invoke(true, null)
    }

    /**
     * Callback from WebRtcBridge on failed promise execution.
     */
    fun handleDeviceSwitchFailure(tabId: String, kind: String, deviceId: String, error: String, operationId: String) {
        Log.e(TAG, "Device switch failure callback: tabId=$tabId, kind=$kind, error=$error, operationId=$operationId")
        val opKey = "${tabId}_$kind"
        val activeOpId = activeOperationIds[opKey]
        if (activeOpId != operationId) {
            Log.w(TAG, "Ignoring stale switch failure callback. activeOp=$activeOpId, callbackOp=$operationId")
            return
        }

        val callbackKey = "${tabId}_${kind}_$operationId"
        pendingTargetDevices.remove(callbackKey)
        val callback = pendingSwitchCallbacks.remove(callbackKey)
        callback?.invoke(false, error)
    }

    /**
     * Handles physical device removal.
     */
    fun handleDeviceRemoval(context: Context, deviceId: String) {
        Log.w(TAG, "Physical device removed: $deviceId")
        tabSourceStates.forEach { (tabId, tabMap) ->
            val cam = tabMap[WebMediaSourceType.CAMERA]
            if (cam?.selectedDevice == deviceId) {
                Log.w(TAG, "Camera device $deviceId disconnected. Triggering fallback re-acquisition.")
                val cameras = WebMediaDeviceManager.enumerateVideoDevices(context)
                val fallbackCamera = cameras.firstOrNull { it.deviceId != deviceId }
                if (fallbackCamera != null) {
                    cam.selectedDevice = fallbackCamera.deviceId
                    WebMediaDeviceManager.setSelectedDeviceId(tabId, "videoinput", fallbackCamera.deviceId)
                } else {
                    cam.hardwareState = HardwareState.DISCONNECTED
                    cam.selectedDevice = null
                }
            }

            val mic = tabMap[WebMediaSourceType.MICROPHONE]
            if (mic?.selectedDevice == deviceId) {
                Log.w(TAG, "Microphone device $deviceId disconnected. Triggering fallback re-acquisition.")
                val mics = WebMediaDeviceManager.enumerateAudioDevices(context)
                val fallbackMic = mics.firstOrNull { it.deviceId != deviceId }
                if (fallbackMic != null) {
                    mic.selectedDevice = fallbackMic.deviceId
                    WebMediaDeviceManager.setSelectedDeviceId(tabId, "audioinput", fallbackMic.deviceId)
                } else {
                    mic.hardwareState = HardwareState.DISCONNECTED
                    mic.selectedDevice = null
                }
            }
        }
    }

    /**
     * Handles user permission denial.
     */
    fun handlePermissionDenial(tabId: String, sourceType: WebMediaSourceType) {
        Log.i(TAG, "Permission denied for tab $tabId, type $sourceType")
        val metrics = getSourceMetrics(tabId, sourceType)
        metrics.permissionState = PermissionState.DENIED
        metrics.captureState = CaptureState.IDLE
        metrics.trackState = "ended"
    }

    /**
     * Clears and stops all capture sessions on WebView destruction.
     */
    fun handleWebViewDestruction(tabId: String) {
        Log.i(TAG, "WebView destroyed: tabId=$tabId. Releasing resources.")
        stopCamera(tabId)
        stopMicrophone(tabId)
        stopScreenCapture(tabId)
        tabSourceStates.remove(tabId)
        pendingSwitchCallbacks.keys.removeAll { it.startsWith("${tabId}_") }
        pendingTargetDevices.keys.removeAll { it.startsWith("${tabId}_") }
        activeOperationIds.keys.removeAll { it.startsWith("${tabId}_") }
    }

    /**
     * Clears and stops all capture sessions on Tab close.
     */
    fun handleTabClose(tabId: String) {
        handleWebViewDestruction(tabId)
        ScreenCaptureManager.onTabClosed(tabId)
    }

    /**
     * Resets and clears all cached sessions.
     */
    fun clear() {
        tabSourceStates.clear()
        pendingSwitchCallbacks.clear()
    }
}
