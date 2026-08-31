package com.swift.browser.analyticscore

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InternalMetricsManager {
    private val initTimeMs = SystemClock.elapsedRealtime()

    private val _snapshot = MutableStateFlow(takeSnapshot())
    val snapshot: StateFlow<InternalPerformanceSnapshot> = _snapshot.asStateFlow()

    fun refreshSnapshot(): InternalPerformanceSnapshot {
        val newSnap = takeSnapshot()
        _snapshot.value = newSnap
        return newSnap
    }

    private fun takeSnapshot(): InternalPerformanceSnapshot {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val allocatedMb = (totalMemory - freeMemory) / (1024 * 1024)
        val maxMb = maxMemory / (1024 * 1024)
        val freeMb = freeMemory / (1024 * 1024)
        val activeThreads = Thread.activeCount()
        val uptime = SystemClock.elapsedRealtime() - initTimeMs

        return InternalPerformanceSnapshot(
            heapAllocatedMb = allocatedMb,
            maxMemoryMb = maxMb,
            freeMemoryMb = freeMb,
            activeThreadCount = activeThreads,
            uptimeMs = uptime
        )
    }
}
