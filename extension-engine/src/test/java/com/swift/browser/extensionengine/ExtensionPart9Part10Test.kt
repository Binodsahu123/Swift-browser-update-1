package com.swift.browser.extensionengine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExtensionPart9Part10Test {

    private lateinit var context: Context
    private lateinit var permissionManager: PermissionManager
    private lateinit var registry: ExtensionRegistry
    private lateinit var messageBus: MessageBus
    private lateinit var eventManager: EventManager
    private lateinit var dnrAdapter: ExtensionDnrAdapter
    private lateinit var webRequestAdapter: ExtensionWebRequestAdapter
    private lateinit var contentScriptManager: ContentScriptManager
    private lateinit var scriptingAdapter: ExtensionScriptingAdapter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        registry = ExtensionRegistry()
        messageBus = MessageBus()
        permissionManager = PermissionManager(context)
        permissionManager.setRegistry(registry)
        eventManager = EventManager(messageBus)
        dnrAdapter = ExtensionDnrAdapter(permissionManager, registry)
        webRequestAdapter = ExtensionWebRequestAdapter(permissionManager, registry, eventManager, dnrAdapter)
        contentScriptManager = ContentScriptManager(context, permissionManager, ScriptInjector(), CssInjector(), registry)
        scriptingAdapter = ExtensionScriptingAdapter(permissionManager, registry, contentScriptManager)

        val manifest = ParsedExtension(
            id = "ext_p9_10",
            name = "Test Extension Part 9 and 10",
            version = "1.0",
            description = "Test extension",
            manifestVersion = 3,
            permissions = listOf("declarativeNetRequest", "webRequest", "scripting"),
            hostPermissions = listOf("*://*.example.com/*"),
            backgroundScripts = emptyList(),
            isServiceWorker = false,
            contentScripts = emptyList(),
            actionPopup = "",
            optionsPage = "",
            manifestJson = "{}",
            allowedInPrivate = true
        )
        registry.registerExtension(manifest)
    }

    // --- Part 9: DeclarativeNetRequest Tests ---

    @Test
    fun testDnrAddAndGetDynamicRules() {
        val sender = ExtensionSender(extensionId = "ext_p9_10", isPrivate = false)
        val options = JSONObject().apply {
            val addRules = JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 1)
                    put("priority", 1)
                    put("action", JSONObject().put("type", "block"))
                    put("condition", JSONObject().put("urlFilter", "example.com/bad"))
                })
            }
            put("addRules", addRules)
        }

        val updateRes = dnrAdapter.updateDynamicRules(sender, options)
        assertEquals("success", updateRes.getString("status"))

        val rules = dnrAdapter.getDynamicRules(sender)
        assertEquals(1, rules.length())
        assertEquals(1, rules.getJSONObject(0).getInt("id"))
    }

    @Test
    fun testDnrRuleEvaluationPriority() {
        val sender = ExtensionSender(extensionId = "ext_p9_10", isPrivate = false)
        val options = JSONObject().apply {
            val addRules = JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 1)
                    put("priority", 1)
                    put("action", JSONObject().put("type", "block"))
                    put("condition", JSONObject().put("urlFilter", "example.com/api"))
                })
                put(JSONObject().apply {
                    put("id", 2)
                    put("priority", 10)
                    put("action", JSONObject().put("type", "allow"))
                    put("condition", JSONObject().put("urlFilter", "example.com/api"))
                })
            }
            put("addRules", addRules)
        }
        dnrAdapter.updateDynamicRules(sender, options)

        val match = dnrAdapter.evaluateRequest("https://example.com/api", "xmlhttprequest", requestHeaders = emptyMap(), isPrivate = false)
        assertNotNull(match)
        assertEquals("allow", match?.actionType)
    }

    @Test
    fun testDnrPrivateModeIsolation() {
        val sender = ExtensionSender(extensionId = "ext_p9_10", isPrivate = false)
        val options = JSONObject().apply {
            val addRules = JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 1)
                    put("priority", 1)
                    put("action", JSONObject().put("type", "block"))
                    put("condition", JSONObject().put("urlFilter", "example.com/block"))
                })
            }
            put("addRules", addRules)
        }
        dnrAdapter.updateDynamicRules(sender, options)

        permissionManager.setAllowedInPrivate("ext_p9_10", false)

        val privateMatch = dnrAdapter.evaluateRequest("https://example.com/block", "main_frame", requestHeaders = emptyMap(), isPrivate = true)
        assertNull(privateMatch)

        val normalMatch = dnrAdapter.evaluateRequest("https://example.com/block", "main_frame", requestHeaders = emptyMap(), isPrivate = false)
        assertNotNull(normalMatch)
        assertEquals("block", normalMatch?.actionType)
    }

    // --- Part 9: WebRequest Tests ---

    @Test
    fun testWebRequestInterceptAndEventNotification() {
        var eventTriggered = false
        eventManager.addListener("webRequest.onBeforeRequest", "ext_p9_10")

        val request = WebRequestData(
            requestId = "req_123",
            url = "https://example.com/test",
            method = "GET",
            type = "main_frame",
            tabId = 1
        )

        val result = webRequestAdapter.interceptRequest(request)
        assertTrue(result is WebRequestInterceptResult.Continue)
    }

    // --- Part 10: Scripting API Tests ---

    @Test
    fun testScriptingExecuteScriptWithArgsAndReturn() {
        val sender = ExtensionSender(extensionId = "ext_p9_10", isPrivate = false)
        val delegate = object : FakeBrowserDelegate() {
            override fun queryTabs(queryInfo: JSONObject): JSONArray {
                return JSONArray().apply {
                    put(JSONObject().apply {
                        put("id", "tab_10")
                        put("url", "https://example.com/page")
                    })
                }
            }

            override fun executeScriptOnTab(tabId: String, code: String, callback: (String?) -> Unit) {
                callback("{\"status\":\"success\",\"result\":15}")
            }
        }

        val spec = JSONObject().apply {
            put("target", JSONObject().put("tabId", "tab_10"))
            put("func", "function(a, b) { return a + b; }")
            put("args", JSONArray().apply { put(7); put(8) })
        }

        var successArr: JSONArray? = null
        var errorMsg: String? = null

        scriptingAdapter.executeScript(sender, spec, delegate, context) { res, err ->
            successArr = res
            errorMsg = err
        }

        assertNull(errorMsg)
        assertNotNull(successArr)
        assertEquals(1, successArr?.length())
        assertEquals(15, successArr?.getJSONObject(0)?.getInt("result"))
    }

    @Test
    fun testScriptingProtectedSchemeBlocking() {
        val sender = ExtensionSender(extensionId = "ext_p9_10", isPrivate = false)
        val delegate = object : FakeBrowserDelegate() {
            override fun queryTabs(queryInfo: JSONObject): JSONArray {
                return JSONArray().apply {
                    put(JSONObject().apply {
                        put("id", "tab_swift")
                        put("url", "swift://settings")
                    })
                }
            }
        }

        val spec = JSONObject().apply {
            put("target", JSONObject().put("tabId", "tab_swift"))
            put("func", "function() { return 'hack'; }")
        }

        var errorMsg: String? = null
        scriptingAdapter.executeScript(sender, spec, delegate, context) { _, err ->
            errorMsg = err
        }

        assertNotNull(errorMsg)
        assertTrue(errorMsg?.contains("SecurityError") == true)
    }

    @Test
    fun testScriptingDynamicContentScriptRegistration() {
        val sender = ExtensionSender(extensionId = "ext_p9_10", isPrivate = false)
        val scripts = JSONArray().apply {
            put(JSONObject().apply {
                put("id", "dyn_script_1")
                put("matches", JSONArray().apply { put("*://*.example.com/*") })
                put("js", JSONArray().apply { put("script.js") })
            })
        }

        val regRes = scriptingAdapter.registerContentScripts(sender, scripts)
        assertEquals("success", regRes.getString("status"))

        val registered = scriptingAdapter.getRegisteredContentScripts(sender)
        assertEquals(1, registered.length())
        assertEquals("dyn_script_1", registered.getJSONObject(0).getString("id"))

        val unregRes = scriptingAdapter.unregisterContentScripts(sender, JSONArray().apply { put("dyn_script_1") })
        assertEquals("success", unregRes.getString("status"))

        val remaining = scriptingAdapter.getRegisteredContentScripts(sender)
        assertEquals(0, remaining.length())
    }

    @Test
    fun testScriptingAllFramesBlocked() {
        val sender = ExtensionSender(extensionId = "ext_p9_10", isPrivate = false)
        val delegate = object : FakeBrowserDelegate() {
            override fun queryTabs(queryInfo: JSONObject): JSONArray {
                return JSONArray().apply {
                    put(JSONObject().apply {
                        put("id", "tab_10")
                        put("url", "https://example.com/page")
                    })
                }
            }
        }
        val spec = JSONObject().apply {
            put("target", JSONObject().apply {
                put("tabId", "tab_10")
                put("allFrames", true)
            })
            put("func", "function() { return 1; }")
        }
        var errorMsg: String? = null
        scriptingAdapter.executeScript(sender, spec, delegate, context) { _, err ->
            errorMsg = err
        }
        assertEquals("SCRIPTING_FRAME_TARGETING_UNSUPPORTED", errorMsg)
    }

    @Test
    fun testScriptingNonZeroFrameIdBlocked() {
        val sender = ExtensionSender(extensionId = "ext_p9_10", isPrivate = false)
        val delegate = object : FakeBrowserDelegate() {
            override fun queryTabs(queryInfo: JSONObject): JSONArray {
                return JSONArray().apply {
                    put(JSONObject().apply {
                        put("id", "tab_10")
                        put("url", "https://example.com/page")
                    })
                }
            }
        }
        val spec = JSONObject().apply {
            put("target", JSONObject().apply {
                put("tabId", "tab_10")
                put("frameIds", JSONArray().apply { put(1) })
            })
            put("func", "function() { return 1; }")
        }
        var errorMsg: String? = null
        scriptingAdapter.executeScript(sender, spec, delegate, context) { _, err ->
            errorMsg = err
        }
        assertEquals("SCRIPTING_FRAME_TARGETING_UNSUPPORTED", errorMsg)
    }

    @Test
    fun testScriptingIsolatedWorldUnsupported() {
        val sender = ExtensionSender(extensionId = "ext_p9_10", isPrivate = false)
        val delegate = object : FakeBrowserDelegate() {
            override fun queryTabs(queryInfo: JSONObject): JSONArray {
                return JSONArray().apply {
                    put(JSONObject().apply {
                        put("id", "tab_10")
                        put("url", "https://example.com/page")
                    })
                }
            }
        }
        val spec = JSONObject().apply {
            put("target", JSONObject().put("tabId", "tab_10"))
            put("world", "ISOLATED")
            put("func", "function() { return 1; }")
        }
        var errorMsg: String? = null
        scriptingAdapter.executeScript(sender, spec, delegate, context) { _, err ->
            errorMsg = err
        }
        assertEquals("SCRIPTING_WORLD_UNSUPPORTED", errorMsg)
    }

    @Test
    fun testScriptingInvalidArgumentsBlocked() {
        val sender = ExtensionSender(extensionId = "ext_p9_10", isPrivate = false)
        val delegate = object : FakeBrowserDelegate() {
            override fun queryTabs(queryInfo: JSONObject): JSONArray {
                return JSONArray().apply {
                    put(JSONObject().apply {
                        put("id", "tab_10")
                        put("url", "https://example.com/page")
                    })
                }
            }
        }
        val spec = JSONObject().apply {
            put("target", JSONObject().put("tabId", "tab_10"))
            put("func", "function() { return 1; }")
            put("args", JSONArray().apply { put(java.lang.Thread()) })
        }
        var errorMsg: String? = null
        scriptingAdapter.executeScript(sender, spec, delegate, context) { _, err ->
            errorMsg = err
        }
        assertEquals("SCRIPTING_ARGUMENT_UNSUPPORTED", errorMsg)
    }

    @Test
    fun testScriptingDuplicateContentScriptRegistrationThrows() {
        val sender = ExtensionSender(extensionId = "ext_p9_10", isPrivate = false)
        val scripts = JSONArray().apply {
            put(JSONObject().apply {
                put("id", "dup_script")
                put("matches", JSONArray().apply { put("*://*.example.com/*") })
                put("js", JSONArray().apply { put("script.js") })
            })
        }
        scriptingAdapter.registerContentScripts(sender, scripts)
        try {
            scriptingAdapter.registerContentScripts(sender, scripts)
            fail("Expected duplicate registration to throw")
        } catch (e: IllegalArgumentException) {
            assertEquals("SCRIPTING_DUPLICATE_REGISTRATION", e.message)
        }
    }

    @Test
    fun testScriptingUnregisteredContentScriptNotFoundThrows() {
        val sender = ExtensionSender(extensionId = "ext_p9_10", isPrivate = false)
        val ids = JSONArray().apply { put("non_existent") }
        try {
            scriptingAdapter.unregisterContentScripts(sender, ids)
            fail("Expected unregistration of non-existent script to throw")
        } catch (e: IllegalArgumentException) {
            assertEquals("SCRIPTING_REGISTRATION_NOT_FOUND", e.message)
        }
    }

    @Test
    fun testScriptingUpdateContentScriptNotFoundThrows() {
        val sender = ExtensionSender(extensionId = "ext_p9_10", isPrivate = false)
        val scripts = JSONArray().apply {
            put(JSONObject().apply {
                put("id", "non_existent_update")
                put("matches", JSONArray().apply { put("*://*.example.com/*") })
                put("js", JSONArray().apply { put("script.js") })
            })
        }
        try {
            scriptingAdapter.updateContentScripts(sender, scripts)
            fail("Expected update of non-existent script to throw")
        } catch (e: IllegalArgumentException) {
            assertEquals("SCRIPTING_REGISTRATION_NOT_FOUND", e.message)
        }
    }

    @Test
    fun testScriptingInsertAndRemoveCssOrderAndTracking() {
        val sender = ExtensionSender(extensionId = "ext_p9_10", isPrivate = false)
        val delegate = object : FakeBrowserDelegate() {
            override fun queryTabs(queryInfo: JSONObject): JSONArray {
                return JSONArray().apply {
                    put(JSONObject().apply {
                        put("id", "tab_css")
                        put("url", "https://example.com/page")
                    })
                }
            }
        }
        val spec = JSONObject().apply {
            put("target", JSONObject().put("tabId", "tab_css"))
            put("css", "body { background: red; }")
        }
        val insertRes = scriptingAdapter.insertCSS(sender, spec, delegate, context)
        assertEquals("success", insertRes.getString("status"))

        val removeRes = scriptingAdapter.removeCSS(sender, spec, delegate, context)
        assertEquals("success", removeRes.getString("status"))

        // Try to remove again, should fail with SCRIPTING_REMOVE_CSS_PARTIAL
        try {
            scriptingAdapter.removeCSS(sender, spec, delegate, context)
            fail("Expected remove non-tracked CSS to fail")
        } catch (e: IllegalArgumentException) {
            assertEquals("SCRIPTING_REMOVE_CSS_PARTIAL", e.message)
        }
    }

    @Test
    fun testDnrAtomicRuleStoreValidation() {
        val store = ExtensionDnrRuleStore()
        val rule1 = DnrRule(
            id = 101,
            priority = 1,
            action = DnrAction("block"),
            condition = DnrCondition(urlFilter = "ads.example.com")
        )
        val rule2 = DnrRule(
            id = 102,
            priority = 2,
            action = DnrAction("redirect", redirectUrl = "https://example.com/safe"),
            condition = DnrCondition(urlFilter = "tracker.example.com")
        )
        
        // Add dynamic rules
        store.updateDynamicRules("ext_p9_10", emptySet(), listOf(rule1, rule2))
        assertEquals(2, store.getDynamicRules("ext_p9_10").size)

        // Attempting to add duplicate rule ID should fail atomically without altering existing store
        try {
            store.updateDynamicRules("ext_p9_10", emptySet(), listOf(rule1))
            fail("Expected duplicate rule ID to throw")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("DNR_RULE_DUPLICATE") == true)
        }
        assertEquals(2, store.getDynamicRules("ext_p9_10").size)

        // Remove 101 and add 103
        val rule3 = DnrRule(
            id = 103,
            priority = 3,
            action = DnrAction("allow"),
            condition = DnrCondition(urlFilter = "good.example.com")
        )
        store.updateDynamicRules("ext_p9_10", setOf(101), listOf(rule3))
        val dynamicRules = store.getDynamicRules("ext_p9_10")
        assertEquals(2, dynamicRules.size)
        assertTrue(dynamicRules.none { it.id == 101 })
        assertTrue(dynamicRules.any { it.id == 103 })
    }

    @Test
    fun testWebRequestSubscriptionRegistryAndFiltering() {
        val subRegistry = WebRequestSubscriptionRegistry()
        val filter = WebRequestFilter(
            urls = listOf("*://*.example.com/*"),
            types = listOf("script", "stylesheet")
        )
        subRegistry.addSubscription("ext_p9_10", "onBeforeRequest", filter)

        val matchingContext = ExtensionNetworkRequestContext(
            requestId = "req_1",
            url = "https://api.example.com/bundle.js",
            resourceType = "script"
        )
        val matches = subRegistry.getMatchingSubscriptions("webRequest.onBeforeRequest", matchingContext)
        assertEquals(1, matches.size)
        assertEquals("ext_p9_10", matches[0].extensionId)

        val nonMatchingContext = ExtensionNetworkRequestContext(
            requestId = "req_2",
            url = "https://other.com/bundle.js",
            resourceType = "script"
        )
        val noMatches = subRegistry.getMatchingSubscriptions("webRequest.onBeforeRequest", nonMatchingContext)
        assertTrue(noMatches.isEmpty())
    }

    @Test
    fun testWebRequestIdMapperDeterministicAllocation() {
        val idMapper = WebRequestIdMapper()
        val id1 = idMapper.getOrCreateRequestId("https://example.com/test1")
        val id2 = idMapper.getOrCreateRequestId("https://example.com/test1")
        assertEquals(id1, id2)

        val id3 = idMapper.getOrCreateRequestId("https://example.com/test2")
        assertNotEquals(id1, id3)

        idMapper.releaseRequest("https://example.com/test1")
        val id4 = idMapper.getOrCreateRequestId("https://example.com/test1")
        assertNotEquals(id1, id4)
    }

    open class FakeBrowserDelegate : BrowserDelegate {
        override fun queryTabs(queryInfo: JSONObject): JSONArray = JSONArray()
        override fun createTab(url: String, active: Boolean) {}
        override fun removeTab(tabId: String) {}
        override fun reloadTab(tabId: String) {}
        override fun updateTab(tabId: String, url: String) {}
        override fun showNotification(title: String, message: String) {}
        override fun downloadFile(url: String, filename: String?) {}
        override fun getActiveTabId(): String? = null
        override fun executeScriptOnTab(tabId: String, code: String, callback: (String?) -> Unit) { callback(null) }
        override fun checkExtensionPermission(extensionId: String, permission: String, callback: (Boolean) -> Unit) { callback(true) }
    }
}
