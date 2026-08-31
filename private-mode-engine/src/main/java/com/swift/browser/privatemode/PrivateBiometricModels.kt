package com.swift.browser.privatemode

/**
 * Result states for Biometric / Device Credential authentication in Private Mode.
 */
sealed class BiometricAuthResult {
    object Success : BiometricAuthResult()
    data class Error(val errorCode: Int, val message: String) : BiometricAuthResult()
    object Failed : BiometricAuthResult()
    object Cancelled : BiometricAuthResult()
    data class Unavailable(val reason: String) : BiometricAuthResult()

    val isSuccessful: Boolean get() = this is Success
}

/**
 * Biometric hardware and enrollment status.
 */
enum class BiometricAvailability {
    AVAILABLE,
    NO_HARDWARE,
    HARDWARE_UNAVAILABLE,
    NONE_ENROLLED,
    SECURITY_UPDATE_REQUIRED,
    UNSUPPORTED
}

/**
 * Configuration options for Biometric Private Tab Protection.
 */
data class PrivateBiometricConfig(
    val isBiometricRequired: Boolean = true,
    val allowDeviceCredentialFallback: Boolean = true,
    val autoPurgeOnTimeoutOrExit: Boolean = true,
    val timeoutMillis: Long = 0L,
    val promptTitle: String = "Unlock Private Tabs",
    val promptSubtitle: String = "Fingerprint or face unlock required to view active private tabs",
    val promptDescription: String = "Confirm your biometric credentials to access private browsing session"
)
