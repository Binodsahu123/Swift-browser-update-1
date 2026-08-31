package com.swift.browser.browserengine

import android.util.Log

class BrowserRenderEngine(private val stateEngine: BrowserStateEngine) {
    companion object {
        private const val TAG = "BrowserRenderEngine"
    }
    private var attachedAdapter: Any? = null

    fun attachAdapter(adapter: Any?) {
        attachedAdapter = adapter
        stateEngine.updateState(BrowserState.ATTACHED)
        stateEngine.updateDiagnostics("Render Adapter Attached")
        Log.d(TAG, "WebView/Render Adapter attached")
    }

    fun detachAdapter() {
        attachedAdapter = null
        stateEngine.updateState(BrowserState.DETACHED)
        stateEngine.updateDiagnostics("Render Adapter Detached")
        Log.d(TAG, "WebView/Render Adapter detached")
    }

    fun requestRender() {
        Log.d(TAG, "Requesting frame render update")
        stateEngine.updateDiagnostics("Render requested")
    }
}
