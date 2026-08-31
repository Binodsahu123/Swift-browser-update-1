package com.swift.browser.adblockengine.core

import android.content.Context
import com.swift.browser.adblockengine.brave.BraveAdblockAdapter
import com.swift.browser.adblockengine.filters.FilterListManager
import com.swift.browser.adblockengine.filters.FilterUpdateScheduler

/**
 * Diagnostic reporting subsystem. Collects trace data and telemetry.
 */
object AdBlockDiagnostics {
    data class LogItem(val message: String, val timestamp: Long = System.currentTimeMillis())

    private val logQueue = ArrayList<LogItem>()
    private const val MAX_LOGS = 50

    fun init(context: Context) {
        logEvent("Diagnostics Init", "Diagnostic reporter established successfully.")
    }

    fun logEvent(category: String, message: String) {
        synchronized(logQueue) {
            if (logQueue.size >= MAX_LOGS) {
                logQueue.removeAt(0)
            }
            logQueue.add(LogItem("[$category] $message"))
        }
    }

    fun getLogs(): List<LogItem> {
        return synchronized(logQueue) {
            ArrayList(logQueue)
        }
    }

    fun getDiagnosticsReport(context: Context): Map<String, Any> {
        val rulesCount = BraveAdblockAdapter.getRulesCount()
        return mapOf(
            "adapter" to "Brave adblock-rust style matcher (Kotlin Adapter)",
            "rules_count" to rulesCount,
            "whitelist_size" to AdBlockWhitelistManager.getWhitelist().size,
            "blacklist_size" to AdBlockExceptionManager.getBlacklist().size,
            "easylist_active" to AdBlockPolicy.isEasyListEnabled,
            "easyprivacy_active" to AdBlockPolicy.isEasyPrivacyEnabled,
            "last_update_success" to (FilterUpdateScheduler.lastUpdateTime > 0),
            "last_update_timestamp" to FilterUpdateScheduler.lastUpdateTime,
            "active_logs" to getLogs().take(15).map { it.message }
        )
    }
}
