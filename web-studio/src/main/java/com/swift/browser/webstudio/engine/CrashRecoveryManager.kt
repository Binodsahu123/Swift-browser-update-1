package com.swift.browser.webstudio.engine

class CrashRecoveryManager(private val diagnosticsManager: DiagnosticsManager) {
    fun executeSafe(operationName: String, action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            diagnosticsManager.logError("Crash recovered in operation: $operationName", e)
            // Prevent actual crash, ensure state remains stable
        }
    }
}
