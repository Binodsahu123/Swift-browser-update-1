package com.swift.browser.privatemode

import android.webkit.WebView
import kotlinx.coroutines.flow.StateFlow

/**
 * Public API for the Private Mode Engine.
 */
interface PrivateModeEngineApi {
    val state: StateFlow<PrivateModeState>

    /**
     * Opens a new private session with isolated storage/profile.
     */
    fun openSession(policy: PrivateModePolicy = PrivateModePolicy()): PrivateModeSession

    /**
     * Obtains an active or tracked private session by ID.
     */
    fun getSession(sessionId: String): PrivateModeSession?

    /**
     * Attaches a tab to a specific private session.
     */
    fun attachTab(sessionId: String, tabId: String): Boolean

    /**
     * Detaches a tab from its private session. If it was the last tab, closes the session.
     */
    fun detachTab(tabId: String): Boolean

    /**
     * Explicitly closes a private session and purges isolated profile/storage.
     */
    suspend fun closeSession(sessionId: String): Boolean

    /**
     * Closes all active private sessions and purges all isolated data.
     */
    suspend fun closeAllSessions()

    /**
     * Cleans up orphaned or stale sessions/tabs without touching standard browser data.
     */
    suspend fun cleanupOrphans()

    // --- Backward Compatibility & Auxiliary Methods ---
    fun startPrivateSession(): PrivateModeSession
    suspend fun endPrivateSession(sessionId: String)
    suspend fun endAllPrivateSessions()
    fun isPrivateModeActive(): Boolean
    fun getActivePrivateSession(): PrivateModeSession?

    fun configureWebViewForPrivateMode(webView: WebView, sessionId: String)
    fun registerPrivateTab(tabId: String, sessionId: String? = null)
    fun unregisterPrivateTab(tabId: String)
    
    fun shouldRecordHistory(isPrivate: Boolean): Boolean
    fun shouldRecordTopSites(isPrivate: Boolean): Boolean
    fun shouldPersistSession(isPrivate: Boolean): Boolean

    // --- Biometric Authentication Protection ---
    val biometricAuthManager: PrivateBiometricAuthManager
    val isBiometricUnlocked: StateFlow<Boolean>
    var isAutoPurgeOnTimeoutOrExit: Boolean
    var biometricTimeoutMillis: Long
    fun lockPrivateTabs()
    fun unlockPrivateTabs()
    fun setBiometricRequired(required: Boolean)
    fun isBiometricRequired(): Boolean
    fun canAccessPrivateTabs(): Boolean
    fun authenticateBiometric(
        activity: androidx.fragment.app.FragmentActivity,
        config: PrivateBiometricConfig = PrivateBiometricConfig(),
        onResult: (BiometricAuthResult) -> Unit = {}
    )
    fun checkBiometricAvailability(): BiometricAvailability
    suspend fun purgeAllPrivateCacheAndCookies()
    suspend fun onBiometricTimeout()
    suspend fun onAppExit()
    fun onAppBackgrounded()
    fun onAppForegrounded()
}

