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
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ExtensionPart11Part12Test {

    private lateinit var context: Context
    private lateinit var permissionManager: PermissionManager
    private lateinit var registry: ExtensionRegistry
    private lateinit var messageBus: MessageBus
    private lateinit var eventManager: EventManager
    private lateinit var actionAdapter: ExtensionActionAdapter
    private lateinit var contextMenusAdapter: ExtensionContextMenusAdapter
    private lateinit var commandsAdapter: ExtensionCommandsAdapter
    private lateinit var omniboxAdapter: ExtensionOmniboxAdapter
    private lateinit var sidePanelAdapter: ExtensionSidePanelAdapter
    private lateinit var managementAdapter: ExtensionManagementAdapter
    private lateinit var topSitesAdapter: ExtensionTopSitesAdapter
    private lateinit var idleAdapter: ExtensionIdleAdapter
    private lateinit var ttsAdapter: ExtensionTtsAdapter
    private lateinit var searchAdapter: ExtensionSearchAdapter
    private lateinit var alarmsAdapter: ExtensionAlarmsAdapter
    private lateinit var systemAdapter: ExtensionSystemAdapter

    private val manifestJsonWithEverything = """
    {
      "name": "Full Test Extension",
      "version": "1.0",
      "manifest_version": 3,
      "action": {
        "default_popup": "popup.html",
        "default_title": "Default Test Title",
        "default_icon": "icons/icon16.png"
      },
      "commands": {
        "toggle-feature": {
          "suggested_key": {
            "default": "Ctrl+Shift+Y"
          },
          "description": "Toggle feature"
        }
      },
      "omnibox": {
        "keyword": "testkey"
      },
      "permissions": ["contextMenus", "sidePanel", "alarms", "management", "idle", "tts", "search", "topSites", "system.cpu", "system.memory", "system.storage"]
    }
    """.trimIndent()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        registry = ExtensionRegistry()
        messageBus = MessageBus()
        permissionManager = PermissionManager(context)
        permissionManager.setRegistry(registry)
        eventManager = EventManager(messageBus)
        actionAdapter = ExtensionActionAdapter(permissionManager, registry, eventManager)
        contextMenusAdapter = ExtensionContextMenusAdapter(permissionManager, registry, eventManager)
        commandsAdapter = ExtensionCommandsAdapter(permissionManager, registry, eventManager)
        omniboxAdapter = ExtensionOmniboxAdapter(permissionManager, registry, eventManager)
        sidePanelAdapter = ExtensionSidePanelAdapter(permissionManager, registry)
        managementAdapter = ExtensionManagementAdapter(permissionManager, registry, eventManager)
        topSitesAdapter = ExtensionTopSitesAdapter(permissionManager, registry)
        idleAdapter = ExtensionIdleAdapter(permissionManager, registry)
        ttsAdapter = ExtensionTtsAdapter(permissionManager, registry)
        searchAdapter = ExtensionSearchAdapter(permissionManager, registry)
        alarmsAdapter = ExtensionAlarmsAdapter(permissionManager, registry, eventManager)
        systemAdapter = ExtensionSystemAdapter(permissionManager, registry)

        // Register a test extension
        val parsed = ParsedExtension(
            id = "test_ext_11_12",
            name = "Full Test Extension",
            version = "1.0",
            description = "Test",
            manifestVersion = 3,
            permissions = listOf("contextMenus", "sidePanel", "alarms", "management", "idle", "tts", "search", "topSites", "system.cpu", "system.memory", "system.storage"),
            hostPermissions = emptyList(),
            backgroundScripts = emptyList(),
            isServiceWorker = false,
            contentScripts = emptyList(),
            actionPopup = "popup.html",
            optionsPage = "options.html",
            manifestJson = manifestJsonWithEverything,
            allowedInPrivate = true
        )
        registry.registerExtension(parsed)

        // Register helper management extension to perform state transitions
        val mgmtParsed = ParsedExtension(
            id = "test_ext_mgmt",
            name = "Management Caller",
            version = "1.0",
            description = "Test",
            manifestVersion = 3,
            permissions = listOf("management"),
            hostPermissions = emptyList(),
            backgroundScripts = emptyList(),
            isServiceWorker = false,
            contentScripts = emptyList(),
            actionPopup = "",
            optionsPage = "",
            manifestJson = "{\"permissions\": [\"management\"]}",
            allowedInPrivate = true
        )
        registry.registerExtension(mgmtParsed)

        // Clear all static adapter states
        ExtensionActionAdapter.cleanupExtensionState("test_ext_11_12")
        ExtensionContextMenusAdapter.cleanupExtensionState("test_ext_11_12")
        ExtensionCommandsAdapter.cleanupExtensionState("test_ext_11_12")
        ExtensionOmniboxAdapter.cleanupExtensionState("test_ext_11_12")
        ExtensionSidePanelAdapter.cleanupExtensionState("test_ext_11_12")
    }

    // --- chrome.action tests ---

    @Test
    fun testActionDefaultsAndSetGetTitle() {
        val sender = ExtensionSender("test_ext_11_12")
        
        // Check default parsed values
        val titleObj1 = actionAdapter.getTitle(sender, JSONObject())
        assertEquals("Full Test Extension", titleObj1.getString("title"))

        // Set title
        actionAdapter.setTitle(sender, JSONObject().put("title", "New Title"))
        val titleObj2 = actionAdapter.getTitle(sender, JSONObject())
        assertEquals("New Title", titleObj2.getString("title"))
    }

    @Test
    fun testActionTabScoped() {
        val sender = ExtensionSender("test_ext_11_12")
        
        // Tab-scoped title setting and retrieval
        actionAdapter.setTitle(sender, JSONObject().put("title", "Tab Title").put("tabId", "tab123"))
        val tabTitle = actionAdapter.getTitle(sender, JSONObject().put("tabId", "tab123"))
        assertEquals("Tab Title", tabTitle.getString("title"))

        val globalTitle = actionAdapter.getTitle(sender, JSONObject())
        assertEquals("Full Test Extension", globalTitle.getString("title"))
    }

    @Test
    fun testActionSetPopupAndGet() {
        val sender = ExtensionSender("test_ext_11_12")

        // Get default popup
        val popupObj1 = actionAdapter.getPopup(sender, JSONObject())
        assertEquals("popup.html", popupObj1.getString("popup"))

        // Set popup (passing null context to skip path existence validation since it's a unit test)
        actionAdapter.setPopup(sender, JSONObject().put("popup", "new_popup.html"), null)
        val popupObj2 = actionAdapter.getPopup(sender, JSONObject())
        assertEquals("new_popup.html", popupObj2.getString("popup"))
    }

    @Test
    fun testActionSetBadgeTextAndGet() {
        val sender = ExtensionSender("test_ext_11_12")

        // Default badge
        val badge1 = actionAdapter.getBadgeText(sender, JSONObject())
        assertEquals("", badge1.getString("text"))

        // Set badge
        actionAdapter.setBadgeText(sender, JSONObject().put("text", "99+"))
        val badge2 = actionAdapter.getBadgeText(sender, JSONObject())
        assertEquals("99+", badge2.getString("text"))
    }

    @Test
    fun testActionSetIconDataUnsupported() {
        val sender = ExtensionSender("test_ext_11_12")

        // raw ImageData should be rejected
        assertThrows(IllegalArgumentException::class.java) {
            actionAdapter.setIcon(sender, JSONObject().put("imageData", JSONObject()))
        }
    }

    // --- chrome.contextMenus tests ---

    @Test
    fun testContextMenuCreateAndRemove() {
        val sender = ExtensionSender("test_ext_11_12")

        // Create item
        val createProps = JSONObject().apply {
            put("id", "menu_item_1")
            put("title", "Test Click")
            put("type", "normal")
        }
        val createRes = contextMenusAdapter.create(sender, createProps)
        assertEquals("success", createRes.getString("status"))
        assertEquals("menu_item_1", createRes.getString("id"))

        // Duplicate item should fail
        assertThrows(IllegalArgumentException::class.java) {
            contextMenusAdapter.create(sender, createProps)
        }

        // Remove item
        val removeRes = contextMenusAdapter.remove(sender, "menu_item_1")
        assertEquals("success", removeRes.getString("status"))
    }

    @Test
    fun testContextMenuParentValidation() {
        val sender = ExtensionSender("test_ext_11_12")

        // Creating parentless child should fail
        val childProps = JSONObject().apply {
            put("id", "child_item")
            put("parentId", "non_existent_parent")
            put("title", "Child")
        }
        assertThrows(IllegalArgumentException::class.java) {
            contextMenusAdapter.create(sender, childProps)
        }
    }

    // --- chrome.commands tests ---

    @Test
    fun testCommandsParsingAndValidation() {
        val sender = ExtensionSender("test_ext_11_12")

        // Get parsed commands from manifest
        val cmds = commandsAdapter.getAll(sender)
        assertEquals(1, cmds.length())
        val cmd = cmds.getJSONObject(0)
        assertEquals("toggle-feature", cmd.getString("name"))
        assertEquals("Toggle feature", cmd.getString("description"))
        assertEquals("Ctrl+Shift+Y", cmd.getString("shortcut"))
    }

    // --- chrome.omnibox tests ---

    @Test
    fun testOmniboxKeywordConflictAndValidation() {
        val sender = ExtensionSender("test_ext_11_12")

        // Register valid unique keyword from manifest
        val regRes = omniboxAdapter.registerKeyword(sender)
        assertEquals("success", regRes.getString("status"))
        assertEquals("testkey", regRes.getString("keyword"))

        // Registering same keyword under another extension should conflict
        val secondExt = ParsedExtension(
            id = "other_ext",
            name = "Other Extension",
            version = "1.0",
            description = "Test",
            manifestVersion = 3,
            permissions = emptyList(),
            hostPermissions = emptyList(),
            backgroundScripts = emptyList(),
            isServiceWorker = false,
            contentScripts = emptyList(),
            actionPopup = "",
            optionsPage = "",
            manifestJson = "{\"omnibox\": {\"keyword\": \"testkey\"}}",
            allowedInPrivate = true
        )
        registry.registerExtension(secondExt)

        val otherSender = ExtensionSender("other_ext")
        assertThrows(IllegalArgumentException::class.java) {
            omniboxAdapter.registerKeyword(otherSender)
        }
    }

    // --- chrome.sidePanel tests ---

    @Test
    fun testSidePanelOptionsAndUnsupportedUI() {
        val sender = ExtensionSender("test_ext_11_12")

        // Set global options (skipping context validation)
        val setRes = sidePanelAdapter.setOptions(sender, JSONObject().put("path", "panel.html"), null)
        assertEquals("success", setRes.getString("status"))

        val getRes = sidePanelAdapter.getOptions(sender, JSONObject())
        assertEquals("panel.html", getRes.getString("path"))

        // Open on Android now maps to UI host
        val openRes = sidePanelAdapter.open(sender, JSONObject())
        assertEquals("success", openRes.getString("status"))
        assertEquals("panel.html", openRes.getString("path"))
    }

    // --- Lifecycle State Cleanup tests ---

    @Test
    fun testLifecycleCleanupOnDisable() {
        val sender = ExtensionSender("test_ext_11_12")

        // Populate state
        actionAdapter.setTitle(sender, JSONObject().put("title", "Active Title"))
        contextMenusAdapter.create(sender, JSONObject().apply {
            put("id", "temp_menu")
            put("title", "Temp Menu")
        })

        // Verify state is stored
        assertEquals("Active Title", actionAdapter.getTitle(sender, JSONObject()).getString("title"))
        assertTrue(ExtensionContextMenusAdapter.itemsMap.containsKey("test_ext_11_12_temp_menu"))

        // Disable cleanups
        ExtensionActionAdapter.cleanupExtensionState("test_ext_11_12")
        ExtensionContextMenusAdapter.cleanupExtensionState("test_ext_11_12")

        // Verify clean state
        assertEquals("Full Test Extension", actionAdapter.getTitle(sender, JSONObject()).getString("title"))
        assertFalse(ExtensionContextMenusAdapter.itemsMap.containsKey("test_ext_11_12_temp_menu"))
    }

    // --- chrome.alarms tests ---

    @Test
    fun testAlarmsAdapterBasic() {
        val sender = ExtensionSender("test_ext_11_12")
        val alarmInfo = JSONObject().apply {
            put("delayInMinutes", 0.001)
        }
        val createRes = alarmsAdapter.create(sender, "test_alarm", alarmInfo)
        assertEquals("created", createRes.getString("status"))
        assertEquals("test_alarm", createRes.getString("name"))

        val getRes = alarmsAdapter.get(sender, "test_alarm")
        assertNotNull(getRes)
        assertEquals("test_alarm", getRes!!.getString("name"))

        val allAlarms = alarmsAdapter.getAll(sender)
        assertEquals(1, allAlarms.length())

        val clearRes = alarmsAdapter.clear(sender, "test_alarm")
        assertTrue(clearRes.getBoolean("cleared"))

        val allAlarmsAfter = alarmsAdapter.getAll(sender)
        assertEquals(0, allAlarmsAfter.length())
    }

    // --- chrome.management tests ---

    @Test
    fun testManagementAdapterBasic() {
        val sender = ExtensionSender("test_ext_11_12")
        val selfRes = managementAdapter.getSelf(sender)
        assertEquals("test_ext_11_12", selfRes.getString("id"))
        assertEquals("Full Test Extension", selfRes.getString("name"))

        val mgmtSender = ExtensionSender("test_ext_mgmt")
        val allRes = managementAdapter.getAll(mgmtSender)
        assertTrue(allRes.length() >= 2)

        val getRes = managementAdapter.get(mgmtSender, "test_ext_11_12")
        assertEquals("test_ext_11_12", getRes.getString("id"))

        // Toggle enabled using the management sender (which remains active/enabled itself)
        val disableRes = managementAdapter.setEnabled(mgmtSender, "test_ext_11_12", false)
        assertFalse(disableRes.getBoolean("enabled"))

        // Clean up: enable back using the management sender
        val enableRes = managementAdapter.setEnabled(mgmtSender, "test_ext_11_12", true)
        assertTrue(enableRes.getBoolean("enabled"))
    }

    // --- chrome.idle tests ---

    @Test
    fun testIdleAdapterBasic() {
        val sender = ExtensionSender("test_ext_11_12")
        val intervalRes = idleAdapter.setDetectionInterval(sender, 30)
        assertEquals("success", intervalRes.getString("status"))
        assertEquals(30, intervalRes.getInt("interval"))

        val stateRes = idleAdapter.queryState(sender, 30, context)
        assertTrue(stateRes.has("state"))
        val state = stateRes.getString("state")
        assertTrue(state == "active" || state == "idle" || state == "locked")
    }

    // --- chrome.tts tests ---

    @Test
    fun testTtsAdapterBasic() {
        val sender = ExtensionSender("test_ext_11_12")
        val speakRes = ttsAdapter.speak(sender, "Hello", JSONObject(), context)
        assertEquals("speaking", speakRes.getString("status"))

        val stopRes = ttsAdapter.stop(sender)
        assertEquals("stopped", stopRes.getString("status"))

        val pauseRes = ttsAdapter.pause(sender)
        assertEquals("paused", pauseRes.getString("status"))

        val resumeRes = ttsAdapter.resume(sender)
        assertEquals("resumed", resumeRes.getString("status"))

        val speakingRes = ttsAdapter.isSpeaking(sender)
        assertFalse(speakingRes.getBoolean("speaking"))
    }

    // --- chrome.search & chrome.topSites tests ---

    @Test
    fun testSearchAndTopSitesAdaptersBasic() {
        val sender = ExtensionSender("test_ext_11_12")
        val searchRes = searchAdapter.query(sender, JSONObject().put("text", "hello world"), null)
        assertEquals("success", searchRes.getString("status"))
        assertEquals("hello world", searchRes.getString("query"))

        val topSitesRes = topSitesAdapter.get(sender, null)
        assertTrue(topSitesRes.length() > 0)
    }

    // --- chrome.system (cpu, memory, storage) tests ---

    @Test
    fun testSystemAdapterBasic() {
        val sender = ExtensionSender("test_ext_11_12")
        val cpuRes = systemAdapter.getCpuInfo(sender, context)
        assertTrue(cpuRes.has("numOfProcessors"))
        assertTrue(cpuRes.has("archName"))

        val memoryRes = systemAdapter.getMemoryInfo(sender, context)
        assertTrue(memoryRes.has("capacity"))
        assertTrue(memoryRes.has("availableCapacity"))

        val storageRes = systemAdapter.getStorageInfo(sender, context)
        assertTrue(storageRes.length() > 0)
    }

    // --- Advanced API Capability Matrix tests ---

    @Test
    fun testCapabilityMatrixBasic() {
        assertTrue(ExtensionAdvancedApiCapabilityMatrix.isSupported("alarms"))
        assertTrue(ExtensionAdvancedApiCapabilityMatrix.isSupported("management"))
        assertTrue(ExtensionAdvancedApiCapabilityMatrix.isSupported("idle"))
        assertTrue(ExtensionAdvancedApiCapabilityMatrix.isSupported("system.cpu"))

        val json = ExtensionAdvancedApiCapabilityMatrix.getAsJson()
        assertTrue(json.has("apis"))
        val array = json.getJSONArray("apis")
        assertTrue(array.length() > 0)
    }
}
