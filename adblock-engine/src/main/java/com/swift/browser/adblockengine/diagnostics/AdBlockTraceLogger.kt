package com.swift.browser.adblockengine.diagnostics

import android.util.Log

/**
 * High frequency tracer log that tracks evaluation routes in debug logs.
 */
object AdBlockTraceLogger {
    private const val TAG = "AdBlockTrace"
    var isEnabled = false

    fun traceBlock(url: String, matchedRule: String, isTracker: Boolean) {
        if (isEnabled) {
            Log.d(TAG, "[BLOCKED] type=${if (isTracker) "Tracker" else "Ad"} url=$url matching_rule=$matchedRule")
        }
    }

    fun traceAllow(url: String, reason: String) {
        if (isEnabled) {
            Log.d(TAG, "[ALLOWED] url=$url reason=$reason")
        }
    }
}
