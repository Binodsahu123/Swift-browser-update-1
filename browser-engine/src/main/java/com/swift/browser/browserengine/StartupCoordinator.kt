package com.swift.browser.browserengine

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class StartupState {
    STARTING,
    CORE_LOADING,
    BROWSER_READY,
    ONBOARDING,
    BACKGROUND_INITIALIZING,
    READY,
    FAILED
}

class StartupCoordinator private constructor() {
    private val _startupState = MutableStateFlow(StartupState.STARTING)
    val startupState: StateFlow<StartupState> = _startupState.asStateFlow()

    private val _startupError = MutableStateFlow<Throwable?>(null)
    val startupError: StateFlow<Throwable?> = _startupError.asStateFlow()

    private var startTimeMs: Long = System.currentTimeMillis()
    private var readyTimeMs: Long = 0L

    val isBlockingSplash: Boolean
        get() = _startupState.value == StartupState.STARTING

    fun onApplicationStarted() {
        startTimeMs = System.currentTimeMillis()
        _startupState.value = StartupState.STARTING
        Log.i(TAG, "StartupCoordinator initialized at $startTimeMs ms")
    }

    fun onFirstFrameRendered() {
        if (_startupState.value == StartupState.STARTING || _startupState.value == StartupState.CORE_LOADING) {
            readyTimeMs = System.currentTimeMillis()
            val duration = readyTimeMs - startTimeMs
            Log.i(TAG, "First UI frame rendered in $duration ms")
            _startupState.value = StartupState.BROWSER_READY
        }
    }

    fun markBackgroundInitializing() {
        if (_startupState.value == StartupState.BROWSER_READY) {
            _startupState.value = StartupState.BACKGROUND_INITIALIZING
        }
    }

    fun markReady() {
        _startupState.value = StartupState.READY
    }

    fun markFailed(throwable: Throwable) {
        Log.e(TAG, "Critical startup failure encountered", throwable)
        _startupError.value = throwable
        _startupState.value = StartupState.FAILED
    }

    fun retryStartup() {
        _startupError.value = null
        _startupState.value = StartupState.STARTING
        startTimeMs = System.currentTimeMillis()
    }

    companion object {
        private const val TAG = "StartupCoordinator"
        val instance: StartupCoordinator by lazy { StartupCoordinator() }
    }
}
