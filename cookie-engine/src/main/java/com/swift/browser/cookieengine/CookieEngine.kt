package com.swift.browser.cookieengine

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

data class CookieChangeEvent(
    val profileName: String,
    val url: String,
    val cookieObj: JSONObject,
    val removed: Boolean,
    val cause: String = "explicit"
)

fun interface OnCookieChangeListener {
    fun onCookieChanged(event: CookieChangeEvent)
}

interface CookieEngine {
    companion object {
        const val PRIVATE_PROFILE_ISOLATION_UNAVAILABLE = "PRIVATE_PROFILE_ISOLATION_UNAVAILABLE"
        fun getPrivateProfileName(sessionId: String): String = "private_profile_$sessionId"
    }

    fun flush(profileName: String? = null)
    fun setAcceptCookie(accept: Boolean)
    fun setAcceptThirdPartyCookies(webView: WebView, accept: Boolean)
    
    // Legacy single-parameter overloads
    fun getCookie(url: String): String? = getCookie(null, url)
    fun setCookie(url: String, value: String, callback: ((Boolean) -> Unit)? = null) = setCookie(null, url, value, callback)
    fun removeCookiesForUrl(url: String) = removeCookiesForUrl(null, url)
    fun removeAllCookies(callback: ((Boolean) -> Unit)? = null)
    
    // Profile-aware cookie operations
    fun getCookie(profileName: String?, url: String): String?
    fun setCookie(profileName: String?, url: String, value: String, callback: ((Boolean) -> Unit)? = null)
    fun removeCookie(profileName: String?, url: String, name: String, domain: String? = null, path: String? = null, callback: ((Boolean) -> Unit)? = null)
    fun removeCookiesForUrl(profileName: String?, url: String)
    
    // Change Observation
    fun addCookieChangeListener(listener: OnCookieChangeListener)
    fun removeCookieChangeListener(listener: OnCookieChangeListener)

    // Normal profile
    fun setupNormalCookies(webView: WebView)
    
    // Profile-aware private APIs
    fun setupPrivateProfile(webView: WebView, profileName: String): String
    fun getProfileCookieManager(profileName: String): CookieManager?
    fun deletePrivateProfile(profileName: String): Boolean
    
    // Backward compatibility
    fun setupIncognitoCookies(webView: WebView)
    fun clearIncognitoCookies()
}

interface ProfileManager {
    fun isMultiProfileSupported(): Boolean
    fun setProfile(webView: WebView, profileName: String)
    fun getProfileCookieManager(profileName: String): CookieManager?
    fun deleteProfile(profileName: String): Boolean
}

class DefaultProfileManager : ProfileManager {
    override fun isMultiProfileSupported(): Boolean {
        return try {
            WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
        } catch (e: Throwable) {
            false
        }
    }

    override fun setProfile(webView: WebView, profileName: String) {
        val profileStore = ProfileStore.getInstance()
        val profile = profileStore.getOrCreateProfile(profileName)
        WebViewCompat.setProfile(webView, profile.name)
        profile.cookieManager.setAcceptCookie(true)
        profile.cookieManager.setAcceptThirdPartyCookies(webView, false)
    }

    override fun getProfileCookieManager(profileName: String): CookieManager? {
        val profileStore = ProfileStore.getInstance()
        // Strict isolation: default profile can be auto-created, but private profiles are GET existing only!
        val profile = if (profileName == Profile.DEFAULT_PROFILE_NAME || profileName == "default") {
            profileStore.getProfile(profileName) ?: profileStore.getOrCreateProfile(profileName)
        } else {
            profileStore.getProfile(profileName)
        }
        return profile?.cookieManager
    }

    override fun deleteProfile(profileName: String): Boolean {
        val profileStore = ProfileStore.getInstance()
        return profileStore.deleteProfile(profileName)
    }
}

class CookieEngineImpl(
    private val context: Context,
    private val profileManager: ProfileManager = DefaultProfileManager()
) : CookieEngine {
    private val defaultCookieManager = CookieManager.getInstance()
    private val TAG = "CookieEngine"
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var flushJob: Job? = null
    private val changeListeners = java.util.concurrent.CopyOnWriteArraySet<OnCookieChangeListener>()

    private fun resolveCookieManager(profileName: String?): CookieManager? {
        if (profileName.isNullOrEmpty() || profileName == "default" || profileName == Profile.DEFAULT_PROFILE_NAME) {
            return getProfileCookieManager("default") ?: defaultCookieManager
        }
        // Strict rule: return profile CookieManager or null if profile deleted/unsupported. NEVER fallback to default!
        return getProfileCookieManager(profileName)
    }

    override fun flush(profileName: String?) {
        val cm = resolveCookieManager(profileName) ?: return
        coroutineScope.launch {
            try {
                cm.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to flush cookies for profile $profileName: ${e.message}")
            }
        }
    }

    override fun addCookieChangeListener(listener: OnCookieChangeListener) {
        changeListeners.add(listener)
    }

    override fun removeCookieChangeListener(listener: OnCookieChangeListener) {
        changeListeners.remove(listener)
    }

    private fun notifyCookieChanged(event: CookieChangeEvent) {
        for (l in changeListeners) {
            try {
                l.onCookieChanged(event)
            } catch (e: Exception) {
                Log.e(TAG, "Error in cookie change listener: ${e.message}")
            }
        }
    }

    override fun setAcceptCookie(accept: Boolean) {
        defaultCookieManager.setAcceptCookie(accept)
    }

    override fun setAcceptThirdPartyCookies(webView: WebView, accept: Boolean) {
        defaultCookieManager.setAcceptThirdPartyCookies(webView, accept)
    }

    override fun getCookie(profileName: String?, url: String): String? {
        val cm = resolveCookieManager(profileName) ?: return null
        return try {
            cm.getCookie(url)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get cookie for url $url: ${e.message}")
            null
        }
    }

    override fun setCookie(profileName: String?, url: String, value: String, callback: ((Boolean) -> Unit)?) {
        val cm = resolveCookieManager(profileName)
        if (cm == null) {
            callback?.invoke(false)
            return
        }
        val targetProfile = profileName ?: "default"
        val valCallback = ValueCallback<Boolean> { success ->
            val isSuccess = success == true
            if (isSuccess) {
                flush(targetProfile)
            }
            callback?.invoke(isSuccess)
        }
        try {
            cm.setCookie(url, value, valCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set cookie for url $url: ${e.message}")
            callback?.invoke(false)
        }
    }

    override fun removeCookie(
        profileName: String?,
        url: String,
        name: String,
        domain: String?,
        path: String?,
        callback: ((Boolean) -> Unit)?
    ) {
        val cm = resolveCookieManager(profileName)
        if (cm == null) {
            callback?.invoke(false)
            return
        }
        val targetProfile = profileName ?: "default"
        val cookieHeader = StringBuilder("$name=; Max-Age=-99999999; expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=${path ?: "/"}")
        if (!domain.isNullOrBlank()) {
            cookieHeader.append("; Domain=$domain")
        }
        val valCallback = ValueCallback<Boolean> { success ->
            val isSuccess = success == true
            if (isSuccess) {
                flush(targetProfile)
            }
            callback?.invoke(isSuccess)
        }
        try {
            cm.setCookie(url, cookieHeader.toString(), valCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove cookie $name for url $url: ${e.message}")
            callback?.invoke(false)
        }
    }

    override fun removeCookiesForUrl(profileName: String?, url: String) {
        val cm = resolveCookieManager(profileName) ?: return
        val targetProfile = profileName ?: "default"
        try {
            val existing = cm.getCookie(url)
            if (!existing.isNullOrEmpty()) {
                val pairs = existing.split(";")
                for (pair in pairs) {
                    val name = pair.substringBefore("=").trim()
                    if (name.isNotEmpty()) {
                        cm.setCookie(url, "$name=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/")
                    }
                }
            }
            cm.setCookie(url, "")
            flush(targetProfile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear cookies for URL: ${e.message}")
        }
    }

    override fun removeAllCookies(callback: ((Boolean) -> Unit)?) {
        val valCallback = ValueCallback<Boolean> { success ->
            val isSuccess = success == true
            if (isSuccess) {
                flush("default")
            }
            callback?.invoke(isSuccess)
        }
        defaultCookieManager.removeAllCookies(valCallback)
    }

    override fun setupNormalCookies(webView: WebView) {
        if (profileManager.isMultiProfileSupported()) {
            try {
                val profileStore = ProfileStore.getInstance()
                val profile = profileStore.getOrCreateProfile(Profile.DEFAULT_PROFILE_NAME)
                WebViewCompat.setProfile(webView, profile.name)
            } catch (e: Exception) {
                Log.e(TAG, "Multi-profile error for normal cookies", e)
            }
        }
        
        defaultCookieManager.setAcceptCookie(true)
        defaultCookieManager.setAcceptThirdPartyCookies(webView, true)
    }

    override fun setupPrivateProfile(webView: WebView, profileName: String): String {
        if (!profileManager.isMultiProfileSupported()) {
            Log.w(TAG, "Platform limitation: Multi-Profile not supported. Private profile isolation unavailable for $profileName.")
            return CookieEngine.PRIVATE_PROFILE_ISOLATION_UNAVAILABLE
        }

        return try {
            profileManager.setProfile(webView, profileName)
            profileName
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup private profile $profileName: ${e.message}", e)
            CookieEngine.PRIVATE_PROFILE_ISOLATION_UNAVAILABLE
        }
    }

    override fun getProfileCookieManager(profileName: String): CookieManager? {
        if (!profileManager.isMultiProfileSupported()) {
            return null
        }
        return try {
            profileManager.getProfileCookieManager(profileName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get profile cookie manager for $profileName: ${e.message}", e)
            null
        }
    }

    override fun deletePrivateProfile(profileName: String): Boolean {
        if (!profileManager.isMultiProfileSupported()) {
            Log.w(TAG, "Platform limitation: Multi-Profile not supported. Cannot delete private profile $profileName.")
            return false
        }
        return try {
            val deleted = profileManager.deleteProfile(profileName)
            Log.d(TAG, "Deleted private profile $profileName: $deleted")
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete private profile $profileName: ${e.message}", e)
            false
        }
    }

    override fun setupIncognitoCookies(webView: WebView) {
        val result = setupPrivateProfile(webView, "private_profile_default")
        if (result == CookieEngine.PRIVATE_PROFILE_ISOLATION_UNAVAILABLE) {
            defaultCookieManager.setAcceptCookie(true)
            defaultCookieManager.setAcceptThirdPartyCookies(webView, false)
        }
    }

    override fun clearIncognitoCookies() {
        deletePrivateProfile("private_profile_default")
        deletePrivateProfile("incognito")
    }
}
