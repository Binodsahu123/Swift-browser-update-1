package com.swift.browser.analyticscore

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CrashAnalyticsManager {
    companion object {
        private const val TAG = "CrashAnalytics"
    }

    private val _crashReports = MutableStateFlow<List<CrashReport>>(emptyList())
    val crashReports: StateFlow<List<CrashReport>> = _crashReports.asStateFlow()

    private val _fatalCrashCount = MutableStateFlow(0)
    val fatalCrashCount: StateFlow<Int> = _fatalCrashCount.asStateFlow()

    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    fun attachUncaughtExceptionHandler() {
        if (previousHandler != null) return
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            recordCrash(throwable, "Uncaught exception on thread ${thread.name}", isFatal = true)
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    fun recordCrash(throwable: Throwable, message: String, isFatal: Boolean = false) {
        val stackTrace = sanitizeCrashText(Log.getStackTraceString(throwable))
        val cleanMessage = sanitizeCrashText(message)
        val report = CrashReport(
            exceptionClass = throwable.javaClass.simpleName,
            message = cleanMessage,
            stackTrace = stackTrace,
            isFatal = isFatal
        )

        Log.e(TAG, "Crash Analytics Captured [fatal=$isFatal]: $cleanMessage", throwable)

        if (isFatal) {
            _fatalCrashCount.value += 1
        }

        val current = _crashReports.value.toMutableList()
        if (current.size >= 100) {
            current.removeAt(0)
        }
        current.add(report)
        _crashReports.value = current
    }

    fun recordError(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            recordCrash(throwable, message, isFatal = false)
        } else {
            val cleanMessage = sanitizeCrashText(message)
            val report = CrashReport(
                exceptionClass = "RecordedError",
                message = cleanMessage,
                stackTrace = "",
                isFatal = false
            )
            val current = _crashReports.value.toMutableList()
            if (current.size >= 100) {
                current.removeAt(0)
            }
            current.add(report)
            _crashReports.value = current
        }
    }

    fun sanitizeCrashText(input: String): String {
        if (input.isBlank()) return input
        var sanitized = input
        // 1. Sanitize sensitive auth parameters and headers first
        sanitized = EngineDiagnosticsManager.sanitizeMessage(sanitized)
        // 2. Scrub email addresses
        sanitized = sanitized.replace(Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,6}"""), "[REDACTED_EMAIL]")
        // 3. Scrub credentials/token query params
        sanitized = sanitized.replace(Regex("""(?i)(key|token|auth|password)=[^&\s]+"""), "$1=[REDACTED]")
        // 4. Strip remaining query params from URLs
        sanitized = sanitized.replace(Regex("""(https?://[^\s/?#]+[^\s?#]*)\?[^\s]*""")) { matchResult ->
            matchResult.groupValues[1]
        }
        return sanitized
    }

    fun clear() {
        _crashReports.value = emptyList()
    }
}
