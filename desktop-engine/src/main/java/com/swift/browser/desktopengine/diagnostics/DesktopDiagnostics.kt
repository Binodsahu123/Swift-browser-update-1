package com.swift.browser.desktopengine.diagnostics

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object DesktopDiagnostics {
    private const val TAG = "DesktopDiagnostics"
    private val _events = MutableSharedFlow<DesktopModeEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<DesktopModeEvent> = _events.asSharedFlow()

    fun recordEvent(event: DesktopModeEvent) {
        Log.d(TAG, "DesktopEvent: $event")
        _events.tryEmit(event)
    }

    fun recordNavigationStarted(tabId: String, url: String) {
        Log.d(TAG, "NavigationStarted: tabId=$tabId, url=$url")
    }
}
