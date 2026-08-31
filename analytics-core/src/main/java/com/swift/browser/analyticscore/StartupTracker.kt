package com.swift.browser.analyticscore

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object StartupTracker {
    private const val TAG = "StartupTracker"
    private var appContext: Context? = null
    private var logFile: File? = null

    data class TraceEntry(
        val stage: String,
        val timestamp: Long = System.currentTimeMillis(),
        val threadName: String = Thread.currentThread().name,
        val className: String,
        val methodName: String,
        val success: Boolean,
        val error: Throwable? = null,
        val extra: String? = null
    ) {
        fun toLogLine(): String {
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
            val status = if (success) "SUCCESS" else "FAILED"
            val errStr = if (error != null) "\nError: ${Log.getStackTraceString(error)}" else ""
            val extraStr = if (extra != null) " [$extra]" else ""
            return "[$dateStr] [Thread:$threadName] [Stage:$stage] [Class:$className::$methodName] [Status:$status]$extraStr$errStr"
        }
    }

    private val entries = CopyOnWriteArrayList<TraceEntry>()

    fun init(context: Context) {
        appContext = context.applicationContext
        try {
            val dir = context.applicationContext.filesDir
            if (!dir.exists()) {
                dir.mkdirs()
            }
            logFile = File(dir, "startup_trace.log")
            recordStage(
                stage = "APPLICATION_START",
                className = "BrowserApplication",
                methodName = "onCreate",
                success = true
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to init StartupTracker persistence", e)
        }
    }

    fun recordStage(
        stage: String,
        className: String,
        methodName: String,
        success: Boolean = true,
        error: Throwable? = null,
        extra: String? = null
    ) {
        val entry = TraceEntry(
            stage = stage,
            className = className,
            methodName = methodName,
            success = success,
            error = error,
            extra = extra
        )
        entries.add(entry)
        val logLine = entry.toLogLine()
        if (success) {
            Log.i(TAG, logLine)
        } else {
            Log.e(TAG, logLine, error)
        }
        persistEntry(logLine)
    }

    private val logQueue = java.util.concurrent.ArrayBlockingQueue<String>(256)
    private val ioExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "StartupTracker-IO").apply { isDaemon = true }
    }

    init {
        ioExecutor.execute {
            while (!Thread.currentThread().isInterrupted) {
                try {
                    val line = logQueue.take()
                    val file = logFile ?: appContext?.let { File(it.filesDir, "startup_trace.log") }
                    if (file != null) {
                        FileWriter(file, true).use { fw ->
                            PrintWriter(fw).use { pw ->
                                pw.println(line)
                            }
                        }
                    }
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } catch (t: Throwable) {
                    // Ignore persistence errors
                }
            }
        }
    }

    private fun persistEntry(line: String) {
        logQueue.offer(line)
    }

    fun getTraceHistory(): List<TraceEntry> = entries.toList()
}
