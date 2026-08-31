package com.swift.browser.analyticscore

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

class EngineDiagnosticsManager(private val maxCapacity: Int = 300) {
    companion object {
        private const val TAG = "EngineDiagnostics"

        fun sanitizeMessage(message: String): String {
            if (message.isBlank()) return message
            var sanitized = message

            // 1. Sanitize sensitive query params (token, auth, key, password, session, access_token, etc.)
            val queryPattern = Regex("""(?i)([?&](?:token|auth|key|password|session|access_token|secret|bearer|ticket)=)[^&\s"'>]+""")
            sanitized = queryPattern.replace(sanitized, "$1[REDACTED]")

            // 2. Redact Authorization / Cookie / Password headers or patterns
            sanitized = sanitized.replace(Regex("""(?i)(cookie\s*:\s*)[^\r\n;]+"""), "$1[REDACTED]")
            sanitized = sanitized.replace(Regex("""(?i)(authorization\s*:\s*)[^\r\n;]+"""), "$1[REDACTED]")
            sanitized = sanitized.replace(Regex("""(?i)(bearer\s+)[a-zA-Z0-9_\-\.]+"""), "$1[REDACTED]")
            sanitized = sanitized.replace(Regex("""(?i)(password\s*=\s*)[^&\s]+"""), "$1[REDACTED]")

            return sanitized
        }
    }

    private val lock = Any()
    private val buffer = ArrayDeque<EngineDiagnosticTrace>(maxCapacity)
    private val recentErrorsBuffer = ArrayDeque<EngineDiagnosticTrace>(20)
    private val engineHealth = mutableMapOf<String, Int>()
    private var totalEvents = 0L
    private var errorCount = 0L
    private var warningCount = 0L

    private val _summary = MutableStateFlow(DiagnosticsSummary())
    val summary: StateFlow<DiagnosticsSummary> = _summary.asStateFlow()

    private val _diagnosticTraces = MutableStateFlow<List<EngineDiagnosticTrace>>(emptyList())
    val diagnosticTraces: StateFlow<List<EngineDiagnosticTrace>> = _diagnosticTraces.asStateFlow()

    fun logDiagnostic(
        engineName: String,
        module: String,
        function: String,
        reason: String,
        severity: DiagnosticSeverity = DiagnosticSeverity.INFO,
        operationId: String? = null,
        tabId: String? = null
    ) {
        val cleanReason = sanitizeMessage(reason)
        val trace = EngineDiagnosticTrace(
            engineName = engineName,
            module = module,
            function = function,
            message = cleanReason,
            severity = severity,
            operationId = operationId,
            tabId = tabId
        )

        try {
            when (severity) {
                DiagnosticSeverity.INFO -> Log.i(TAG, "[$engineName] [$module::$function] $cleanReason")
                DiagnosticSeverity.WARNING -> Log.w(TAG, "[$engineName] [$module::$function] WARNING: $cleanReason")
                DiagnosticSeverity.ERROR, DiagnosticSeverity.CRITICAL -> Log.e(TAG, "[$engineName] [$module::$function] ERROR: $cleanReason")
            }
        } catch (_: Throwable) {
            // Safe fallback during pure JVM unit tests where android.util.Log is not mocked
        }

        synchronized(lock) {
            totalEvents++
            if (severity == DiagnosticSeverity.ERROR || severity == DiagnosticSeverity.CRITICAL) {
                errorCount++
                if (recentErrorsBuffer.size >= 20) {
                    recentErrorsBuffer.removeFirst()
                }
                recentErrorsBuffer.addLast(trace)
                engineHealth[engineName] = ((engineHealth[engineName] ?: 100) - 10).coerceAtLeast(0)
            } else if (severity == DiagnosticSeverity.WARNING) {
                warningCount++
                engineHealth[engineName] = ((engineHealth[engineName] ?: 100) - 2).coerceAtLeast(0)
            } else {
                engineHealth[engineName] = ((engineHealth[engineName] ?: 100) + 1).coerceAtMost(100)
            }

            if (buffer.size >= maxCapacity) {
                buffer.removeFirst()
            }
            buffer.addLast(trace)

            _summary.value = DiagnosticsSummary(
                totalEventsCount = totalEvents,
                errorCount = errorCount,
                warningCount = warningCount,
                lastEvent = trace,
                recentErrors = recentErrorsBuffer.toList(),
                engineHealthMap = engineHealth.toMap()
            )

            _diagnosticTraces.value = buffer.toList()
        }
    }

    fun logError(
        engineName: String,
        module: String,
        function: String,
        error: String,
        operationId: String? = null,
        tabId: String? = null
    ) {
        logDiagnostic(
            engineName = engineName,
            module = module,
            function = function,
            reason = error,
            severity = DiagnosticSeverity.ERROR,
            operationId = operationId,
            tabId = tabId
        )
    }

    fun getTracesSnapshot(): List<EngineDiagnosticTrace> {
        return synchronized(lock) {
            buffer.toList()
        }
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            recentErrorsBuffer.clear()
            engineHealth.clear()
            totalEvents = 0L
            errorCount = 0L
            warningCount = 0L
            _summary.value = DiagnosticsSummary()
            _diagnosticTraces.value = emptyList()
        }
    }
}
