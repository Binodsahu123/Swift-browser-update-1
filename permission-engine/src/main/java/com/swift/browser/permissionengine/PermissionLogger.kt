package com.swift.browser.permissionengine

import android.util.Log

object PermissionLogger {
    private const val TAG = "SwiftPermissionEngine"

    fun logEvent(origin: String, permission: String, state: String, detail: String) {
        try {
            Log.i(TAG, "[EVENT] Origin: $origin | Permission: $permission | State: $state | Detail: $detail")
        } catch (_: Throwable) {
            println("[EVENT] Origin: $origin | Permission: $permission | State: $state | Detail: $detail")
        }
    }

    fun logSuccess(origin: String, permission: String, androidResult: String, grantResult: String, verificationResult: String) {
        try {
            Log.i(TAG, "🟢 [SUCCESS] Origin: $origin | Permission: $permission | Android: $androidResult | WebView: $grantResult | Verification: $verificationResult | Result: SUCCESS")
        } catch (_: Throwable) {
            println("🟢 [SUCCESS] Origin: $origin | Permission: $permission | Android: $androidResult | WebView: $grantResult | Verification: $verificationResult | Result: SUCCESS")
        }
    }

    fun logFailure(origin: String, permission: String, reason: String, stackTrace: String? = null) {
        try {
            Log.e(TAG, "🔴 [FAILURE] Origin: $origin | Permission: $permission | Reason: $reason")
            if (stackTrace != null) {
                Log.e(TAG, "Stacktrace: $stackTrace")
            }
        } catch (_: Throwable) {
            println("🔴 [FAILURE] Origin: $origin | Permission: $permission | Reason: $reason")
            if (stackTrace != null) {
                println("Stacktrace: $stackTrace")
            }
        }
    }
}
