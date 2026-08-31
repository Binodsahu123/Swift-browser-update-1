package com.swift.browser.permissionengine

enum class ResourceDecisionState {
    ALLOW,
    BLOCK,
    ASK,
    USER_DECISION_REQUIRED,
    SYSTEM_PERMISSION_REQUIRED,
    SYSTEM_PERMISSION_BLOCKED,
    HARDWARE_UNAVAILABLE,
    SECURITY_BLOCKED,
    UNKNOWN,
    INVALID,
    EXPIRED,
    CANCELED
}

enum class OriginEligibilityResult {
    ALLOWED,
    BLOCKED,
    UNKNOWN
}

data class ResourcePermissionDecision(
    val permissionType: String,
    val webViewResource: String,
    val websiteDecision: String,
    val securityDecision: String,
    val androidDecision: String,
    val hardwareDecision: String,
    val finalDecision: ResourceDecisionState,
    val reason: String
)

data class FinalPermissionDecision(
    val requestId: String,
    val origin: String,
    val tabId: String,
    val overallDecision: String,
    val resourceDecisions: List<ResourcePermissionDecision>,
    val allowedResources: List<String>,
    val deniedResources: List<String>,
    val androidPermissionsUsed: List<String>,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)

interface PermissionOriginEligibility {
    fun evaluate(
        origin: String,
        permissionType: String
    ): OriginEligibilityResult
}

class StandardPermissionOriginEligibility : PermissionOriginEligibility {
    override fun evaluate(origin: String, permissionType: String): OriginEligibilityResult {
        val clean = origin.lowercase().trim()
        if (clean.isEmpty()) return OriginEligibilityResult.BLOCKED
        val isSecure = clean.startsWith("https://") ||
                clean.startsWith("localhost") ||
                clean.startsWith("http://localhost") ||
                clean.startsWith("http://127.0.0.1") ||
                clean.startsWith("swift://") ||
                clean.startsWith("about:")
        return if (isSecure) OriginEligibilityResult.ALLOWED else OriginEligibilityResult.BLOCKED
    }
}
