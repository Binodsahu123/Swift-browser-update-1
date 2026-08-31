package com.swift.browser.privatemode

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import com.swift.browser.cookieengine.CookieEngineApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles purging and isolation of local storage, cookies, cache, and WebData for Private Mode sessions.
 */
class PrivateStorageManager(private val context: Context) {

    companion object {
        private const val TAG = "PrivateStorageManager"
    }

    /**
     * Clears all temporary storage associated with a Private Mode session.
     */
    suspend fun clearPrivateSessionStorage(sessionId: String, webViews: List<WebView> = emptyList()) {
        withContext(Dispatchers.Main) {
            try {
                // Clear cookies for private sessions
                val cookieEngine = CookieEngineApi.getInstance(context)
                val profileName = com.swift.browser.cookieengine.CookieEngine.getPrivateProfileName(sessionId)
                cookieEngine.deletePrivateProfile(profileName)
                cookieEngine.clearIncognitoCookies()

                // Clear WebStorage
                WebStorage.getInstance().deleteAllData()

                // Clear WebViews cache & history
                webViews.forEach { webView ->
                    try {
                        webView.clearCache(true)
                        webView.clearFormData()
                        webView.clearHistory()
                        webView.clearSslPreferences()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error clearing webview cache: ${e.message}")
                    }
                }

                // Flush cookie changes
                cookieEngine.flush(profileName)

                Log.i(TAG, "Private session storage cleared for session $sessionId")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing private session storage: ${e.message}", e)
            }
        }
    }

    /**
     * Immediately purges ALL private mode cache, cookies, profiles, and site data
     * upon biometric timeout or application exit.
     */
    suspend fun purgeAllPrivateCacheAndCookies(
        activeSessionIds: Collection<String> = emptyList(),
        webViews: List<WebView> = emptyList()
    ) {
        withContext(Dispatchers.Main) {
            try {
                val cookieEngine = CookieEngineApi.getInstance(context)

                // 1. Delete all private profile cookies for known sessions
                activeSessionIds.forEach { sessionId ->
                    try {
                        val profileName = com.swift.browser.cookieengine.CookieEngine.getPrivateProfileName(sessionId)
                        cookieEngine.deletePrivateProfile(profileName)
                        cookieEngine.flush(profileName)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error deleting profile for session $sessionId: ${e.message}")
                    }
                }

                // 2. Clear default incognito cookies and private profiles
                try {
                    cookieEngine.clearIncognitoCookies()
                    cookieEngine.deletePrivateProfile("private_profile_default")
                    cookieEngine.deletePrivateProfile("incognito")
                } catch (e: Exception) {
                    Log.w(TAG, "Error deleting incognito profiles: ${e.message}")
                }

                // 3. Clear WebStorage (IndexedDB, LocalStorage, WebSQL)
                try {
                    WebStorage.getInstance().deleteAllData()
                } catch (e: Exception) {
                    Log.w(TAG, "Error clearing WebStorage: ${e.message}")
                }

                // 4. Clear all WebViews cache, form data, history and SSL preferences
                webViews.forEach { webView ->
                    try {
                        webView.clearCache(true)
                        webView.clearFormData()
                        webView.clearHistory()
                        webView.clearSslPreferences()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error clearing webview cache in purge: ${e.message}")
                    }
                }

                // 5. Clear ephemeral cache using a temporary WebView if no webviews were passed
                try {
                    val tempWebView = WebView(context.applicationContext)
                    tempWebView.clearCache(true)
                    tempWebView.clearFormData()
                    tempWebView.destroy()
                } catch (e: Throwable) {
                    Log.w(TAG, "Temp webview cache clear: ${e.message}")
                }

                // 6. Remove session cookies and flush CookieManager
                try {
                    CookieManager.getInstance().removeSessionCookies(null)
                    CookieManager.getInstance().flush()
                } catch (e: Throwable) {
                    Log.w(TAG, "CookieManager flush on purge: ${e.message}")
                }

                // 7. Clear private permission cache
                com.swift.browser.permissionengine.PermissionCache.clearIncognitoCache()

                Log.i(TAG, "Purged all private mode cache and cookies successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error during purgeAllPrivateCacheAndCookies: ${e.message}", e)
            }
        }
    }
}
