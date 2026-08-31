package com.swift.browser.privatestorage

import android.content.Context
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.ServiceWorkerController
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Storage isolation capability status.
 */
enum class StorageCapabilityStatus {
    SUPPORTED,
    UNSUPPORTED_BY_WEBVIEW
}

/**
 * Abstract profile manager interface for AndroidX WebView Profile integration and testing.
 */
interface PrivateStorageProfileProvider {
    fun isMultiProfileSupported(): Boolean
    fun getOrCreateProfile(profileName: String): ProfileWrapper?
    fun getProfile(profileName: String): ProfileWrapper?
    fun getAllProfileNames(): List<String>
    fun deleteProfile(profileName: String): Boolean
    fun setProfile(webView: WebView, profileName: String)
}

/**
 * Wrapper abstraction for a profile and its scoped storage delegates.
 */
interface ProfileWrapper {
    val name: String
    val webStorage: WebStorage?
    val cookieManager: CookieManager?
    val geolocationPermissions: GeolocationPermissions?
    val serviceWorkerController: ServiceWorkerController?
    
    fun clearStorage()
}

class AndroidXProfileWrapper(private val profile: Profile) : ProfileWrapper {
    override val name: String get() = profile.name
    override val webStorage: WebStorage? get() = try { profile.webStorage } catch (e: Throwable) { null }
    override val cookieManager: CookieManager? get() = try { profile.cookieManager } catch (e: Throwable) { null }
    override val geolocationPermissions: GeolocationPermissions? get() = try { profile.geolocationPermissions } catch (e: Throwable) { null }
    override val serviceWorkerController: ServiceWorkerController? get() = try { profile.serviceWorkerController } catch (e: Throwable) { null }

    override fun clearStorage() {
        try {
            webStorage?.deleteAllData()
        } catch (_: Throwable) {}
        try {
            cookieManager?.removeAllCookies(null)
        } catch (_: Throwable) {}
        try {
            geolocationPermissions?.clearAll()
        } catch (_: Throwable) {}
    }
}

class DefaultPrivateStorageProfileProvider : PrivateStorageProfileProvider {
    override fun isMultiProfileSupported(): Boolean {
        return try {
            WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
        } catch (e: Throwable) {
            false
        }
    }

    override fun getOrCreateProfile(profileName: String): ProfileWrapper? {
        if (!isMultiProfileSupported()) return null
        return try {
            val store = ProfileStore.getInstance()
            val p = store.getOrCreateProfile(profileName)
            AndroidXProfileWrapper(p)
        } catch (e: Throwable) {
            null
        }
    }

    override fun getProfile(profileName: String): ProfileWrapper? {
        if (!isMultiProfileSupported()) return null
        return try {
            val store = ProfileStore.getInstance()
            val p = store.getProfile(profileName) ?: return null
            AndroidXProfileWrapper(p)
        } catch (e: Throwable) {
            null
        }
    }

    override fun getAllProfileNames(): List<String> {
        if (!isMultiProfileSupported()) return emptyList()
        return try {
            ProfileStore.getInstance().allProfileNames
        } catch (e: Throwable) {
            emptyList()
        }
    }

    override fun deleteProfile(profileName: String): Boolean {
        if (!isMultiProfileSupported()) return false
        return try {
            ProfileStore.getInstance().deleteProfile(profileName)
        } catch (e: Throwable) {
            false
        }
    }

    override fun setProfile(webView: WebView, profileName: String) {
        if (isMultiProfileSupported()) {
            try {
                WebViewCompat.setProfile(webView, profileName)
            } catch (_: Throwable) {}
        }
    }
}
