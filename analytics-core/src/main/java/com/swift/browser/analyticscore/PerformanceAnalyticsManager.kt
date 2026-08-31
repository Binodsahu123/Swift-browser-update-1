package com.swift.browser.analyticscore

import android.os.Debug
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

class PerformanceAnalyticsManager(private val maxCapacity: Int = 200) {
    private val timers = ConcurrentHashMap<String, Long>()
    private val lock = Any()

    private val buffer = ArrayDeque<PerformanceMetricRecord>(maxCapacity)
    private var totalDurationSum = 0L
    private var totalRecordsCount = 0L
    private var jankCount = 0

    private val _records = MutableStateFlow<List<PerformanceMetricRecord>>(emptyList())
    val records: StateFlow<List<PerformanceMetricRecord>> = _records.asStateFlow()

    private val _summary = MutableStateFlow(PerformanceSummary())
    val summary: StateFlow<PerformanceSummary> = _summary.asStateFlow()

    private val _latestCpuLoad = MutableStateFlow<Float?>(null)
    val latestCpuLoad: StateFlow<Float?> = _latestCpuLoad.asStateFlow()

    fun startTimer(key: String) {
        timers[key] = SystemClock.elapsedRealtime()
    }

    fun stopTimer(key: String, additionalMetadata: Map<String, Any> = emptyMap()): Long {
        val startTime = timers.remove(key) ?: return -1L
        val duration = SystemClock.elapsedRealtime() - startTime
        recordMetric(key = key, durationMs = duration)
        return duration
    }

    fun recordMetric(
        key: String,
        durationMs: Long,
        jvmHeapUsedMb: Long = getJvmHeapUsedMb(),
        cpuUsagePercent: Float? = _latestCpuLoad.value,
        fps: Int? = null, // null indicates NOT_MEASURED (no fabricated 60 FPS)
        operationId: String? = null,
        tabId: String? = null
    ) {
        val record = PerformanceMetricRecord(
            key = key,
            durationMs = durationMs,
            jvmHeapUsedMb = jvmHeapUsedMb,
            cpuUsagePercent = cpuUsagePercent,
            fps = fps,
            operationId = operationId,
            tabId = tabId
        )

        synchronized(lock) {
            totalDurationSum += durationMs
            totalRecordsCount++

            if (fps != null && fps < 30) {
                jankCount++
            }

            if (buffer.size >= maxCapacity) {
                val oldest = buffer.removeFirst()
                totalDurationSum -= oldest.durationMs
            }
            buffer.addLast(record)

            val avgDuration = if (buffer.isNotEmpty()) totalDurationSum / buffer.size else 0L

            _summary.value = PerformanceSummary(
                jvmHeapUsedMb = jvmHeapUsedMb,
                processMemoryMb = getProcessMemoryMb(),
                cpuUsagePercent = cpuUsagePercent,
                measuredFps = fps,
                jankCount = jankCount,
                recentDurationAverageMs = avgDuration,
                lastRecordedKey = key,
                lastDurationMs = durationMs
            )

            _records.value = buffer.toList()
        }
    }

    fun recordFrameMetric(frameDurationMs: Long, measuredFps: Int?, isJank: Boolean = false) {
        if (isJank) {
            synchronized(lock) { jankCount++ }
        }
        recordMetric(
            key = "frame_render",
            durationMs = frameDurationMs,
            fps = measuredFps
        )
    }

    fun updateCpuLoad(percent: Float) {
        _latestCpuLoad.value = percent
    }

    /**
     * JVM Heap Used (totalMemory - freeMemory).
     * Note: This measures JVM heap allocation only, NOT total device RAM or process PSS.
     */
    fun getJvmHeapUsedMb(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    }

    @Deprecated("Renamed to getJvmHeapUsedMb to accurately distinguish JVM heap from system RAM", ReplaceWith("getJvmHeapUsedMb()"))
    fun getCurrentRamUsageMb(): Long = getJvmHeapUsedMb()

    /**
     * Process PSS memory in MB if available.
     */
    fun getProcessMemoryMb(): Long {
        return try {
            val pssKb = Debug.getPss()
            if (pssKb > 0) pssKb / 1024 else getJvmHeapUsedMb()
        } catch (_: Exception) {
            getJvmHeapUsedMb()
        }
    }

    fun getRecordsSnapshot(): List<PerformanceMetricRecord> {
        return synchronized(lock) { buffer.toList() }
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            totalDurationSum = 0L
            totalRecordsCount = 0L
            jankCount = 0
            _summary.value = PerformanceSummary()
            _records.value = emptyList()
            timers.clear()
        }
    }
}
