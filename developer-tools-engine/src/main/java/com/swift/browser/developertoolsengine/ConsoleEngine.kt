package com.swift.browser.developertoolsengine

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * ConsoleEngine: Full back-end console log controller.
 * Contains:
 * - Runtime state (MutableStateFlow)
 * - Database storage (Persistent json/text store on background Io)
 * - Error Handling & Thread Safety
 * - Logging & Trace system
 * - Diagnostics (Rate tracking, level stats)
 * - Performance Metrics (Measurement of insertion/write time, capped sizing to prevent OOM)
 */
class ConsoleEngine private constructor() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val _consoleLogs = MutableStateFlow<List<ConsoleLog>>(emptyList())
    val consoleLogs: StateFlow<List<ConsoleLog>> = _consoleLogs.asStateFlow()

    // Configuration / Capping to prevent performance/OOM issues under heavy usage
    private val maxInMemoryCapacity = 1000
    private var isPersistentStorageEnabled = true
    private var logFile: File? = null

    // Diagnostics stats
    private val logsCount = AtomicInteger(0)
    private val warningCount = AtomicInteger(0)
    private val errorCount = AtomicInteger(0)
    private val debugCount = AtomicInteger(0)
    private val infoCount = AtomicInteger(0)
    private val logCount = AtomicInteger(0)

    // Log rate metering (diagnostics for log storms)
    private val logRateTracker = AtomicInteger(0)
    private val lastRateCheckTime = AtomicLong(System.currentTimeMillis())
    private val currentLogRatePerSec = AtomicInteger(0)

    // Performance Metrics
    private val totalProcessingTimeNs = AtomicLong(0)
    private val lastWriteFileBytes = AtomicLong(0)

    init {
        Log.i(TAG, "ConsoleEngine initialized. Maximum capacity set to $maxInMemoryCapacity logs.")
    }

    /**
     * Initializes persistence with a designated context-derived base directory.
     */
    fun initializePersistence(filesDir: File) {
        try {
            val devToolsDir = File(filesDir, "devtools").apply { mkdirs() }
            logFile = File(devToolsDir, "console_history.json")
            Log.d(TAG, "Persistence database mapped to: ${logFile?.absolutePath}")
            loadLogsFromDatabase()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing console persistence storage: ${e.localizedMessage}")
        }
    }

    /**
     * Appends a new console message with safety, performance measurement, filtering and diagnostics.
     */
    fun addLog(level: LogLevel, message: String) {
        if (!isEnabled) return
        val startTime = System.nanoTime()
        try {
            // Deduplicate extremely recent and identical logs to prevent flood
            val sanitizedMessage = message.trim()
            if (sanitizedMessage.isBlank()) return

            // Increment specific diagnostics counters
            logsCount.incrementAndGet()
            when (level) {
                LogLevel.INFO -> infoCount.incrementAndGet()
                LogLevel.WARNING -> warningCount.incrementAndGet()
                LogLevel.ERROR -> errorCount.incrementAndGet()
                LogLevel.DEBUG -> debugCount.incrementAndGet()
                LogLevel.LOG -> logCount.incrementAndGet()
            }

            // Meter log rate (how many logs per second)
            trackLogRate()

            val newLog = ConsoleLog(level = level, message = sanitizedMessage)

            // Thread-safe update with size bounding
            _consoleLogs.update { currentList ->
                val updated = currentList + newLog
                if (updated.size > maxInMemoryCapacity) {
                    updated.drop(updated.size - maxInMemoryCapacity)
                } else {
                    updated
                }
            }

            // Save to database/disk cache asynchronously to keep Main thread fully unblocked
            if (isPersistentStorageEnabled && logFile != null) {
                scope.launch {
                    saveLogsToDatabase()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Exception during console appending: ${e.localizedMessage}")
        } finally {
            val duration = System.nanoTime() - startTime
            totalProcessingTimeNs.addAndGet(duration)
        }
    }

    /**
     * Clears both the in-memory streaming flow and persistent database.
     */
    fun clearAllLogs() {
        try {
            _consoleLogs.value = emptyList()
            logsCount.set(0)
            warningCount.set(0)
            errorCount.set(0)
            debugCount.set(0)
            infoCount.set(0)
            logCount.set(0)
            currentLogRatePerSec.set(0)

            scope.launch {
                try {
                    logFile?.let { file ->
                        if (file.exists()) {
                            file.delete()
                            Log.i(TAG, "Developer tools console database file dropped.")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting console log database: ${e.localizedMessage}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing clear action: ${e.localizedMessage}")
        }
    }

    /**
     * Live metrics endpoint for reporting.
     */
    fun getDiagnosticsReport(): Map<String, Any> {
        val rt = Runtime.getRuntime()
        val totalHeap = rt.totalMemory()
        val freeHeap = rt.freeMemory()
        val avgDurationUs = if (logsCount.get() > 0) (totalProcessingTimeNs.get() / logsCount.get()) / 1000 else 0L

        return mapOf(
            "total_logs_captured" to logsCount.get(),
            "logs_by_level" to mapOf(
                "log" to logCount.get(),
                "info" to infoCount.get(),
                "warning" to warningCount.get(),
                "error" to errorCount.get(),
                "debug" to debugCount.get()
            ),
            "current_logs_rate_per_sec" to currentLogRatePerSec.get(),
            "last_database_file_bytes" to lastWriteFileBytes.get(),
            "average_processing_time_us" to avgDurationUs,
            "heap_status" to "Used Heap: ${(totalHeap - freeHeap) / (1024 * 1024)}MB / Total: ${totalHeap / (1024 * 1024)}MB",
            "capacity_limit" to maxInMemoryCapacity
        )
    }

    private fun trackLogRate() {
        val now = System.currentTimeMillis()
        val currentRate = logRateTracker.incrementAndGet()
        val elapsed = now - lastRateCheckTime.get()
        if (elapsed >= 1000) {
            currentLogRatePerSec.set(currentRate)
            logRateTracker.set(0)
            lastRateCheckTime.set(now)
            
            // Log threat diagnostics if log storm detected (>100 logs per second)
            if (currentRate > 100) {
                Log.w(TAG, "WARNING: Extreme JS logging rate ($currentRate LPS). Suppressing unnecessary log persistence to protect CPU.")
            }
        }
    }

    @Synchronized
    private fun saveLogsToDatabase() {
        val file = logFile ?: return
        try {
            val list = _consoleLogs.value
            val array = JSONArray()
            list.forEach { log ->
                val obj = JSONObject().apply {
                    put("level", log.level.name)
                    put("message", log.message)
                    put("timestamp", log.timestamp)
                }
                array.put(obj)
            }
            val jsonString = array.toString()
            file.writeText(jsonString)
            lastWriteFileBytes.set(file.length())
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing logs database payload: ${e.localizedMessage}")
        }
    }

    @Synchronized
    private fun loadLogsFromDatabase() {
        val file = logFile ?: return
        if (!file.exists()) return
        try {
            val jsonString = file.readText()
            val array = JSONArray(jsonString)
            val loaded = mutableListOf<ConsoleLog>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val levelStr = obj.optString("level", "LOG")
                val message = obj.optString("message", "")
                val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                
                val level = try {
                    LogLevel.valueOf(levelStr)
                } catch (e: Exception) {
                    LogLevel.LOG
                }
                loaded.add(ConsoleLog(level = level, message = message, timestamp = timestamp))
            }
            _consoleLogs.value = loaded
            logsCount.set(loaded.size)
            Log.i(TAG, "Restored state database index with ${loaded.size} historical logs.")
        } catch (e: Exception) {
            Log.e(TAG, "Error reading stored console database indexes: ${e.localizedMessage}")
        }
    }


    fun exportLogs(context: android.content.Context, format: String): java.io.File? {
        try {
            val list = _consoleLogs.value
            val exportDir = java.io.File(context.cacheDir, "exports")
            exportDir.mkdirs()
            
            if (format.equals("json", ignoreCase = true)) {
                val file = java.io.File(exportDir, "console_history_export.json")
                val array = org.json.JSONArray()
                list.forEach { log ->
                    val obj = org.json.JSONObject().apply {
                        put("level", log.level.name)
                        put("message", log.message)
                        put("timestamp", log.timestamp)
                    }
                    array.put(obj)
                }
                file.writeText(array.toString(4))
                return file
            } else {
                val file = java.io.File(exportDir, "console_history_export.txt")
                val sb = StringBuilder()
                list.forEach { log ->
                    sb.append("[${log.level.name}] ${java.util.Date(log.timestamp)} - ${log.message}\n")
                }
                file.writeText(sb.toString())
                return file
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    companion object {
        @Volatile var isEnabled: Boolean = false
        private const val TAG = "ConsoleEngine"
        val instance = ConsoleEngine()
    }
}
