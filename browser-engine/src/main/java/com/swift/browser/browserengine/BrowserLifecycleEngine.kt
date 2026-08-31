package com.swift.browser.browserengine

import android.content.Context
import android.util.Log

class BrowserLifecycleEngine(private val stateEngine: BrowserStateEngine) {
    companion object {
        private const val TAG = "BrowserLifecycleEngine"
    }
    private var isInitialized = false

    fun initialize(context: Context? = null) {
        if (isInitialized) return
        Log.d(TAG, "Initializing Browser Engine Core Lifecycle")
        stateEngine.updateState(BrowserState.IDLE)
        stateEngine.updateDiagnostics("Browser Lifecycle Initialized")
        isInitialized = true
    }

    fun onPause() {
        Log.d(TAG, "Browser Lifecycle Paused")
        stateEngine.updateDiagnostics("Lifecycle Paused")
    }

    fun onResume() {
        Log.d(TAG, "Browser Lifecycle Resumed")
        stateEngine.updateDiagnostics("Lifecycle Resumed")
    }

    fun onDestroy() {
        Log.d(TAG, "Browser Lifecycle Destroyed")
        stateEngine.updateState(BrowserState.DETACHED)
        stateEngine.updateDiagnostics("Lifecycle Destroyed")
    }

    fun clearCache(context: Context) {
        try {
            android.webkit.WebStorage.getInstance().deleteAllData()
            context.cacheDir.deleteRecursively()
            Log.d(TAG, "Browser engine cache cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing browser engine cache: ${e.message}")
        }
    }
}
