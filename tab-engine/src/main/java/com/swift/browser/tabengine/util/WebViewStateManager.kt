package com.swift.browser.tabengine.util

import android.content.Context
import android.os.Bundle
import android.os.Parcel
import android.util.Log
import android.webkit.WebView
import java.io.File

object WebViewStateManager {
    fun saveWebViewStateToFile(context: Context, tabId: String, webView: WebView) {
        try {
            val bundle = Bundle()
            webView.saveState(bundle)
            val file = File(context.cacheDir, "webview_state_$tabId.bin")
            val parcel = Parcel.obtain()
            bundle.writeToParcel(parcel, 0)
            val bytes = parcel.marshall()
            parcel.recycle()
            file.writeBytes(bytes)
            Log.i("WebViewStateManager", "Saved state file for tab $tabId: ${file.length()} bytes")
        } catch (e: Exception) {
            Log.e("WebViewStateManager", "Failed to save state file for tab $tabId", e)
        }
    }

    fun restoreWebViewStateFromFile(context: Context, tabId: String, webView: WebView): Boolean {
        try {
            val file = File(context.cacheDir, "webview_state_$tabId.bin")
            if (!file.exists()) return false
            val bytes = file.readBytes()
            val parcel = Parcel.obtain()
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            val bundle = Bundle()
            bundle.readFromParcel(parcel)
            parcel.recycle()
            webView.restoreState(bundle)
            Log.i("WebViewStateManager", "Restored state file for tab $tabId successfully")
            return true
        } catch (e: Exception) {
            Log.e("WebViewStateManager", "Failed to restore state file for tab $tabId", e)
            return false
        }
    }
}
