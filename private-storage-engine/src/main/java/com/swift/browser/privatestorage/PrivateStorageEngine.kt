package com.swift.browser.privatestorage

import android.content.Context
import android.util.Log
import android.webkit.WebView
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

interface PrivateStorageEngine {
    companion object {
        const val UNSUPPORTED_BY_WEBVIEW = "UNSUPPORTED_BY_WEBVIEW"
        const val SUCCESS = "SUCCESS"
        const val SESSION_CLOSED = "SESSION_CLOSED"
        const val PRIVATE_PROFILE_PREFIX = "private_profile_"
        
        fun defaultProfileName(sessionId: String): String = "$PRIVATE_PROFILE_PREFIX$sessionId"
    }

    /**
     * Checks current platform support for AndroidX Multi-Profile WebView storage isolation.
     */
    fun capabilityStatus(): StorageCapabilityStatus

    /**
     * Creates an isolated storage session bound to a private profile.
     * Returns SUCCESS if created, or UNSUPPORTED_BY_WEBVIEW if not supported on the device.
     */
    fun createPrivateStorageSession(sessionId: String, profileName: String = defaultProfileName(sessionId)): String

    /**
     * Clears private storage (WebStorage, IndexedDB/storage, Cookies, Geolocation) strictly for the given profile.
     * NEVER clears normal-profile or global storage.
     */
    fun clearPrivateStorage(sessionId: String, profileName: String = defaultProfileName(sessionId)): String

    /**
     * Performs ordered cleanup and deletion of a private profile:
     * 1. Destroys dependent WebViews bound to the session
     * 2. Releases profile-bound resources
     * 3. Clears and deletes private profile from ProfileStore
     * 4. Marks session closed
     */
    fun deletePrivateProfile(sessionId: String, profileName: String = defaultProfileName(sessionId)): String

    /**
     * Identifies and deletes any orphan private profiles not belonging to active sessions.
     */
    fun cleanupOrphanProfiles(
        activeSessionIds: Set<String> = emptySet(),
        activeProfileNames: Set<String> = emptySet()
    ): List<String>

    /**
     * Registers a WebView instance associated with a private session.
     */
    fun registerWebView(sessionId: String, webView: WebView)

    /**
     * Binds a WebView to the session's private profile.
     */
    fun bindWebView(sessionId: String, profileName: String, webView: WebView)

    /**
     * Queries whether a private session is currently active.
     */
    fun isSessionActive(sessionId: String): Boolean
}

class PrivateStorageEngineImpl(
    private val context: Context,
    private val profileProvider: PrivateStorageProfileProvider = DefaultPrivateStorageProfileProvider()
) : PrivateStorageEngine {

    private val TAG = "PrivateStorageEngine"

    // Tracks active private sessions: sessionId -> SessionState
    private data class SessionState(
        val sessionId: String,
        val profileName: String,
        val webViews: CopyOnWriteArrayList<WebView> = CopyOnWriteArrayList(),
        @Volatile var isClosed: Boolean = false
    )

    private val activeSessions = ConcurrentHashMap<String, SessionState>()

    override fun capabilityStatus(): StorageCapabilityStatus {
        return if (profileProvider.isMultiProfileSupported()) {
            StorageCapabilityStatus.SUPPORTED
        } else {
            StorageCapabilityStatus.UNSUPPORTED_BY_WEBVIEW
        }
    }

    override fun createPrivateStorageSession(sessionId: String, profileName: String): String {
        if (!profileProvider.isMultiProfileSupported()) {
            Log.w(TAG, "MULTI_PROFILE not supported by WebView on this platform. Explicitly returning ${PrivateStorageEngine.UNSUPPORTED_BY_WEBVIEW}")
            return PrivateStorageEngine.UNSUPPORTED_BY_WEBVIEW
        }

        val session = SessionState(sessionId = sessionId, profileName = profileName)
        activeSessions[sessionId] = session

        try {
            val profile = profileProvider.getOrCreateProfile(profileName)
            if (profile == null) {
                Log.e(TAG, "Failed to create isolated profile $profileName")
                return PrivateStorageEngine.UNSUPPORTED_BY_WEBVIEW
            }
            Log.i(TAG, "Created private storage session $sessionId with profile $profileName")
            return PrivateStorageEngine.SUCCESS
        } catch (e: Throwable) {
            Log.e(TAG, "Exception creating private profile $profileName: ${e.message}", e)
            return PrivateStorageEngine.UNSUPPORTED_BY_WEBVIEW
        }
    }

    override fun registerWebView(sessionId: String, webView: WebView) {
        val session = activeSessions[sessionId]
        if (session != null) {
            if (!session.webViews.contains(webView)) {
                session.webViews.add(webView)
            }
        }
    }

    override fun bindWebView(sessionId: String, profileName: String, webView: WebView) {
        registerWebView(sessionId, webView)
        if (profileProvider.isMultiProfileSupported()) {
            profileProvider.setProfile(webView, profileName)
        }
    }

    override fun clearPrivateStorage(sessionId: String, profileName: String): String {
        if (!profileProvider.isMultiProfileSupported()) {
            return PrivateStorageEngine.UNSUPPORTED_BY_WEBVIEW
        }

        try {
            val profile = profileProvider.getProfile(profileName) ?: profileProvider.getOrCreateProfile(profileName)
            if (profile != null) {
                // Clear storage strictly within the profile scope
                profile.clearStorage()
                Log.i(TAG, "Cleared private storage for profile $profileName (session $sessionId)")
                return PrivateStorageEngine.SUCCESS
            } else {
                return PrivateStorageEngine.UNSUPPORTED_BY_WEBVIEW
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error clearing private storage for profile $profileName: ${e.message}", e)
            return PrivateStorageEngine.UNSUPPORTED_BY_WEBVIEW
        }
    }

    override fun deletePrivateProfile(sessionId: String, profileName: String): String {
        if (!profileProvider.isMultiProfileSupported()) {
            // Even if unsupported, perform local session close
            closeSessionInternal(sessionId)
            return PrivateStorageEngine.UNSUPPORTED_BY_WEBVIEW
        }

        val session = activeSessions[sessionId]

        // Strict Cleanup Order:
        // 1. Destroy dependent WebViews
        session?.webViews?.forEach { webView ->
            try {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.clearHistory()
                webView.destroy()
            } catch (e: Throwable) {
                Log.w(TAG, "Error destroying webview for session $sessionId: ${e.message}")
            }
        }
        session?.webViews?.clear()

        // 2. Release profile-bound resources
        val profile = profileProvider.getProfile(profileName)
        try {
            profile?.clearStorage()
        } catch (e: Throwable) {
            Log.w(TAG, "Error releasing profile storage before deletion: ${e.message}")
        }

        // 3. Clear/delete private profile from ProfileStore
        val deleted = try {
            profileProvider.deleteProfile(profileName)
        } catch (e: Throwable) {
            Log.e(TAG, "Error deleting profile $profileName from store: ${e.message}", e)
            false
        }

        // 4. Mark session closed
        closeSessionInternal(sessionId)

        Log.i(TAG, "Completed deletePrivateProfile for $profileName (session $sessionId, deleted=$deleted)")
        return if (deleted) PrivateStorageEngine.SESSION_CLOSED else PrivateStorageEngine.SESSION_CLOSED
    }

    override fun cleanupOrphanProfiles(
        activeSessionIds: Set<String>,
        activeProfileNames: Set<String>
    ): List<String> {
        if (!profileProvider.isMultiProfileSupported()) {
            return emptyList()
        }

        val allProfiles = profileProvider.getAllProfileNames()
        val inMemoryActiveProfiles = activeSessions.values.filterNot { it.isClosed }.map { it.profileName }.toSet()
        val allActiveNames = activeProfileNames + inMemoryActiveProfiles + activeSessionIds.map { PrivateStorageEngine.defaultProfileName(it) }

        val orphans = allProfiles.filter { name ->
            name.startsWith(PrivateStorageEngine.PRIVATE_PROFILE_PREFIX) && !allActiveNames.contains(name)
        }

        val deletedList = mutableListOf<String>()
        orphans.forEach { orphanName ->
            try {
                val profile = profileProvider.getProfile(orphanName)
                profile?.clearStorage()
                val deleted = profileProvider.deleteProfile(orphanName)
                if (deleted) {
                    deletedList.add(orphanName)
                    Log.i(TAG, "Cleaned up orphan private profile: $orphanName")
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed cleaning orphan profile $orphanName: ${e.message}")
            }
        }
        return deletedList
    }

    override fun isSessionActive(sessionId: String): Boolean {
        val session = activeSessions[sessionId]
        return session != null && !session.isClosed
    }

    private fun closeSessionInternal(sessionId: String) {
        val session = activeSessions[sessionId]
        if (session != null) {
            session.isClosed = true
            activeSessions.remove(sessionId)
        }
    }
}
