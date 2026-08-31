package com.swift.browser.browserengine

import android.os.SystemClock
import android.util.Log
import com.swift.browser.analyticscore.AnalyticsCore

object PerformanceMonitor {
    private const val TAG = "PerformanceMonitor"

    fun startTimer(key: String) {
        AnalyticsCore.startTimer(key)
    }

    fun stopTimer(key: String): Long {
        val duration = AnalyticsCore.stopTimer(key)
        if (duration >= 0) {
            Log.i(TAG, "[PerformanceMonitor] $key completed in: ${duration}ms")
        }
        return duration
    }

    fun getMemoryUsage(): Long {
        return AnalyticsCore.performanceAnalytics.getCurrentRamUsageMb()
    }

    fun printTelemetry() {
        val memory = getMemoryUsage()
        AnalyticsCore.recordPerformance("ram_telemetry", memory)
        Log.i(TAG, "[PerformanceMonitor] Telemetry -> RAM Utilization: ${memory}MB")
    }
}
