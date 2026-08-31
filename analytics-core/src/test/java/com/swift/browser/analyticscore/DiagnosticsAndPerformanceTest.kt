package com.swift.browser.analyticscore

import org.junit.Assert.*
import org.junit.Test

class DiagnosticsAndPerformanceTest {

    @Test
    fun testDiagnosticsBufferBoundingAndEviction() {
        val manager = EngineDiagnosticsManager(maxCapacity = 50)
        
        for (i in 1..100) {
            manager.logDiagnostic(
                engineName = "test_engine",
                module = "test_mod",
                function = "testFunc",
                reason = "Event number $i",
                severity = DiagnosticSeverity.INFO
            )
        }

        val traces = manager.getTracesSnapshot()
        assertEquals(50, traces.size)
        // Oldest 50 evicted, newest 50 remaining: 51 to 100
        assertEquals("Event number 51", traces.first().message)
        assertEquals("Event number 100", traces.last().message)
        assertEquals(100L, manager.summary.value.totalEventsCount)
    }

    @Test
    fun testSensitiveDataSanitizationInDiagnostics() {
        val rawTokenUrl = "https://example.com/api?access_token=secret_12345&user=john"
        val sanitized = EngineDiagnosticsManager.sanitizeMessage("Navigating to $rawTokenUrl with Cookie: session_id=xyz789")

        assertFalse("Token must not be present in sanitized output", sanitized.contains("secret_12345"))
        assertTrue("Token query param must be redacted", sanitized.contains("[REDACTED]"))
        assertFalse("Cookie header must not be exposed", sanitized.contains("session_id=xyz789"))
    }

    @Test
    fun testDiagnosticsSummaryTracking() {
        val manager = EngineDiagnosticsManager(maxCapacity = 10)
        manager.logDiagnostic("engine_a", "mod", "f", "info", DiagnosticSeverity.INFO)
        manager.logDiagnostic("engine_a", "mod", "f", "warning", DiagnosticSeverity.WARNING)
        manager.logDiagnostic("engine_a", "mod", "f", "error 1", DiagnosticSeverity.ERROR)
        manager.logDiagnostic("engine_a", "mod", "f", "error 2", DiagnosticSeverity.ERROR)

        val summary = manager.summary.value
        assertEquals(4L, summary.totalEventsCount)
        assertEquals(2L, summary.errorCount)
        assertEquals(1L, summary.warningCount)
        assertEquals("error 2", summary.lastEvent?.message)
        assertEquals(2, summary.recentErrors.size)
    }

    @Test
    fun testPerformanceManagerRingBuffer() {
        val perf = PerformanceAnalyticsManager(maxCapacity = 30)

        for (i in 1..60) {
            perf.recordMetric(
                key = "operation_$i",
                durationMs = i.toLong(),
                fps = if (i % 2 == 0) 60 else null
            )
        }

        val records = perf.getRecordsSnapshot()
        assertEquals(30, records.size)
        assertEquals("operation_31", records.first().key)
        assertEquals("operation_60", records.last().key)

        val summary = perf.summary.value
        assertEquals("operation_60", summary.lastRecordedKey)
        assertEquals(60L, summary.lastDurationMs)
    }

    @Test
    fun testPerformanceMetricNullFpsWhenNotMeasured() {
        val perf = PerformanceAnalyticsManager()
        perf.recordMetric("test_key", 15L) // No FPS provided

        val record = perf.getRecordsSnapshot().last()
        assertNull("FPS must be null (NOT_MEASURED) if no frame metrics provided", record.fps)
    }
}
