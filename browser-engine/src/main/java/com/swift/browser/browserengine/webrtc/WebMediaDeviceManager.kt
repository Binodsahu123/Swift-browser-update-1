package com.swift.browser.browserengine.webrtc

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
import android.webkit.WebView
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

data class WebMediaDeviceInfo(
    val deviceId: String,
    val kind: String, // "videoinput" or "audioinput"
    val label: String,
    val groupId: String
)

object WebMediaDeviceManager {
    private const val TAG = "WebMediaDeviceManager"
    
    // Track active WebViews per tabId using WeakReference to prevent leaks
    private val activeWebViews = ConcurrentHashMap<String, WeakReference<WebView>>()
    
    // Remember current selected device per tab/session. Map of tabId -> (kind -> deviceId)
    private val selectedDevices = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()
    
    // Listeners for device changes
    private val deviceChangeListeners = ConcurrentHashMap<String, () -> Unit>()

    private var isListenersRegistered = false
    private var cameraManager: CameraManager? = null
    private var audioManager: AudioManager? = null

    fun initialize(context: Context) {
        synchronized(this) {
            if (isListenersRegistered) return
            val appContext = context.applicationContext
            val camManager = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val audManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            cameraManager = camManager
            audioManager = audManager

            try {
                // Register camera availability callback
                camManager?.registerAvailabilityCallback(object : CameraManager.AvailabilityCallback() {
                    override fun onCameraAvailable(cameraId: String) {
                        Log.i(TAG, "Camera available: $cameraId. Dispatching device change event.")
                        onDeviceChanged()
                    }

                    override fun onCameraUnavailable(cameraId: String) {
                        Log.i(TAG, "Camera unavailable: $cameraId. Dispatching device change event.")
                        onDeviceChanged()
                    }
                }, null)

                // Register audio device callback
                audManager?.registerAudioDeviceCallback(object : android.media.AudioDeviceCallback() {
                    override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                        Log.i(TAG, "Audio devices added. Dispatching device change event.")
                        onDeviceChanged()
                    }

                    override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                        Log.i(TAG, "Audio devices removed. Dispatching device change event.")
                        onDeviceChanged()
                        handleAudioDeviceDisappearance(removedDevices)
                    }
                }, null)

                isListenersRegistered = true
                Log.i(TAG, "WebMediaDeviceManager callbacks registered successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register WebMediaDeviceManager callbacks: ${e.message}", e)
            }
        }
    }

    fun registerWebView(tabId: String, webView: WebView) {
        activeWebViews[tabId] = WeakReference(webView)
        Log.d(TAG, "Registered WebView for tabId: $tabId")
    }

    fun getWebView(tabId: String): WebView? {
        return activeWebViews[tabId]?.get()
    }

    fun unregisterWebView(tabId: String) {
        activeWebViews.remove(tabId)
        selectedDevices.remove(tabId)
        Log.d(TAG, "Unregistered WebView and cleared selected devices for tabId: $tabId")
    }

    fun addDeviceChangeListener(id: String, listener: () -> Unit) {
        deviceChangeListeners[id] = listener
    }

    fun removeDeviceChangeListener(id: String) {
        deviceChangeListeners.remove(id)
    }

    private var lastGlobalSnapshot: List<String>? = null

    private fun getPhysicalInventorySnapshot(context: Context): List<String> {
        val snapshot = mutableListOf<String>()
        val camManager = cameraManager ?: (context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager)
        if (camManager != null) {
            try {
                for (cameraId in camManager.cameraIdList) {
                    snapshot.add("cam_$cameraId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting camera snapshot: ${e.message}")
            }
        }
        val audManager = audioManager ?: (context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
        if (audManager != null) {
            try {
                val devices = audManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                for (device in devices) {
                    snapshot.add("mic_${device.id}_${device.type}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting audio snapshot: ${e.message}")
            }
        }
        return snapshot.sorted()
    }

    private fun onDeviceChanged() {
        val context = activeWebViews.values.firstOrNull()?.get()?.context ?: return
        val newSnapshot = getPhysicalInventorySnapshot(context)
        val oldSnapshot = lastGlobalSnapshot
        if (oldSnapshot != null && oldSnapshot == newSnapshot) {
            Log.d(TAG, "No actual change in physical device inventory. Skipping event dispatch.")
            return
        }
        
        // Find which devices were removed and invalidate their mappings
        if (oldSnapshot != null) {
            val removed = oldSnapshot.filter { !newSnapshot.contains(it) }
            for (item in removed) {
                if (item.startsWith("cam_")) {
                    val rawId = item.removePrefix("cam_")
                    WebDeviceIdentityManager.invalidatePhysicalDevice(rawId)
                } else if (item.startsWith("mic_")) {
                    val rawId = item.substringBeforeLast("_")
                    WebDeviceIdentityManager.invalidatePhysicalDevice(rawId)
                }
            }
        }

        lastGlobalSnapshot = newSnapshot
        Log.i(TAG, "Device inventory changed from $oldSnapshot to $newSnapshot. Dispatching devicechange.")

        activeWebViews.forEach { (tabId, ref) ->
            val webView = ref.get()
            if (webView != null) {
                webView.post {
                    webView.evaluateJavascript(
                        "if (navigator.mediaDevices) { navigator.mediaDevices.dispatchEvent(new Event('devicechange')); }",
                        null
                    )
                }
            }
        }
        deviceChangeListeners.values.forEach { it.invoke() }
    }

    private fun handleAudioDeviceDisappearance(removedDevices: Array<out AudioDeviceInfo>?) {
        if (removedDevices == null) return
        for (device in removedDevices) {
            val devId = "mic_${device.id}"
            for ((tabId, devicesMap) in selectedDevices) {
                if (devicesMap["audioinput"] == devId) {
                    Log.w(TAG, "Active microphone $devId disappeared from tab $tabId. Dropping from selection.")
                    devicesMap.remove("audioinput")
                }
            }
        }
    }

    fun enumerateVideoDevices(context: Context): List<WebMediaDeviceInfo> {
        val manager = cameraManager ?: (context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager) ?: return emptyList()
        val list = mutableListOf<WebMediaDeviceInfo>()
        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                val label = when (facing) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "Front Camera (ID $cameraId)"
                    CameraCharacteristics.LENS_FACING_BACK -> "Back Camera (ID $cameraId)"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "External Camera (ID $cameraId)"
                    else -> "Camera (ID $cameraId)"
                }
                list.add(
                    WebMediaDeviceInfo(
                        deviceId = cameraId,
                        kind = "videoinput",
                        label = label,
                        groupId = "camera_group"
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enumerating video devices: ${e.message}", e)
        }
        return list
    }

    fun enumerateAudioDevices(context: Context): List<WebMediaDeviceInfo> {
        val manager = audioManager ?: (context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager) ?: return emptyList()
        val list = mutableListOf<WebMediaDeviceInfo>()
        try {
            val devices = manager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            for (device in devices) {
                val label = when (device.type) {
                    AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Microphone"
                    AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset Microphone"
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth Headset Microphone"
                    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Microphone"
                    else -> device.productName?.toString()?.takeIf { it.isNotEmpty() } ?: "External Microphone"
                }
                list.add(
                    WebMediaDeviceInfo(
                        deviceId = "mic_${device.id}",
                        kind = "audioinput",
                        label = label,
                        groupId = "mic_group"
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enumerating audio devices: ${e.message}", e)
        }
        return list
    }

    fun getDevicesJson(context: Context): String {
        return getDevicesJson(context, "default_tab", "https://localhost")
    }

    fun getDevicesJson(context: Context, tabId: String, origin: String): String {
        val videoDevices = enumerateVideoDevices(context)
        val audioDevices = enumerateAudioDevices(context)
        val all = videoDevices + audioDevices
        val sb = StringBuilder()
        sb.append("[")
        for (i in all.indices) {
            val dev = all[i]
            val opaqueId = WebDeviceIdentityManager.getOpaqueId(tabId, origin, dev.deviceId)
            sb.append("{")
            sb.append("\"deviceId\":\"$opaqueId\",")
            sb.append("\"kind\":\"${dev.kind}\",")
            sb.append("\"label\":\"${dev.label}\",")
            sb.append("\"groupId\":\"${dev.groupId}\"")
            sb.append("}")
            if (i < all.size - 1) {
                sb.append(",")
            }
        }
        sb.append("]")
        return sb.toString()
    }

    fun getSelectedDeviceId(tabId: String, kind: String): String? {
        return selectedDevices[tabId]?.get(kind)
    }

    fun setSelectedDeviceId(tabId: String, kind: String, deviceId: String) {
        selectedDevices.getOrPut(tabId) { ConcurrentHashMap() }[kind] = deviceId
        Log.i(TAG, "Set selected device for tab $tabId: $kind -> $deviceId")
    }
}
