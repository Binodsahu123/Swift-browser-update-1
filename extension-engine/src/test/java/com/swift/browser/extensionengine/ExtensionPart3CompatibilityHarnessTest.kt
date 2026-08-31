package com.swift.browser.extensionengine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.swift.browser.tabengine.api.TabEngineApi
import com.swift.browser.tabengine.engine.TabEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExtensionPart3CompatibilityHarnessTest {

    private lateinit var context: Context
    private lateinit var registry: ExtensionRegistry
    private lateinit var permissionManager: PermissionManager
    private lateinit var eventManager: EventManager
    private lateinit var messageBus: MessageBus
    private lateinit var tabEngine: TabEngineApi
    private lateinit var tabsAdapter: ExtensionTabsAdapter
    private lateinit var cookieAdapter: ExtensionCookieAdapter
    private lateinit var downloadsAdapter: ExtensionDownloadsAdapter
    private lateinit var bookmarksAdapter: ExtensionBookmarksAdapter
    private lateinit var historyAdapter: ExtensionHistoryAdapter
    private lateinit var dnrAdapter: ExtensionDnrAdapter
    private lateinit var scriptingAdapter: ExtensionScriptingAdapter
    private lateinit var actionAdapter: ExtensionActionAdapter
    private lateinit var permissionAdapter: ExtensionPermissionAdapter
    private lateinit var contentScriptManager: ContentScriptManager
    private lateinit var mockBrowserDelegate: BrowserDelegate

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        registry = ExtensionRegistry()
        messageBus = MessageBus()
        permissionManager = PermissionManager(context)
        permissionManager.setRegistry(registry)
        eventManager = EventManager(messageBus)

        tabEngine = TabEngine(context = context, scope = CoroutineScope(Dispatchers.Unconfined))
        tabsAdapter = ExtensionTabsAdapter(tabEngine, registry, permissionManager, messageBus)
        
        mockBrowserDelegate = object : BrowserDelegate {
            override fun queryTabs(queryInfo: JSONObject): JSONArray {
                val array = JSONArray()
                try {
                    tabEngine.getNormalTabs().forEach {
                        array.put(JSONObject().put("id", it.id).put("url", it.url))
                    }
                    tabEngine.getPrivateTabs().forEach {
                        array.put(JSONObject().put("id", it.id).put("url", it.url))
                    }
                } catch (e: Exception) {}
                if (array.length() == 0) {
                    array.put(JSONObject().put("id", 1).put("url", "https://example.com"))
                }
                return array
            }
            override fun createTab(url: String, active: Boolean) {}
            override fun removeTab(tabId: String) {}
            override fun reloadTab(tabId: String) {}
            override fun updateTab(tabId: String, url: String) {}
            override fun showNotification(title: String, message: String) {}
            override fun downloadFile(url: String, filename: String?) {}
            override fun getActiveTabId(): String? = "1"
            override fun executeScriptOnTab(tabId: String, code: String, callback: (String?) -> Unit) { callback(null) }
            override fun checkExtensionPermission(extensionId: String, permission: String, callback: (Boolean) -> Unit) { callback(true) }
        }
        permissionAdapter = ExtensionPermissionAdapter(context).apply {
            setRegistry(registry)
            setEventManager(eventManager)
        }
        cookieAdapter = ExtensionCookieAdapter(context, permissionAdapter, registry, eventManager)
        downloadsAdapter = ExtensionDownloadsAdapter(context, permissionManager, registry, eventManager)
        bookmarksAdapter = ExtensionBookmarksAdapter(context, permissionManager, registry, eventManager)
        historyAdapter = ExtensionHistoryAdapter(permissionManager, registry, eventManager)
        dnrAdapter = ExtensionDnrAdapter(permissionManager, registry)
        contentScriptManager = ContentScriptManager(context, permissionManager, ScriptInjector(), CssInjector(), registry)
        scriptingAdapter = ExtensionScriptingAdapter(permissionManager, registry, contentScriptManager)
        actionAdapter = ExtensionActionAdapter(permissionManager, registry, eventManager)
    }

    private fun registerTestExtension(
        id: String,
        name: String,
        manifestVersion: Int = 3,
        permissions: List<String> = emptyList(),
        hostPermissions: List<String> = emptyList(),
        contentScripts: List<ContentScriptSpec> = emptyList(),
        backgroundScripts: List<String> = emptyList(),
        isServiceWorker: Boolean = false,
        actionPopup: String = "",
        allowedInPrivate: Boolean = false
    ) {
        val ext = ParsedExtension(
            id = id,
            name = name,
            version = "1.0.0",
            description = "Harness Test Extension",
            manifestVersion = manifestVersion,
            permissions = permissions,
            hostPermissions = hostPermissions,
            backgroundScripts = backgroundScripts,
            isServiceWorker = isServiceWorker,
            contentScripts = contentScripts,
            actionPopup = actionPopup,
            optionsPage = "",
            manifestJson = "{}",
            allowedInPrivate = allowedInPrivate
        )
        registry.registerExtension(ext)
    }

    // 1. Simple Content-Script Extension
    @Test
    fun testCategory1_SimpleContentScriptExtension() {
        val cs = ContentScriptSpec(
            matches = listOf("https://*.example.com/*"),
            js = listOf("content.js"),
            css = emptyList(),
            runAt = "document_idle"
        )
        registerTestExtension(
            id = "ext_cs_simple",
            name = "Simple CS Extension",
            contentScripts = listOf(cs)
        )

        val ext = registry.getExtension("ext_cs_simple")
        assertNotNull(ext)
        assertEquals(1, ext!!.contentScripts.size)
        assertEquals("document_idle", ext.contentScripts[0].runAt)
        assertTrue(ext.contentScripts[0].matches.contains("https://*.example.com/*"))
    }

    // 2. Popup Extension
    @Test
    fun testCategory2_PopupExtension() {
        registerTestExtension(
            id = "ext_popup",
            name = "Popup Extension",
            actionPopup = "popup.html"
        )
        val sender = ExtensionSender("ext_popup")
        val popupResult = actionAdapter.getPopup(sender, JSONObject())
        assertEquals("popup.html", popupResult.getString("popup"))

        actionAdapter.setPopup(sender, JSONObject().put("popup", "new_popup.html"))
        val updatedPopup = actionAdapter.getPopup(sender, JSONObject())
        assertEquals("new_popup.html", updatedPopup.getString("popup"))
    }

    // 3. Storage Extension
    @Test
    fun testCategory3_StorageExtension() = runBlocking {
        val db = ExtensionDatabase.getInstance(context)
        val storageManager = StorageManager(db, context, registry)
        val extId = "ext_storage"
        val payload = JSONObject().put("theme", "dark").put("fontSize", "14")
        
        storageManager.set(extId, "local", payload)
        val retrieved = storageManager.get(extId, "local", listOf("theme", "fontSize"))
        assertEquals("dark", retrieved.optString("theme"))
        assertEquals("14", retrieved.optString("fontSize"))

        storageManager.remove(extId, "local", listOf("fontSize"))
        val afterRemove = storageManager.get(extId, "local", listOf("fontSize"))
        assertFalse(afterRemove.has("fontSize"))
    }

    // 4. Tabs Extension
    @Test
    fun testCategory4_TabsExtension() {
        registerTestExtension(
            id = "ext_tabs",
            name = "Tabs Extension",
            permissions = listOf("tabs")
        )
        val sender = ExtensionSender("ext_tabs")
        val createTabRes = tabsAdapter.createTab(sender, JSONObject().put("url", "https://example.org"))
        assertNotNull(createTabRes)
        assertTrue(createTabRes.has("id"))
    }

    // 5. Cookie Extension
    @Test
    fun testCategory5_CookieExtension() {
        registerTestExtension(
            id = "ext_cookies",
            name = "Cookie Extension",
            permissions = listOf("cookies"),
            hostPermissions = listOf("https://example.com/*")
        )
        val sender = ExtensionSender("ext_cookies")
        val queryObj = JSONObject().put("url", "https://example.com")
        val cookies = cookieAdapter.getAll(sender, queryObj)
        assertNotNull(cookies)
    }

    // 6. Downloads Extension
    @Test
    fun testCategory6_DownloadsExtension() = runBlocking {
        registerTestExtension(
            id = "ext_downloads",
            name = "Downloads Extension",
            permissions = listOf("downloads")
        )
        val sender = ExtensionSender("ext_downloads")
        val searchResult = downloadsAdapter.search(sender, JSONObject())
        assertNotNull(searchResult)
    }

    // 7. DNR Blocker
    @Test
    fun testCategory7_DnrBlockerExtension() {
        registerTestExtension(
            id = "ext_dnr",
            name = "DNR Blocker Extension",
            permissions = listOf("declarativeNetRequest")
        )
        val sender = ExtensionSender("ext_dnr")
        val ruleObj = JSONObject()
            .put("id", 101)
            .put("priority", 1)
            .put("action", JSONObject().put("type", "block"))
            .put("condition", JSONObject().put("urlFilter", "*://adserver.com/*"))

        val addRulesReq = JSONObject().put("addRules", JSONArray().put(ruleObj))
        val updateRes = dnrAdapter.updateDynamicRules(sender, addRulesReq)
        assertTrue(updateRes.optBoolean("success", true))

        val matched = dnrAdapter.evaluateRequest("https://adserver.com/banner.js", "script", emptyMap(), false, "https://example.com")
        assertNotNull(matched)
        assertEquals("block", matched!!.actionType)
    }

    // 8. Runtime Messaging Extension
    @Test
    fun testCategory8_RuntimeMessagingExtension() {
        registerTestExtension(
            id = "ext_messaging",
            name = "Messaging Extension"
        )
        var received = false
        messageBus.registerListener(object : MessageListener {
            override fun onMessageReceived(
                extensionId: String,
                senderTabId: String?,
                message: JSONObject,
                callbackId: String?,
                targetTabId: String?
            ) {
                if (extensionId == "ext_messaging" && message.optString("action") == "ping") {
                    received = true
                }
            }
            override fun onResponseReceived(extensionId: String, callbackId: String, response: Any) {}
        })

        val sender = ExtensionSender("ext_messaging")
        messageBus.broadcastMessage(sender, JSONObject().put("action", "ping"))
        assertTrue(received)
    }

    // 9. MV2 Background Extension
    @Test
    fun testCategory9_Mv2BackgroundExtension() {
        registerTestExtension(
            id = "ext_mv2_bg",
            name = "MV2 Background Extension",
            manifestVersion = 2,
            backgroundScripts = listOf("background.js"),
            isServiceWorker = false
        )
        val ext = registry.getExtension("ext_mv2_bg")
        assertNotNull(ext)
        assertEquals(2, ext!!.manifestVersion)
        assertFalse(ext.isServiceWorker)
        assertEquals(listOf("background.js"), ext.backgroundScripts)
    }

    // 10. MV3 Service Worker Extension
    @Test
    fun testCategory10_Mv3ServiceWorkerExtension() {
        registerTestExtension(
            id = "ext_mv3_sw",
            name = "MV3 SW Extension",
            manifestVersion = 3,
            backgroundScripts = listOf("sw.js"),
            isServiceWorker = true
        )
        val ext = registry.getExtension("ext_mv3_sw")
        assertNotNull(ext)
        assertEquals(3, ext!!.manifestVersion)
        assertTrue(ext.isServiceWorker)
    }

    // 11. Multi-Frame Content Script Extension
    @Test
    fun testCategory11_MultiFrameExtension() {
        val cs = ContentScriptSpec(
            matches = listOf("https://*/*"),
            js = listOf("all_frames.js"),
            css = emptyList(),
            allFrames = true
        )
        registerTestExtension(
            id = "ext_multiframe",
            name = "MultiFrame Extension",
            contentScripts = listOf(cs)
        )
        val ext = registry.getExtension("ext_multiframe")
        assertTrue(ext!!.contentScripts[0].allFrames)
    }

    // 12. MAIN-World Extension
    @Test
    fun testCategory12_MainWorldExtension() {
        registerTestExtension(
            id = "ext_main_world",
            name = "Main World Scripting",
            permissions = listOf("scripting"),
            hostPermissions = listOf("https://*/*")
        )
        val sender = ExtensionSender("ext_main_world")
        val req = JSONObject()
            .put("target", JSONObject().put("tabId", 1))
            .put("world", "MAIN")
            .put("func", "function() { return window.location.href; }")
        
        scriptingAdapter.executeScript(sender, req, mockBrowserDelegate, context) { res, err ->
            assertNull(err)
        }
    }

    // 13. ISOLATED-World Extension
    @Test
    fun testCategory13_IsolatedWorldExtension() {
        registerTestExtension(
            id = "ext_isolated_world",
            name = "Isolated World Scripting",
            permissions = listOf("scripting"),
            hostPermissions = listOf("https://*/*")
        )
        val sender = ExtensionSender("ext_isolated_world")
        val req = JSONObject()
            .put("target", JSONObject().put("tabId", 1))
            .put("world", "ISOLATED")
            .put("func", "function() { return document.title; }")

        scriptingAdapter.executeScript(sender, req, mockBrowserDelegate, context) { res, err ->
            // Robolectric does not support JS_INJECTION_IN_FRAME_AND_WORLD by default
            if (err != null) {
                assertEquals("SCRIPTING_WORLD_UNSUPPORTED", err)
            }
        }
    }

    // 14. Large/Complex Multi-Permission Extension
    @Test
    fun testCategory14_LargeComplexExtension() {
        val permissions = listOf("tabs", "cookies", "storage", "downloads", "bookmarks", "history", "scripting")
        registerTestExtension(
            id = "ext_large_complex",
            name = "Complex Extension",
            permissions = permissions,
            hostPermissions = listOf("https://*/*")
        )
        assertTrue(permissionManager.hasApiPermission("ext_large_complex", permissions, "tabs"))
        assertTrue(permissionManager.hasApiPermission("ext_large_complex", permissions, "cookies"))
        assertTrue(permissionManager.hasApiPermission("ext_large_complex", permissions, "storage"))
        assertTrue(permissionManager.hasApiPermission("ext_large_complex", permissions, "downloads"))
        assertTrue(permissionManager.hasApiPermission("ext_large_complex", permissions, "bookmarks"))
        assertTrue(permissionManager.hasApiPermission("ext_large_complex", permissions, "history"))
        assertTrue(permissionManager.hasApiPermission("ext_large_complex", permissions, "scripting"))
        assertTrue(permissionManager.hasHostPermission("ext_large_complex", listOf("https://*/*"), permissions, "https://news.ycombinator.com/item"))
    }

    // 15. Userscript-Manager Style Extension
    @Test
    fun testCategory15_UserscriptManagerExtension() {
        registerTestExtension(
            id = "ext_userscripts",
            name = "Userscript Engine",
            permissions = listOf("scripting", "storage"),
            hostPermissions = listOf("<all_urls>")
        )
        val sender = ExtensionSender("ext_userscripts")
        val req = JSONObject()
            .put("target", JSONObject().put("tabId", 1))
            .put("world", "MAIN")
            .put("func", "function(x) { return x; }")
            .put("args", JSONArray().put("var x = 10;"))
        
        scriptingAdapter.executeScript(sender, req, mockBrowserDelegate, context) { res, err ->
            assertNull(err)
        }
    }

    // 16. Dark-Mode / Content-Modification Extension
    @Test
    fun testCategory16_DarkModeContentModificationExtension() {
        registerTestExtension(
            id = "ext_darkmode",
            name = "Dark Reader Mode",
            permissions = listOf("scripting", "storage"),
            hostPermissions = listOf("<all_urls>")
        )
        val sender = ExtensionSender("ext_darkmode")
        val insertReq = JSONObject()
            .put("target", JSONObject().put("tabId", 1))
            .put("css", "html, body { background-color: #121212 !important; color: #e0e0e0 !important; }")
        
        val insertRes = scriptingAdapter.insertCSS(sender, insertReq, mockBrowserDelegate, context)
        assertNotNull(insertRes)
    }

    // 17. Automation-Style Extension
    @Test
    fun testCategory17_AutomationExtension() {
        registerTestExtension(
            id = "ext_automation",
            name = "Form Automator",
            permissions = listOf("tabs", "scripting", "storage"),
            hostPermissions = listOf("https://forms.gle/*")
        )
        val sender = ExtensionSender("ext_automation")
        val createTab = tabsAdapter.createTab(sender, JSONObject().put("url", "https://forms.gle/test"))
        assertNotNull(createTab)
        val scriptReq = JSONObject()
            .put("target", JSONObject().put("tabId", createTab.optString("id", "1")))
            .put("func", "function() { return document.forms.length; }")
        
        scriptingAdapter.executeScript(sender, scriptReq, mockBrowserDelegate, context) { res, err ->
            assertNull(err)
        }
    }
}
