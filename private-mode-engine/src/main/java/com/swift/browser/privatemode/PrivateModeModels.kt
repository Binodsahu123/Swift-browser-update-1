package com.swift.browser.privatemode

import java.util.UUID

/**
 * Session states lifecycle for Private Browsing.
 */
enum class PrivateModeSessionState {
    CREATING,
    ACTIVE,
    CLOSING,
    CLOSED,
    FAILED
}

/**
 * Policy rules governing Private Browsing Sessions.
 */
data class PrivateModePolicy(
    val saveHistory: Boolean = false,
    val saveCookies: Boolean = false,
    val saveFormData: Boolean = false,
    val savePasswords: Boolean = false,
    val saveCacheLocally: Boolean = false,
    val enableAdBlockByDefault: Boolean = true,
    val sendDoNotTrackHeader: Boolean = true,
    val sendGlobalPrivacyControl: Boolean = true,
    val isolateWebStorage: Boolean = true,
    val blockThirdPartyCookies: Boolean = true,
    val isolatePermissionsToSession: Boolean = true,
    val showPrivateModeBanner: Boolean = true,
    val requireBiometricPrompt: Boolean = true,
    val allowDeviceCredentialFallback: Boolean = true,
    val autoPurgeOnTimeoutOrExit: Boolean = true
)

/**
 * Session representation for Private Mode.
 * Each session has sessionId -> private profile -> private tabs mapping.
 */
data class PrivateModeSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastActivityAt: Long = System.currentTimeMillis(),
    val privateTabIds: Set<String> = emptySet(),
    val profileName: String = "private_profile_$sessionId",
    val state: PrivateModeSessionState = PrivateModeSessionState.CREATING,
    val policy: PrivateModePolicy = PrivateModePolicy()
) {
    // Aliases for backwards compatibility
    val id: String get() = sessionId
    val tabIds: MutableList<String> get() = privateTabIds.toMutableList()
    val startTime: Long get() = createdAt
}

/**
 * State representing Private Mode activity across the engine.
 */
data class PrivateModeState(
    val isActive: Boolean = false,
    val sessions: Map<String, PrivateModeSession> = emptyMap(),
    val tabToSessionMap: Map<String, String> = emptyMap(),
    val totalPrivateTabsCount: Int = 0,
    val policy: PrivateModePolicy = PrivateModePolicy(),
    val isBiometricUnlocked: Boolean = false,
    val isBiometricRequired: Boolean = true,
    val biometricAvailability: BiometricAvailability = BiometricAvailability.AVAILABLE,
    val isAutoPurgeOnTimeoutOrExit: Boolean = true,
    val biometricTimeoutMillis: Long = 0L
) {
    // Aliases for backwards compatibility
    val activeSessionId: String? get() = sessions.values.firstOrNull { it.state == PrivateModeSessionState.ACTIVE }?.sessionId
    val openPrivateTabsCount: Int get() = totalPrivateTabsCount
    val canAccessPrivateTabs: Boolean get() = !isBiometricRequired || isBiometricUnlocked
}

/**
 * Descriptor for an isolated WebView profile.
 */
data class PrivateProfileInfo(
    val name: String,
    val isMultiProfileSupported: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)

