package com.swift.browser.extensionengine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExtensionPrivacyTest {

    private lateinit var context: Context
    private val manifestParser = ManifestParser()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testDefaultAllowedInPrivateIsFalse() {
        val manifestJson = """
            {
              "manifest_version": 3,
              "name": "Test Extension",
              "version": "1.0"
            }
        """.trimIndent()
        val parsed = manifestParser.parse(manifestJson)
        assertFalse("Extension should not be allowed in private mode by default", parsed.allowedInPrivate)
        @Suppress("DEPRECATION")
        assertFalse("Legacy allowedInIncognito getter should match allowedInPrivate", parsed.allowedInIncognito)
    }

    @Test
    fun testParseAllowedInPrivateManifestField() {
        val manifestJson = """
            {
              "manifest_version": 3,
              "name": "Private Allowed Extension",
              "version": "1.0",
              "allowedInPrivate": true
            }
        """.trimIndent()
        val parsed = manifestParser.parse(manifestJson)
        assertTrue("Extension with allowedInPrivate=true in manifest should be allowed", parsed.allowedInPrivate)
    }

    @Test
    fun testLegacyAllowedInIncognitoManifestMigration() {
        val manifestJson = """
            {
              "manifest_version": 3,
              "name": "Legacy Incognito Extension",
              "version": "1.0",
              "allowedInIncognito": true
            }
        """.trimIndent()
        val parsed = manifestParser.parse(manifestJson)
        assertTrue("Legacy allowedInIncognito=true should parse into allowedInPrivate", parsed.allowedInPrivate)
    }

    @Test
    fun testPermissionManagerAllowedInPrivateToggle() {
        val registry = ExtensionRegistry()
        val parsed = ParsedExtension(
            id = "ext_toggle",
            name = "Toggle Ext",
            version = "1.0",
            description = "",
            manifestVersion = 3,
            permissions = emptyList(),
            hostPermissions = emptyList(),
            backgroundScripts = emptyList(),
            isServiceWorker = false,
            contentScripts = emptyList(),
            actionPopup = "",
            optionsPage = "",
            manifestJson = "{}",
            allowedInPrivate = false
        )
        registry.register(parsed)

        val permissionManager = PermissionManager(context)
        permissionManager.setRegistry(registry)

        assertFalse(permissionManager.isAllowedInPrivate("ext_toggle"))

        permissionManager.setAllowedInPrivate("ext_toggle", true)
        assertTrue(permissionManager.isAllowedInPrivate("ext_toggle"))
        assertTrue(registry.getExtension("ext_toggle")?.allowedInPrivate == true)
    }

    @Test
    fun testContentScriptInjectionBlockedInPrivateTabWhenNotAllowed() {
        val permissionManager = PermissionManager(context)
        val scriptInjector = ScriptInjector()
        val cssInjector = CssInjector()
        var evaluatedCount = 0

        val evaluator = object : ScriptEvaluator {
            override fun evaluateJavascript(code: String, callback: ((String?) -> Unit)?) {
                evaluatedCount++
                callback?.invoke(null)
            }
            override fun post(action: () -> Unit) {
                action()
            }
        }

        val contentScriptManager = ContentScriptManager(context, permissionManager, scriptInjector, cssInjector) { 1 }

        val spec = ContentScriptSpec(matches = listOf("https://example.com/*"), js = listOf("content.js"), css = emptyList())
        val ext = ParsedExtension(
            id = "disallowed_ext",
            name = "Disallowed Ext",
            version = "1.0",
            description = "",
            manifestVersion = 3,
            permissions = emptyList(),
            hostPermissions = listOf("https://example.com/*"),
            backgroundScripts = emptyList(),
            isServiceWorker = false,
            contentScripts = listOf(spec),
            actionPopup = "",
            optionsPage = "",
            manifestJson = "{}",
            allowedInPrivate = false
        )

        contentScriptManager.matchAndInject(
            evaluator = evaluator,
            url = "https://example.com/page",
            parsedExtensions = listOf(ext),
            runAtFilter = "document_idle",
            bootstrapScriptProvider = { "" },
            isPrivate = true,
            privateSessionId = "session_abc"
        )

        assertEquals("Content script must not be injected into private tab when extension is disallowed in private", 0, evaluatedCount)
    }

    @Test
    fun testBackgroundWorkerBlockedInPrivateWhenNotAllowed() {
        val permissionManager = PermissionManager(context)
        val scriptInjector = ScriptInjector()
        val messageBus = MessageBus()
        val bgManager = BackgroundScriptManager(context, scriptInjector, messageBus)

        val ext = ParsedExtension(
            id = "disallowed_ext_bg",
            name = "Disallowed Ext BG",
            version = "1.0",
            description = "",
            manifestVersion = 3,
            permissions = emptyList(),
            hostPermissions = emptyList(),
            backgroundScripts = listOf("background.js"),
            isServiceWorker = false,
            contentScripts = emptyList(),
            actionPopup = "",
            optionsPage = "",
            manifestJson = "{}",
            allowedInPrivate = false
        )

        bgManager.startBackgroundWorker(ext, "", isPrivate = true, permissionManager = permissionManager)
        assertFalse("Background WebView should not be spawned for disallowed private extension", bgManager.hasBackgroundWorker("disallowed_ext_bg"))
    }

    @Test
    fun testPrivateCookieAccessBlockedWhenNotAllowed() {
        val registry = ExtensionRegistry()
        val permissionManager = PermissionManager(context)
        permissionManager.setRegistry(registry)
        val cookieAccess = ExtensionCookieAccess(context, permissionManager, registry)

        val ext = ParsedExtension(
            id = "cookie_ext",
            name = "Cookie Ext",
            version = "1.0",
            description = "",
            manifestVersion = 3,
            permissions = listOf("cookies"),
            hostPermissions = listOf("https://example.com/*"),
            backgroundScripts = emptyList(),
            isServiceWorker = false,
            contentScripts = emptyList(),
            actionPopup = "",
            optionsPage = "",
            manifestJson = "{}",
            allowedInPrivate = false
        )
        registry.register(ext)

        assertFalse(cookieAccess.hasCookieHostPermission("cookie_ext", "https://example.com", isPrivate = true))
        assertNull(cookieAccess.getCookie("cookie_ext", "https://example.com", "session_token", isPrivate = true))
        assertNull(cookieAccess.getAllCookies("cookie_ext", "https://example.com", isPrivate = true))
        assertFalse(cookieAccess.setCookie("cookie_ext", "https://example.com", "key", "val", isPrivate = true))
    }

    @Test
    fun testIsolatedPrivateStoragePerSession() = kotlinx.coroutines.runBlocking {
        val fakeDao = FakeStorageDao()
        val fakeDb = FakeExtensionDatabase(fakeDao)

        val storageManager = StorageManager(fakeDb)

        val items = JSONObject().apply {
            put("secret_token", "12345")
        }

        // Write in private session A
        storageManager.set("ext_storage", "local", items, isPrivate = true, privateSessionId = "session_A")

        // Read in private session A -> present
        val resA = storageManager.get("ext_storage", "local", null, isPrivate = true, privateSessionId = "session_A")
        assertEquals("12345", resA.optString("secret_token"))

        // Read in private session B -> absent (session isolated)
        val resB = storageManager.get("ext_storage", "local", null, isPrivate = true, privateSessionId = "session_B")
        assertFalse(resB.has("secret_token"))

        // Database DAO should never have received persistent insert
        assertEquals(0, fakeDao.insertedCount)

        // Clear private session A
        storageManager.clearPrivateStorage("session_A")
        val resAAfterClear = storageManager.get("ext_storage", "local", null, isPrivate = true, privateSessionId = "session_A")
        assertFalse(resAAfterClear.has("secret_token"))
    }
}

private class FakeExtensionDatabase(private val dao: StorageDao) : ExtensionDatabase() {
    override fun extensionDao(): ExtensionDao = error("Not needed")
    override fun storageDao(): StorageDao = dao
    override fun createOpenHelper(config: androidx.room.DatabaseConfiguration): androidx.sqlite.db.SupportSQLiteOpenHelper = error("Not needed")
    override fun createInvalidationTracker(): androidx.room.InvalidationTracker = error("Not needed")
    override fun clearAllTables() {}
}

private class FakeStorageDao : StorageDao {
    var insertedCount = 0
    override suspend fun insertStorage(items: List<StorageEntity>) { insertedCount += items.size }
    override suspend fun getStorageByArea(extensionId: String, area: String): List<StorageEntity> = emptyList()
    override suspend fun getStorageByKeys(extensionId: String, area: String, keys: List<String>): List<StorageEntity> = emptyList()
    override suspend fun deleteStorageByKeys(extensionId: String, area: String, keys: List<String>) {}
    override suspend fun clearStorage(extensionId: String, area: String) {}
}
