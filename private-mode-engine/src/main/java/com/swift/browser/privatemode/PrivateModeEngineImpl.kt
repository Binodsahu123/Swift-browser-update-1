package com.swift.browser.privatemode

import android.content.Context
import android.util.Log
import android.webkit.WebView
import com.swift.browser.permissionengine.PermissionCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Core Implementation of PrivateModeEngineApi.
 * Manages multi-session isolation, profile mapping, tab registration, and lifecycle state.
 */
class PrivateModeEngineImpl(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) : PrivateModeEngineApi {

    companion object {
        private const val TAG = "PrivateModeEngine"

        @Volatile
        private var instance: PrivateModeEngineImpl? = null

        fun getInstance(context: Context): PrivateModeEngineImpl {
            return instance ?: synchronized(this) {
                instance ?: PrivateModeEngineImpl(context.applicationContext).also { instance = it }
            }
        }
    }

    private val profileManager = PrivateProfileManager(context)
    private val storageManager = PrivateStorageManager(context)
    private val networkManager = PrivateNetworkManager()
    private val mediaManager = PrivateMediaManager()
    override val biometricAuthManager = PrivateBiometricAuthManager(context)
    override val isBiometricUnlocked: StateFlow<Boolean> = biometricAuthManager.isUnlocked

    private val _state = MutableStateFlow(PrivateModeState())
    override val state: StateFlow<PrivateModeState> = _state.asStateFlow()

    private val activeSessions = ConcurrentHashMap<String, PrivateModeSession>()
    private val tabToSessionMap = ConcurrentHashMap<String, String>()

    init {
        scope.launch {
            biometricAuthManager.isUnlocked.collect {
                syncState()
            }
        }
    }

    private fun syncState() {
        val sessionMap = activeSessions.toMap()
        val tabMap = tabToSessionMap.toMap()
        val totalTabs = tabMap.size
        val active = sessionMap.values.any { it.state == PrivateModeSessionState.ACTIVE }
        
        val firstActiveSession = sessionMap.values.firstOrNull { it.state == PrivateModeSessionState.ACTIVE }
        val currentPolicy = firstActiveSession?.policy ?: PrivateModePolicy()

        _state.value = PrivateModeState(
            isActive = active,
            sessions = sessionMap,
            tabToSessionMap = tabMap,
            totalPrivateTabsCount = totalTabs,
            policy = currentPolicy,
            isBiometricUnlocked = biometricAuthManager.isUnlocked.value,
            isBiometricRequired = biometricAuthManager.isBiometricRequired.value,
            biometricAvailability = biometricAuthManager.checkBiometricAvailability(),
            isAutoPurgeOnTimeoutOrExit = biometricAuthManager.isAutoPurgeOnTimeoutOrExit.value,
            biometricTimeoutMillis = biometricAuthManager.timeoutDurationMillis.value
        )
    }

    override fun openSession(policy: PrivateModePolicy): PrivateModeSession {
        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val initialSession = PrivateModeSession(
            sessionId = sessionId,
            createdAt = now,
            lastActivityAt = now,
            privateTabIds = emptySet(),
            profileName = "private_profile_$sessionId",
            state = PrivateModeSessionState.CREATING,
            policy = policy
        )
        activeSessions[sessionId] = initialSession
        syncState()

        return try {
            profileManager.getOrCreatePrivateProfile(sessionId)
            val activeSession = initialSession.copy(state = PrivateModeSessionState.ACTIVE)
            activeSessions[sessionId] = activeSession
            syncState()
            Log.i(TAG, "Opened Private Browsing Session: $sessionId")
            activeSession
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Private Browsing Session $sessionId: ${e.message}", e)
            val failedSession = initialSession.copy(state = PrivateModeSessionState.FAILED)
            activeSessions[sessionId] = failedSession
            syncState()
            failedSession
        }
    }

    override fun getSession(sessionId: String): PrivateModeSession? {
        return activeSessions[sessionId]
    }

    override fun attachTab(sessionId: String, tabId: String): Boolean {
        val session = activeSessions[sessionId] ?: return false
        if (session.state != PrivateModeSessionState.ACTIVE) return false

        // Detach from previous session if registered elsewhere
        val currentSessionId = tabToSessionMap[tabId]
        if (currentSessionId != null && currentSessionId != sessionId) {
            detachTab(tabId)
        }

        val updatedTabIds = session.privateTabIds + tabId
        val updatedSession = session.copy(
            privateTabIds = updatedTabIds,
            lastActivityAt = System.currentTimeMillis()
        )
        activeSessions[sessionId] = updatedSession
        tabToSessionMap[tabId] = sessionId
        syncState()
        Log.i(TAG, "Attached tab $tabId to session $sessionId")
        return true
    }

    override fun detachTab(tabId: String): Boolean {
        val sessionId = tabToSessionMap.remove(tabId) ?: return false
        val session = activeSessions[sessionId]
        if (session != null) {
            val updatedTabIds = session.privateTabIds - tabId
            val updatedSession = session.copy(
                privateTabIds = updatedTabIds,
                lastActivityAt = System.currentTimeMillis()
            )
            activeSessions[sessionId] = updatedSession
            Log.i(TAG, "Detached tab $tabId from session $sessionId")

            if (updatedTabIds.isEmpty()) {
                scope.launch {
                    closeSession(sessionId)
                }
            } else {
                syncState()
            }
        } else {
            syncState()
        }
        return true
    }

    override suspend fun closeSession(sessionId: String): Boolean {
        val session = activeSessions[sessionId] ?: return false
        if (session.state == PrivateModeSessionState.CLOSING || session.state == PrivateModeSessionState.CLOSED) {
            return false
        }

        val closingSession = session.copy(state = PrivateModeSessionState.CLOSING)
        activeSessions[sessionId] = closingSession
        syncState()

        // Remove associated tabs from mapping
        session.privateTabIds.forEach { tabId ->
            tabToSessionMap.remove(tabId)
        }

        try {
            storageManager.clearPrivateSessionStorage(sessionId)
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing private storage for $sessionId: ${e.message}")
        }

        try {
            profileManager.deletePrivateProfile(sessionId)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting private profile for $sessionId: ${e.message}")
        }

        PermissionCache.clearIncognitoCache()

        val closedSession = closingSession.copy(state = PrivateModeSessionState.CLOSED)
        activeSessions.remove(sessionId)
        syncState()
        Log.i(TAG, "Closed Private Browsing Session: $sessionId")
        return true
    }

    override suspend fun closeAllSessions() {
        val sessionIds = activeSessions.keys.toList()
        sessionIds.forEach { id ->
            closeSession(id)
        }
        activeSessions.clear()
        tabToSessionMap.clear()
        PermissionCache.clearIncognitoCache()
        syncState()
        Log.i(TAG, "All Private Browsing Sessions closed and purged.")
    }

    override suspend fun cleanupOrphans() {
        val orphanSessionIds = activeSessions.values
            .filter { 
                it.state == PrivateModeSessionState.CLOSING || 
                it.state == PrivateModeSessionState.CLOSED || 
                it.state == PrivateModeSessionState.FAILED || 
                (it.privateTabIds.isEmpty() && it.state == PrivateModeSessionState.ACTIVE) 
            }
            .map { it.sessionId }

        orphanSessionIds.forEach { id ->
            closeSession(id)
        }

        val orphanedTabs = tabToSessionMap.filterValues { !activeSessions.containsKey(it) }.keys
        orphanedTabs.forEach { tabId ->
            tabToSessionMap.remove(tabId)
        }

        PermissionCache.clearIncognitoCache()
        syncState()
        Log.i(TAG, "Cleanup orphans completed.")
    }

    // --- Backward Compatibility & Auxiliary Methods ---
    override fun startPrivateSession(): PrivateModeSession = openSession()

    override suspend fun endPrivateSession(sessionId: String) {
        closeSession(sessionId)
    }

    override suspend fun endAllPrivateSessions() {
        closeAllSessions()
    }

    override fun isPrivateModeActive(): Boolean = _state.value.isActive

    override fun getActivePrivateSession(): PrivateModeSession? {
        val activeId = _state.value.activeSessionId ?: return null
        return getSession(activeId)
    }

    override fun configureWebViewForPrivateMode(webView: WebView, sessionId: String) {
        val session = getSession(sessionId) ?: openSession()
        profileManager.bindWebViewToProfile(webView, session.sessionId)
        networkManager.applyPrivateNetworkSettings(webView, session.policy)
    }

    override fun registerPrivateTab(tabId: String, sessionId: String?) {
        val session = (if (sessionId != null) getSession(sessionId) else null)
            ?: getActivePrivateSession()
            ?: openSession()
        attachTab(session.sessionId, tabId)
    }

    override fun unregisterPrivateTab(tabId: String) {
        detachTab(tabId)
    }

    override fun shouldRecordHistory(isPrivate: Boolean): Boolean = !isPrivate

    override fun shouldRecordTopSites(isPrivate: Boolean): Boolean = !isPrivate

    override fun shouldPersistSession(isPrivate: Boolean): Boolean = !isPrivate

    // --- Biometric Authentication Protection Implementation ---
    override fun lockPrivateTabs() {
        biometricAuthManager.lock()
        syncState()
    }

    override fun unlockPrivateTabs() {
        biometricAuthManager.unlock()
        syncState()
    }

    override fun setBiometricRequired(required: Boolean) {
        biometricAuthManager.setBiometricRequired(required)
        syncState()
    }

    override fun isBiometricRequired(): Boolean = biometricAuthManager.isBiometricRequired.value

    override fun canAccessPrivateTabs(): Boolean = biometricAuthManager.canAccessPrivateTabs()

    override fun authenticateBiometric(
        activity: androidx.fragment.app.FragmentActivity,
        config: PrivateBiometricConfig,
        onResult: (BiometricAuthResult) -> Unit
    ) {
        biometricAuthManager.authenticate(activity, config) { result ->
            syncState()
            onResult(result)
        }
    }

    override var isAutoPurgeOnTimeoutOrExit: Boolean
        get() = biometricAuthManager.isAutoPurgeOnTimeoutOrExit()
        set(value) {
            biometricAuthManager.setAutoPurgeOnTimeoutOrExit(value)
            syncState()
        }

    override var biometricTimeoutMillis: Long
        get() = biometricAuthManager.getTimeoutDurationMillis()
        set(value) {
            biometricAuthManager.setTimeoutDurationMillis(value)
            syncState()
        }

    override fun checkBiometricAvailability(): BiometricAvailability =
        biometricAuthManager.checkBiometricAvailability()

    override suspend fun purgeAllPrivateCacheAndCookies() {
        val sessionIds = activeSessions.keys.toList()
        storageManager.purgeAllPrivateCacheAndCookies(sessionIds)
        profileManager.deleteAllPrivateProfiles()
        PermissionCache.clearIncognitoCache()
        Log.i(TAG, "Completed purgeAllPrivateCacheAndCookies across all private sessions")
    }

    override suspend fun onBiometricTimeout() {
        lockPrivateTabs()
        if (isAutoPurgeOnTimeoutOrExit) {
            purgeAllPrivateCacheAndCookies()
        }
    }

    override suspend fun onAppExit() {
        if (isAutoPurgeOnTimeoutOrExit) {
            purgeAllPrivateCacheAndCookies()
        }
    }

    override fun onAppBackgrounded() {
        biometricAuthManager.onAppBackgrounded(
            onPurgeAction = { purgeAllPrivateCacheAndCookies() },
            scope = scope
        )
        syncState()
    }

    override fun onAppForegrounded() {
        biometricAuthManager.onAppForegrounded(
            onPurgeAction = { purgeAllPrivateCacheAndCookies() },
            scope = scope
        )
        syncState()
    }
}

