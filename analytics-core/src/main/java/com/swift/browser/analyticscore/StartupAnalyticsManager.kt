package com.swift.browser.analyticscore

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StartupAnalyticsManager {
    private var bootStartTimeMs: Long = SystemClock.elapsedRealtime()
    
    private val _startupTraces = MutableStateFlow<List<StartupTrace>>(emptyList())
    val startupTraces: StateFlow<List<StartupTrace>> = _startupTraces.asStateFlow()

    private val _lastColdStartupMs = MutableStateFlow(0L)
    val lastColdStartupMs: StateFlow<Long> = _lastColdStartupMs.asStateFlow()

    fun markAppLaunchStart() {
        bootStartTimeMs = SystemClock.elapsedRealtime()
    }

    fun recordStartupPhase(
        phaseName: String,
        startupType: StartupType = StartupType.COLD,
        durationMs: Long = SystemClock.elapsedRealtime() - bootStartTimeMs
    ) {
        val trace = StartupTrace(
            startupType = startupType,
            phaseName = phaseName,
            durationMs = durationMs,
            totalBootDurationMs = SystemClock.elapsedRealtime() - bootStartTimeMs
        )

        if (startupType == StartupType.COLD && phaseName == "COMPLETE_BOOT") {
            _lastColdStartupMs.value = trace.totalBootDurationMs
        }

        val current = _startupTraces.value.toMutableList()
        if (current.size >= 100) {
            current.removeAt(0)
        }
        current.add(trace)
        _startupTraces.value = current
    }

    fun clear() {
        _startupTraces.value = emptyList()
    }
}
