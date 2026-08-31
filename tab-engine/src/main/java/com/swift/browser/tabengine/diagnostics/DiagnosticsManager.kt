package com.swift.browser.tabengine.diagnostics

import android.util.Log

object DiagnosticsManager {
    fun recordFrameTime(timeMs: Long) {
        if (timeMs > 16) {
            Log.w("DiagnosticsManager", "Dropped frame! Took ${timeMs}ms")
        }
    }
    
    fun logEvent(event: String) {
        Log.d("DiagnosticsManager", event)
    }
}
