package com.swift.browser.cookieengine

import android.webkit.CookieManager
import android.webkit.WebView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class CookieEngineTest {

    private lateinit var cookieEngine: CookieEngine

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        cookieEngine = CookieEngineImpl(app)
    }

    @Test
    fun testPrivateProfileNameHelper() {
        val profileName = CookieEngine.getPrivateProfileName("session_12345")
        assertEquals("private_profile_session_12345", profileName)
    }

    @Test
    fun testUnsupportedMultiProfilePathReturnsUnavailable() {
        val app = RuntimeEnvironment.getApplication()
        val unsupportedManager = object : ProfileManager {
            override fun isMultiProfileSupported(): Boolean = false
            override fun setProfile(webView: WebView, profileName: String) {}
            override fun getProfileCookieManager(profileName: String): CookieManager? = null
            override fun deleteProfile(profileName: String): Boolean = false
        }
        val unsupportedEngine = CookieEngineImpl(app, profileManager = unsupportedManager)
        val webView = WebView(app)

        val result = unsupportedEngine.setupPrivateProfile(webView, "private_profile_test")
        assertEquals(CookieEngine.PRIVATE_PROFILE_ISOLATION_UNAVAILABLE, result)

        val cm = unsupportedEngine.getProfileCookieManager("private_profile_test")
        assertNull(cm)

        val deleted = unsupportedEngine.deletePrivateProfile("private_profile_test")
        assertFalse(deleted)
    }

    @Test
    fun testNormalAndPrivateIsolationWithMultiProfile() {
        val app = RuntimeEnvironment.getApplication()
        val configuredProfiles = mutableMapOf<String, CookieManager>()
        val webViewProfileBindings = mutableMapOf<WebView, String>()

        val fakeProfileManager = object : ProfileManager {
            override fun isMultiProfileSupported(): Boolean = true
            override fun setProfile(webView: WebView, profileName: String) {
                webViewProfileBindings[webView] = profileName
                if (!configuredProfiles.containsKey(profileName)) {
                    configuredProfiles[profileName] = CookieManager.getInstance()
                }
            }
            override fun getProfileCookieManager(profileName: String): CookieManager? = configuredProfiles[profileName]
            override fun deleteProfile(profileName: String): Boolean {
                return configuredProfiles.remove(profileName) != null
            }
        }

        val multiProfileEngine = CookieEngineImpl(app, profileManager = fakeProfileManager)
        val webViewNormal = WebView(app)
        val webViewPrivate = WebView(app)

        // Setup normal profile
        multiProfileEngine.setupNormalCookies(webViewNormal)

        // Setup private profile
        val privateProfileName = CookieEngine.getPrivateProfileName("user_session_1")
        val result = multiProfileEngine.setupPrivateProfile(webViewPrivate, privateProfileName)
        assertEquals(privateProfileName, result)
        assertEquals(privateProfileName, webViewProfileBindings[webViewPrivate])

        // Retrieve profile cookie manager
        val privateCookieManager = multiProfileEngine.getProfileCookieManager(privateProfileName)
        assertNotNull(privateCookieManager)
    }

    @Test
    fun testTwoPrivateSessionsIsolation() {
        val app = RuntimeEnvironment.getApplication()
        val configuredProfiles = mutableMapOf<String, CookieManager>()
        val webViewProfileBindings = mutableMapOf<WebView, String>()

        val fakeProfileManager = object : ProfileManager {
            override fun isMultiProfileSupported(): Boolean = true
            override fun setProfile(webView: WebView, profileName: String) {
                webViewProfileBindings[webView] = profileName
                configuredProfiles[profileName] = CookieManager.getInstance()
            }
            override fun getProfileCookieManager(profileName: String): CookieManager? = configuredProfiles[profileName]
            override fun deleteProfile(profileName: String): Boolean = configuredProfiles.remove(profileName) != null
        }

        val multiProfileEngine = CookieEngineImpl(app, profileManager = fakeProfileManager)
        val webViewSession1 = WebView(app)
        val webViewSession2 = WebView(app)

        val profile1 = CookieEngine.getPrivateProfileName("session_alpha")
        val profile2 = CookieEngine.getPrivateProfileName("session_beta")

        val res1 = multiProfileEngine.setupPrivateProfile(webViewSession1, profile1)
        val res2 = multiProfileEngine.setupPrivateProfile(webViewSession2, profile2)

        assertEquals(profile1, res1)
        assertEquals(profile2, res2)
        assertNotEquals(profile1, profile2)

        assertEquals(profile1, webViewProfileBindings[webViewSession1])
        assertEquals(profile2, webViewProfileBindings[webViewSession2])

        val cm1 = multiProfileEngine.getProfileCookieManager(profile1)
        val cm2 = multiProfileEngine.getProfileCookieManager(profile2)

        assertNotNull(cm1)
        assertNotNull(cm2)
    }

    @Test
    fun testProfileDeletion() {
        val app = RuntimeEnvironment.getApplication()
        val profiles = mutableSetOf<String>()

        val fakeProfileManager = object : ProfileManager {
            override fun isMultiProfileSupported(): Boolean = true
            override fun setProfile(webView: WebView, profileName: String) {
                profiles.add(profileName)
            }
            override fun getProfileCookieManager(profileName: String): CookieManager? = if (profiles.contains(profileName)) CookieManager.getInstance() else null
            override fun deleteProfile(profileName: String): Boolean = profiles.remove(profileName)
        }

        val multiProfileEngine = CookieEngineImpl(app, profileManager = fakeProfileManager)
        val webView = WebView(app)

        val profileName = CookieEngine.getPrivateProfileName("temp_session")
        multiProfileEngine.setupPrivateProfile(webView, profileName)
        assertTrue(profiles.contains(profileName))

        val deleted = multiProfileEngine.deletePrivateProfile(profileName)
        assertTrue(deleted)
        assertFalse(profiles.contains(profileName))
        assertNull(multiProfileEngine.getProfileCookieManager(profileName))
    }

    @Test
    fun testNormalCookiesSurvivePrivateCleanup() {
        val app = RuntimeEnvironment.getApplication()
        val profiles = mutableSetOf<String>()

        val fakeProfileManager = object : ProfileManager {
            override fun isMultiProfileSupported(): Boolean = true
            override fun setProfile(webView: WebView, profileName: String) {
                profiles.add(profileName)
            }
            override fun getProfileCookieManager(profileName: String): CookieManager? = if (profiles.contains(profileName)) CookieManager.getInstance() else null
            override fun deleteProfile(profileName: String): Boolean = profiles.remove(profileName)
        }

        val multiProfileEngine = CookieEngineImpl(app, profileManager = fakeProfileManager)
        
        // Set normal cookie on default CookieManager
        multiProfileEngine.setCookie("https://swift.browser", "user_login=valid_token")

        val privateProfile = CookieEngine.getPrivateProfileName("ephemeral_session")
        val webView = WebView(app)
        multiProfileEngine.setupPrivateProfile(webView, privateProfile)

        // Delete private profile
        val deleted = multiProfileEngine.deletePrivateProfile(privateProfile)
        assertTrue(deleted)

        // Normal cookie must survive and not be cleared
        val normalCookie = multiProfileEngine.getCookie("https://swift.browser")
        assertNotNull(normalCookie)
        assertTrue(normalCookie!!.contains("user_login=valid_token"))
    }
}
