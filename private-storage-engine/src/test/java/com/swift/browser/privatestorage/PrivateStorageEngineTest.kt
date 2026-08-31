package com.swift.browser.privatestorage

import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.ServiceWorkerController
import android.webkit.WebStorage
import android.webkit.WebView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PrivateStorageEngineTest {

    private class FakeProfileWrapper(
        override val name: String,
        var isStorageCleared: Boolean = false
    ) : ProfileWrapper {
        override val webStorage: WebStorage? = null
        override val cookieManager: CookieManager? = null
        override val geolocationPermissions: GeolocationPermissions? = null
        override val serviceWorkerController: ServiceWorkerController? = null

        override fun clearStorage() {
            isStorageCleared = true
        }
    }

    private class FakeProfileProvider(
        var multiProfileSupported: Boolean = true
    ) : PrivateStorageProfileProvider {
        val profiles = mutableMapOf<String, FakeProfileWrapper>()
        val boundWebViews = mutableMapOf<WebView, String>()

        override fun isMultiProfileSupported(): Boolean = multiProfileSupported

        override fun getOrCreateProfile(profileName: String): ProfileWrapper? {
            if (!multiProfileSupported) return null
            return profiles.getOrPut(profileName) { FakeProfileWrapper(profileName) }
        }

        override fun getProfile(profileName: String): ProfileWrapper? {
            if (!multiProfileSupported) return null
            return profiles[profileName]
        }

        override fun getAllProfileNames(): List<String> {
            if (!multiProfileSupported) return emptyList()
            return profiles.keys.toList()
        }

        override fun deleteProfile(profileName: String): Boolean {
            if (!multiProfileSupported) return false
            return profiles.remove(profileName) != null
        }

        override fun setProfile(webView: WebView, profileName: String) {
            if (multiProfileSupported) {
                boundWebViews[webView] = profileName
            }
        }
    }

    private lateinit var fakeProvider: FakeProfileProvider
    private lateinit var storageEngine: PrivateStorageEngine

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        fakeProvider = FakeProfileProvider(multiProfileSupported = true)
        storageEngine = PrivateStorageEngineImpl(app, profileProvider = fakeProvider)
    }

    @Test
    fun testMultiProfileUnsupportedReturnsExplicitStatus() {
        fakeProvider.multiProfileSupported = false
        assertEquals(StorageCapabilityStatus.UNSUPPORTED_BY_WEBVIEW, storageEngine.capabilityStatus())

        val result = storageEngine.createPrivateStorageSession("sess_unsupported", "profile_unsupported")
        assertEquals(PrivateStorageEngine.UNSUPPORTED_BY_WEBVIEW, result)

        val clearRes = storageEngine.clearPrivateStorage("sess_unsupported", "profile_unsupported")
        assertEquals(PrivateStorageEngine.UNSUPPORTED_BY_WEBVIEW, clearRes)

        val deleteRes = storageEngine.deletePrivateProfile("sess_unsupported", "profile_unsupported")
        assertEquals(PrivateStorageEngine.UNSUPPORTED_BY_WEBVIEW, deleteRes)
    }

    @Test
    fun testPrivateAndNormalIsolation() {
        // Normal profile in provider
        fakeProvider.getOrCreateProfile("Default")

        val sessionId = "session_private_1"
        val privateProfile = "private_profile_session_private_1"

        val createRes = storageEngine.createPrivateStorageSession(sessionId, privateProfile)
        assertEquals(PrivateStorageEngine.SUCCESS, createRes)
        assertTrue(storageEngine.isSessionActive(sessionId))

        // Check profiles in store
        assertNotNull(fakeProvider.getProfile("Default"))
        assertNotNull(fakeProvider.getProfile(privateProfile))

        // Clear private storage
        storageEngine.clearPrivateStorage(sessionId, privateProfile)
        val privWrapper = fakeProvider.profiles[privateProfile]
        assertNotNull(privWrapper)
        assertTrue(privWrapper!!.isStorageCleared)

        val defaultWrapper = fakeProvider.profiles["Default"]
        assertNotNull(defaultWrapper)
        assertFalse(defaultWrapper!!.isStorageCleared)
    }

    @Test
    fun testTwoPrivateSessionsIsolation() {
        val sess1 = "session_A"
        val profile1 = "private_profile_session_A"
        val sess2 = "session_B"
        val profile2 = "private_profile_session_B"

        storageEngine.createPrivateStorageSession(sess1, profile1)
        storageEngine.createPrivateStorageSession(sess2, profile2)

        assertTrue(storageEngine.isSessionActive(sess1))
        assertTrue(storageEngine.isSessionActive(sess2))

        assertNotNull(fakeProvider.getProfile(profile1))
        assertNotNull(fakeProvider.getProfile(profile2))

        // Clear only session A
        storageEngine.clearPrivateStorage(sess1, profile1)
        assertTrue(fakeProvider.profiles[profile1]!!.isStorageCleared)
        assertFalse(fakeProvider.profiles[profile2]!!.isStorageCleared)

        // Delete session A
        val delRes = storageEngine.deletePrivateProfile(sess1, profile1)
        assertEquals(PrivateStorageEngine.SESSION_CLOSED, delRes)
        assertFalse(storageEngine.isSessionActive(sess1))
        assertTrue(storageEngine.isSessionActive(sess2))

        // Session B profile must still exist
        assertNull(fakeProvider.getProfile(profile1))
        assertNotNull(fakeProvider.getProfile(profile2))
    }

    @Test
    fun testOrphanProfileCleanup() {
        // Setup active session
        val activeSess = "active_1"
        val activeProfile = "private_profile_active_1"
        storageEngine.createPrivateStorageSession(activeSess, activeProfile)

        // Simulate orphan profile left behind from crashed previous run
        val orphanProfile1 = "private_profile_orphan_99"
        val orphanProfile2 = "private_profile_orphan_100"
        fakeProvider.getOrCreateProfile(orphanProfile1)
        fakeProvider.getOrCreateProfile(orphanProfile2)
        fakeProvider.getOrCreateProfile("normal_custom_profile")

        assertEquals(4, fakeProvider.profiles.size)

        // Cleanup orphans while active_1 is running
        val cleaned = storageEngine.cleanupOrphanProfiles(
            activeSessionIds = setOf(activeSess),
            activeProfileNames = setOf(activeProfile)
        )

        assertEquals(2, cleaned.size)
        assertTrue(cleaned.contains(orphanProfile1))
        assertTrue(cleaned.contains(orphanProfile2))

        // Active profile and normal profile should NOT be deleted
        assertNotNull(fakeProvider.getProfile(activeProfile))
        assertNotNull(fakeProvider.getProfile("normal_custom_profile"))
        assertNull(fakeProvider.getProfile(orphanProfile1))
        assertNull(fakeProvider.getProfile(orphanProfile2))
    }

    @Test
    fun testGlobalDataSafetyOnPrivateDeletion() {
        // Ensure standard/normal profile exists
        val normalProfile = fakeProvider.getOrCreateProfile("Default") as FakeProfileWrapper
        assertFalse(normalProfile.isStorageCleared)

        val privateSess = "sess_safety"
        val privateProfile = "private_profile_sess_safety"
        storageEngine.createPrivateStorageSession(privateSess, privateProfile)

        // Bind WebView
        val app = RuntimeEnvironment.getApplication()
        val webView = WebView(app)
        storageEngine.bindWebView(privateSess, privateProfile, webView)
        assertEquals(privateProfile, fakeProvider.boundWebViews[webView])

        // Delete private profile
        storageEngine.deletePrivateProfile(privateSess, privateProfile)

        // Normal profile remains untouched
        assertNotNull(fakeProvider.getProfile("Default"))
        assertFalse(normalProfile.isStorageCleared)
        assertFalse(storageEngine.isSessionActive(privateSess))
    }
}
