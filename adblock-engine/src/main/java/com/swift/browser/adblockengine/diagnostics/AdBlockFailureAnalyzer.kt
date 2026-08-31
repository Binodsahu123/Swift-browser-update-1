package com.swift.browser.adblockengine.diagnostics

/**
 * Analyzes rule parse exceptions, network download limits, and updates errors.
 */
object AdBlockFailureAnalyzer {
    fun analyzeFailure(errorMsg: String): String {
        return when {
            errorMsg.contains("Timeout") -> "Network update failed because of connection timeout limit."
            errorMsg.contains("404") -> "EasyList source URL returned 404. Subscription target might have changed."
            errorMsg.contains("OutOfMemory") -> "Failed parsing large custom filter file due to extreme size."
            else -> "Unknown subsystem warning: $errorMsg"
        }
    }
}
