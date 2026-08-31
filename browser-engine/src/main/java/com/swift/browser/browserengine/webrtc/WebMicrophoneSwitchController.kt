package com.swift.browser.browserengine.webrtc

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
import android.webkit.WebView

object WebMicrophoneSwitchController {
    private const val TAG = "WebMicrophoneSwitchController"

    /**
     * Switches the active microphone device.
     */
    fun switchMicrophone(context: Context, webView: WebView, tabId: String, targetMicId: String, callback: ((Boolean) -> Unit)? = null) {
        Log.i(TAG, "switchMicrophone: tabId=$tabId, targetMicId=$targetMicId")

        // 1. Perform native AudioManager routing where applicable
        val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager != null) {
            try {
                val audioDevices = WebMediaDeviceManager.enumerateAudioDevices(context)
                val targetDevice = audioDevices.firstOrNull { it.deviceId == targetMicId || it.deviceId == "mic_$targetMicId" }
                if (targetDevice != null) {
                    val actualIdStr = targetDevice.deviceId.removePrefix("mic_")
                    val actualId = actualIdStr.toIntOrNull()
                    if (actualId != null) {
                        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                        val targetInput = devices.firstOrNull { device -> device.id == actualId }
                        if (targetInput != null) {
                            Log.i(TAG, "Found target native microphone: ${targetInput.productName}, type: ${targetInput.type}")

                            val isBluetooth = targetInput.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                            if (isBluetooth) {
                                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                                audioManager.startBluetoothSco()
                                audioManager.isBluetoothScoOn = true
                                Log.i(TAG, "Bluetooth SCO started for SCO-based input route.")
                            } else {
                                if (audioManager.isBluetoothScoOn) {
                                    audioManager.stopBluetoothSco()
                                    audioManager.isBluetoothScoOn = false
                                    Log.i(TAG, "Bluetooth SCO stopped.")
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error managing AudioManager routing: ${e.message}", e)
            }
        }

        // 2. Delegate JS track switching and success state tracking to WebMediaSourceManager
        WebMediaSourceManager.switchMicrophone(context, webView, tabId, targetMicId) { success, error ->
            if (!success) {
                Log.e(TAG, "switchMicrophone failed: $error")
            }
            callback?.invoke(success)
        }
    }

    /**
     * Set speakerphone state natively without breaking other audio routes.
     */
    fun toggleSpeakerphone(context: Context, enable: Boolean) {
        val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = enable
            Log.i(TAG, "Speakerphone state set to: $enable")
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling speakerphone state: ${e.message}", e)
        }
    }
}
