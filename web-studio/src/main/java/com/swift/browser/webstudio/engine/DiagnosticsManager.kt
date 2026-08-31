package com.swift.browser.webstudio.engine

import android.util.Log

class DiagnosticsManager {
    private val TAG = "WebStudioDiagnostics"
    private val logs = mutableListOf<String>()

    fun logEvent(event: String) {
        val timestamp = System.currentTimeMillis()
        val log = "[$timestamp] EVENT: $event"
        logs.add(log)
        Log.i(TAG, log)
    }

    fun logError(message: String, e: Throwable? = null) {
        val timestamp = System.currentTimeMillis()
        val log = "[$timestamp] ERROR: $message - ${e?.message}"
        logs.add(log)
        Log.e(TAG, log, e)
    }
    
    fun getDiagnosticReport(): String {
        return logs.joinToString("\n")
    }
}
