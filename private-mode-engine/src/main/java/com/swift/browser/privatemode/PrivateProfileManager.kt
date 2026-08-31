package com.swift.browser.privatemode

import android.content.Context
import android.util.Log
import android.webkit.WebView
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Manages dedicated WebView profile isolation for Private Mode sessions.
 */
class PrivateProfileManager(private val context: Context) {

    companion object {
        private const val TAG = "PrivateProfileManager"
        private const val PRIVATE_PROFILE_NAME_PREFIX = "private_profile_"
    }

    private val activeProfiles = java.util.concurrent.ConcurrentHashMap<String, PrivateProfileInfo>()

    /**
     * Obtains or creates an isolated Profile for a given private session.
     */
    fun getOrCreatePrivateProfile(sessionId: String): PrivateProfileInfo {
        val profileName = "$PRIVATE_PROFILE_NAME_PREFIX$sessionId"
        val isSupported = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
        
        if (isSupported) {
            try {
                val profileStore = ProfileStore.getInstance()
                val existing = profileStore.getProfile(profileName)
                if (existing == null) {
                    profileStore.getOrCreateProfile(profileName)
                    Log.i(TAG, "Created new isolated WebKit Profile: $profileName")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create WebKit profile $profileName: ${e.message}", e)
            }
        } else {
            Log.w(TAG, "WebViewFeature.MULTI_PROFILE is not supported on this device version. Using fallback in-memory session isolation.")
        }

        val info = PrivateProfileInfo(
            name = profileName,
            isMultiProfileSupported = isSupported
        )
        activeProfiles[sessionId] = info
        return info
    }

    /**
     * Binds a WebView instance to a private profile if supported.
     */
    fun bindWebViewToProfile(webView: WebView, sessionId: String) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            try {
                val profileName = "$PRIVATE_PROFILE_NAME_PREFIX$sessionId"
                val profileStore = ProfileStore.getInstance()
                val profile = profileStore.getProfile(profileName) ?: profileStore.getOrCreateProfile(profileName)
                WebViewCompat.setProfile(webView, profile.name)
                Log.i(TAG, "Successfully set WebView profile to ${profile.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Error setting WebView profile: ${e.message}", e)
            }
        }
    }

    /**
     * Purges and deletes an isolated private profile upon session destroy.
     */
    fun deletePrivateProfile(sessionId: String) {
        val profileName = "$PRIVATE_PROFILE_NAME_PREFIX$sessionId"
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            try {
                val profileStore = ProfileStore.getInstance()
                val deleted = profileStore.deleteProfile(profileName)
                Log.i(TAG, "Deleted private profile $profileName: success=$deleted")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting private profile $profileName: ${e.message}", e)
            }
        }
        activeProfiles.remove(sessionId)
    }

    /**
     * Deletes all isolated private profiles currently registered in WebKit ProfileStore.
     */
    fun deleteAllPrivateProfiles() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            try {
                val profileStore = ProfileStore.getInstance()
                val allProfiles = profileStore.allProfileNames
                for (name in allProfiles) {
                    if (name.startsWith(PRIVATE_PROFILE_NAME_PREFIX) || name.startsWith("private_") || name == "incognito") {
                        val deleted = profileStore.deleteProfile(name)
                        Log.i(TAG, "Deleted private profile $name: success=$deleted")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting all private profiles: ${e.message}", e)
            }
        }
        activeProfiles.clear()
    }
}
