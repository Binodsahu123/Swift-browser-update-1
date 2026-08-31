package com.swift.browser.permissionengine

/**
 * Strongly typed decision model returned to native modules and callers requesting capability evaluations.
 * Ensures native modules receive structured decision data without directly altering website permission state.
 */
data class CapabilityDecision(
    val requestId: String,
    val capabilityId: String,
    val origin: String,
    val decision: String, // "ALLOW", "ALLOW_ALWAYS", "ALLOW_ONCE", "BLOCK", "DENIED", "UNSUPPORTED", "CANCELED", "EXPIRED"
    val isAllowed: Boolean,
    val capabilityState: CapabilityState,
    val reason: String = "",
    val requiresPrompt: Boolean = false,
    val isIncognito: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
