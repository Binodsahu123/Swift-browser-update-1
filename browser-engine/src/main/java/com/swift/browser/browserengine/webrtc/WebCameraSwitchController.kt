package com.swift.browser.browserengine.webrtc

import android.content.Context
import android.util.Log
import android.webkit.WebView

object WebCameraSwitchController {
    private const val TAG = "WebCameraSwitchController"

    /**
     * Switches the active camera device.
     */
    fun switchCamera(context: Context, webView: WebView, tabId: String, targetCameraId: String, callback: ((Boolean) -> Unit)? = null) {
        Log.i(TAG, "switchCamera: tabId=$tabId, targetCameraId=$targetCameraId")
        WebMediaSourceManager.switchCamera(context, webView, tabId, targetCameraId) { success, error ->
            if (!success) {
                Log.e(TAG, "switchCamera failed: $error")
            }
            callback?.invoke(success)
        }
    }

    /**
     * Toggles between front and back camera.
     */
    fun toggleCamera(context: Context, webView: WebView, tabId: String, callback: ((Boolean) -> Unit)? = null) {
        WebMediaSourceManager.toggleCamera(context, webView, tabId) { success, error ->
            if (!success) {
                Log.e(TAG, "toggleCamera failed: $error")
            }
            callback?.invoke(success)
        }
    }
}
