package com.swift.browser.extensionengine

import android.content.Context
import android.webkit.CookieManager
import com.swift.browser.cookieengine.CookieEngine
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ExtensionCookieAdapterTest {

    private lateinit var context: Context
    private lateinit var registry: ExtensionRegistry
    private lateinit var messageBus: MessageBus
    private lateinit var eventManager: EventManager
    private lateinit var permissionAdapter: ExtensionPermissionAdapter
    private lateinit var mockCookieEngine: FakeCookieEngine
    private lateinit var adapter: ExtensionCookieAdapter

    class FakeCookieEngine : CookieEngine {
        private val cookies = mutableMapOf<Pair<String, String>, String>()
        val activeProfiles = mutableSetOf<String>("default")

        override fun flush(profileName: String?) {}
        override fun setAcceptCookie(accept: Boolean) {}
        override fun setAcceptThirdPartyCookies(webView: android.webkit.WebView, accept: Boolean) {}

        override fun getCookie(profileName: String?, url: String): String? {
            val prof = profileName ?: "default"
            if (!activeProfiles.contains(prof)) return null
            return cookies[prof to url]
        }

        override fun setCookie(profileName: String?, url: String, value: String, callback: ((Boolean) -> Unit)?) {
            val prof = profileName ?: "default"
            if (!activeProfiles.contains(prof)) {
                callback?.invoke(false)
                return
            }
            val key = prof to url
            val existing = cookies[key] ?: ""
            val name = value.substringBefore("=").trim()
            val valStr = value.substringAfter("=").substringBefore(";").trim()
            if (value.contains("Max-Age=-") || value.contains("Expires=Thu, 01 Jan 1970")) {
                val updated = existing.split(";").map { it.trim() }
                    .filter { !it.startsWith("$name=") && it.isNotEmpty() }
                    .joinToString("; ")
                cookies[key] = updated
            } else {
                val list = existing.split(";").map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("$name=") }.toMutableList()
                list.add("$name=$valStr")
                cookies[key] = list.joinToString("; ")
            }
            callback?.invoke(true)
        }

        override fun removeCookie(profileName: String?, url: String, name: String, domain: String?, path: String?, callback: ((Boolean) -> Unit)?) {
            setCookie(profileName, url, "$name=; Max-Age=-99999999; Path=/", callback)
        }

        override fun removeCookiesForUrl(profileName: String?, url: String) {
            val prof = profileName ?: "default"
            cookies.remove(prof to url)
        }

        override fun removeAllCookies(callback: ((Boolean) -> Unit)?) {
            cookies.clear()
            callback?.invoke(true)
        }

        override fun addCookieChangeListener(listener: com.swift.browser.cookieengine.OnCookieChangeListener) {}
        override fun removeCookieChangeListener(listener: com.swift.browser.cookieengine.OnCookieChangeListener) {}

        override fun setupNormalCookies(webView: android.webkit.WebView) {}
        override fun setupPrivateProfile(webView: android.webkit.WebView, profileName: String): String {
            activeProfiles.add(profileName)
            return profileName
        }

        override fun getProfileCookieManager(profileName: String): CookieManager? {
            return if (activeProfiles.contains(profileName)) CookieManager.getInstance() else null
        }

        override fun deletePrivateProfile(profileName: String): Boolean {
            return activeProfiles.remove(profileName)
        }

        override fun setupIncognitoCookies(webView: android.webkit.WebView) {}
        override fun clearIncognitoCookies() {
            activeProfiles.removeAll { it.startsWith("private_profile_") }
        }
    }

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        registry = ExtensionRegistry()
        messageBus = MessageBus()
        eventManager = EventManager(messageBus)
        permissionAdapter = ExtensionPermissionAdapter(context)
        permissionAdapter.setRegistry(registry)
        permissionAdapter.setEventManager(eventManager)

        val ext = ParsedExtension(
            id = "test_ext_id",
            name = "Test Cookie Extension",
            version = "1.0",
            description = "Description",
            manifestVersion = 3,
            permissions = listOf("cookies"),
            hostPermissions = listOf("https://example.com/*"),
            backgroundScripts = emptyList(),
            isServiceWorker = false,
            contentScripts = emptyList(),
            actionPopup = "",
            optionsPage = "",
            manifestJson = "{}",
            allowedInPrivate = true
        )
        registry.register(ext)
        permissionAdapter.onExtensionRegistered(ext)

        mockCookieEngine = FakeCookieEngine()
        adapter = ExtensionCookieAdapter(
            context = context,
            permissionAdapter = permissionAdapter,
            registry = registry,
            eventManager = eventManager,
            cookieEngine = mockCookieEngine
        )
    }

    @Test
    fun testNormalProfileCookieSetAndGet() {
        val sender = ExtensionSender(extensionId = "test_ext_id", isPrivate = false)
        val setDetails = JSONObject().apply {
            put("url", "https://example.com/")
            put("name", "session_token")
            put("value", "abc123xyz")
        }

        val setResult = adapter.set(sender, setDetails)
        assertNotNull(setResult)
        assertEquals("session_token", setResult!!.getString("name"))

        val getDetails = JSONObject().apply {
            put("url", "https://example.com/")
            put("name", "session_token")
        }
        val getResult = adapter.get(sender, getDetails)
        assertNotNull(getResult)
        assertEquals("abc123xyz", getResult!!.getString("value"))
        assertEquals("0", getResult.getString("storeId"))
        assertTrue(getResult.getBoolean("metadataPartial"))
    }

    @Test
    fun testPrivateProfileIsolationNoFallback() {
        val senderPrivateAllowed = ExtensionSender(
            extensionId = "test_ext_id",
            isPrivate = true,
            privateSessionId = "sess_999"
        )

        // Set private profile as active in fake engine
        val privateProfileName = CookieEngine.getPrivateProfileName("sess_999")
        mockCookieEngine.activeProfiles.add(privateProfileName)

        val setPrivateDetails = JSONObject().apply {
            put("url", "https://example.com/")
            put("name", "private_token")
            put("value", "secret_p123")
        }

        val setResult = adapter.set(senderPrivateAllowed, setPrivateDetails)
        assertNotNull(setResult)
        assertEquals("1", setResult!!.getString("storeId"))

        // Verify normal sender cannot see private cookie
        val senderNormal = ExtensionSender(extensionId = "test_ext_id", isPrivate = false)
        val normalGet = adapter.get(senderNormal, JSONObject().apply {
            put("url", "https://example.com/")
            put("name", "private_token")
        })
        assertNull(normalGet)

        // Now delete private profile and verify private get fails without falling back to normal profile
        mockCookieEngine.deletePrivateProfile(privateProfileName)
        val privateGetAfterDelete = adapter.get(senderPrivateAllowed, JSONObject().apply {
            put("url", "https://example.com/")
            put("name", "private_token")
        })
        assertNull(privateGetAfterDelete)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testCRLFHeaderInjectionRejected() {
        val sender = ExtensionSender(extensionId = "test_ext_id", isPrivate = false)
        val setDetails = JSONObject().apply {
            put("url", "https://example.com/")
            put("name", "bad_name\r\nSet-Cookie: injected=1")
            put("value", "val")
        }
        adapter.set(sender, setDetails)
    }

    @Test(expected = SecurityException::class)
    fun testUnpermittedHostAccessDenied() {
        val sender = ExtensionSender(extensionId = "test_ext_id", isPrivate = false)
        val setDetails = JSONObject().apply {
            put("url", "https://unauthorized-domain.com/")
            put("name", "foo")
            put("value", "bar")
        }
        adapter.set(sender, setDetails)
    }
}
