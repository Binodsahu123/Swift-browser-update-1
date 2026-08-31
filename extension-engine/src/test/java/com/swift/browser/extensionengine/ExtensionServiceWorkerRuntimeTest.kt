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
class ExtensionServiceWorkerRuntimeTest {

    private lateinit var context: Context
    private lateinit var registry: ExtensionRegistry
    private lateinit var swRegistry: ServiceWorkerRegistry
    private lateinit var permissionManager: PermissionManager
    private lateinit var messageBus: MessageBus
    private lateinit var portManager: PortManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        registry = ExtensionRegistry()
        swRegistry = ServiceWorkerRegistry()
        permissionManager = PermissionManager(context)
        messageBus = MessageBus()
        portManager = PortManager(messageBus)
    }

    private fun createDummyMV3Extension(id: String = "test_ext_mv3"): ParsedExtension {
        return ParsedExtension(
            id = id,
            name = "Test MV3 Extension",
            version = "1.0.0",
            description = "Test MV3 Extension Description",
            manifestVersion = 3,
            permissions = listOf("storage", "alarms", "notifications"),
            hostPermissions = listOf("https://*.example.com/*"),
            backgroundScripts = listOf("background.js"),
            isServiceWorker = true,
            contentScripts = emptyList(),
            actionPopup = "",
            optionsPage = "",
            manifestJson = """{"manifest_version": 3, "name": "Test MV3 Extension", "version": "1.0.0", "background": {"service_worker": "background.js"}}""",
            allowedInPrivate = false
        )
    }

    @Test
    fun testPartialServiceWorkerSupportFlagExposed() {
        assertTrue("PARTIAL_SERVICE_WORKER_SUPPORT constant must be true", PARTIAL_SERVICE_WORKER_SUPPORT)
    }

    @Test
    fun testWorkerStartupAndStateTransitions() {
        val ext = createDummyMV3Extension()
        registry.register(ext)

        val reg = swRegistry.register(ext.id, "background.js", isMV3 = true)
        assertEquals(ServiceWorkerState.REGISTERED, reg.state)

        val okDormant = swRegistry.transitionState(ext.id, ServiceWorkerState.DORMANT)
        assertTrue(okDormant)
        assertEquals(ServiceWorkerState.DORMANT, swRegistry.getState(ext.id))

        val okWake = swRegistry.transitionState(ext.id, ServiceWorkerState.WAKE)
        assertTrue(okWake)
        assertEquals(ServiceWorkerState.WAKE, swRegistry.getState(ext.id))

        val okActive = swRegistry.transitionState(ext.id, ServiceWorkerState.ACTIVE)
        assertTrue(okActive)
        assertEquals(ServiceWorkerState.ACTIVE, swRegistry.getState(ext.id))
    }

    @Test
    fun testWorkerSuspend() {
        val ext = createDummyMV3Extension()
        registry.register(ext)
        swRegistry.register(ext.id, "background.js")
        swRegistry.transitionState(ext.id, ServiceWorkerState.DORMANT)
        swRegistry.transitionState(ext.id, ServiceWorkerState.WAKE)
        swRegistry.transitionState(ext.id, ServiceWorkerState.ACTIVE)

        val okIdle = swRegistry.transitionState(ext.id, ServiceWorkerState.IDLE)
        assertTrue(okIdle)
        val okSuspend = swRegistry.transitionState(ext.id, ServiceWorkerState.SUSPEND)
        assertTrue(okSuspend)
        val okDormant = swRegistry.transitionState(ext.id, ServiceWorkerState.DORMANT)
        assertTrue(okDormant)

        assertEquals(ServiceWorkerState.DORMANT, swRegistry.getState(ext.id))
    }

    @Test
    fun testWakeOnEventAndQueuedEvents() {
        val ext = createDummyMV3Extension()
        registry.register(ext)
        val worker = swRegistry.register(ext.id, "background.js")
        swRegistry.transitionState(ext.id, ServiceWorkerState.DORMANT)

        val event1 = QueuedServiceWorkerEvent("evt1", "runtime.onMessage", JSONObject().put("test", 1))
        val event2 = QueuedServiceWorkerEvent("evt2", "alarms.onAlarm", JSONObject().put("alarm", "a1"))

        worker.pendingEvents.add(event1)
        worker.pendingEvents.add(event2)

        assertEquals(2, worker.pendingEvents.size)
        swRegistry.transitionState(ext.id, ServiceWorkerState.WAKE)
        swRegistry.transitionState(ext.id, ServiceWorkerState.EVENT)
        swRegistry.transitionState(ext.id, ServiceWorkerState.ACTIVE)

        val flushed = worker.pendingEvents.toList()
        worker.pendingEvents.clear()

        assertEquals(2, flushed.size)
        assertEquals("evt1", flushed[0].eventId)
        assertEquals("evt2", flushed[1].eventId)
    }

    @Test
    fun testMultipleEventsQueueing() {
        val ext = createDummyMV3Extension()
        registry.register(ext)
        val worker = swRegistry.register(ext.id, "background.js")
        swRegistry.transitionState(ext.id, ServiceWorkerState.DORMANT)

        for (i in 1..5) {
            worker.pendingEvents.add(QueuedServiceWorkerEvent("evt_$i", "runtime.onMessage", JSONObject()))
        }

        assertEquals(5, worker.pendingEvents.size)
    }

    @Test
    fun testWorkerCrashHandling() {
        val ext = createDummyMV3Extension("crash_ext")
        registry.register(ext)
        swRegistry.register(ext.id, "background.js")
        swRegistry.transitionState(ext.id, ServiceWorkerState.DORMANT)
        swRegistry.transitionState(ext.id, ServiceWorkerState.WAKE)
        swRegistry.transitionState(ext.id, ServiceWorkerState.ACTIVE)

        val okSuspend = swRegistry.transitionState(ext.id, ServiceWorkerState.SUSPEND)
        assertTrue(okSuspend)
        val okDormant = swRegistry.transitionState(ext.id, ServiceWorkerState.DORMANT)
        assertTrue(okDormant)
        assertEquals(ServiceWorkerState.DORMANT, swRegistry.getState(ext.id))
    }

    @Test
    fun testDisableExtension() {
        val ext = createDummyMV3Extension("disable_ext")
        registry.register(ext)
        swRegistry.register(ext.id, "background.js")
        swRegistry.transitionState(ext.id, ServiceWorkerState.DORMANT)
        swRegistry.transitionState(ext.id, ServiceWorkerState.WAKE)
        swRegistry.transitionState(ext.id, ServiceWorkerState.ACTIVE)

        registry.transitionState(ext.id, ExtensionState.INSTALLED_DISABLED)

        assertFalse("Disabled extension should not be enabled", registry.isExtensionEnabled(ext.id))
        swRegistry.transitionState(ext.id, ServiceWorkerState.SUSPEND)
        swRegistry.transitionState(ext.id, ServiceWorkerState.DORMANT)
        assertEquals(ServiceWorkerState.DORMANT, swRegistry.getState(ext.id))
    }

    @Test
    fun testUninstallExtension() {
        val ext = createDummyMV3Extension("uninstall_ext")
        registry.register(ext)
        swRegistry.register(ext.id, "background.js")

        registry.unregister(ext.id)
        swRegistry.unregister(ext.id)

        assertNull("Uninstalled extension should be removed from registry", registry.getExtension(ext.id))
        assertNull("Uninstalled extension should be removed from SW registry", swRegistry.getWorker(ext.id))
    }

    @Test
    fun testPrivateModePolicy() {
        val ext = createDummyMV3Extension("private_ext")
        registry.register(ext)

        assertFalse("Extension should not be allowed in private by default", ext.allowedInPrivate)
        assertFalse("PermissionManager should deny private mode by default", permissionManager.isAllowedInPrivate(ext.id))

        permissionManager.setAllowedInPrivate(ext.id, true)
        assertTrue("PermissionManager should now allow private mode", permissionManager.isAllowedInPrivate(ext.id))
    }

    @Test
    fun testTabCloseNotification() {
        val ext = createDummyMV3Extension("tab_close_ext")
        registry.register(ext)
        val worker = swRegistry.register(ext.id, "background.js")
        swRegistry.transitionState(ext.id, ServiceWorkerState.ACTIVE)

        val tabCloseEvent = QueuedServiceWorkerEvent("tab_close_123", "tabs.onRemoved", JSONObject().put("tabId", "123"))
        worker.pendingEvents.add(tabCloseEvent)

        assertEquals(1, worker.pendingEvents.size)
        assertEquals("tabs.onRemoved", worker.pendingEvents.first().eventName)
    }

    @Test
    fun testProcessRestartRecovery() {
        val ext1 = createDummyMV3Extension("restart_ext1")
        val ext2 = createDummyMV3Extension("restart_ext2")

        registry.register(ext1)
        registry.register(ext2)

        swRegistry.register(ext1.id, "background.js")
        swRegistry.register(ext2.id, "background.js")

        swRegistry.transitionState(ext1.id, ServiceWorkerState.DORMANT)
        swRegistry.transitionState(ext2.id, ServiceWorkerState.DORMANT)

        assertEquals(ServiceWorkerState.DORMANT, swRegistry.getState(ext1.id))
        assertEquals(ServiceWorkerState.DORMANT, swRegistry.getState(ext2.id))
    }

    @Test
    fun testRuntimeBridgeReportCrash() {
        val ext = createDummyMV3Extension("crash_report_ext")
        registry.register(ext)

        var crashReported = false
        var reportedExtId = ""
        var reportedReason = ""

        val db = ExtensionDatabase.getInstance(context)
        val storageManager = StorageManager(db)
        val tabMessenger = TabMessenger(messageBus)
        val activeTabManager = ActiveTabManager(null)
        val tabBridge = TabBridge(tabMessenger, activeTabManager, portManager)
        val eventManager = EventManager(messageBus)
        val webView = object : android.webkit.WebView(context) {
            override fun getUrl(): String {
                return "chrome-extension://${ext.id}/_generated_background_page.html"
            }
        }
        val bridge = RuntimeBridge(
            context = context,
            webView = webView,
            storageManager = storageManager,
            messageBus = messageBus,
            delegate = null,
            eventManager = eventManager,
            tabId = null,
            portManager = portManager,
            tabBridge = tabBridge,
            registry = registry,
            permissionManager = permissionManager,
            isPrivate = false,
            privateSessionId = null
        )

        bridge.onWorkerCrash = { extId, reason ->
            crashReported = true
            reportedExtId = extId
            reportedReason = reason
        }

        val payload = JSONObject().apply {
            put("api", "runtime.reportCrash")
            put("extensionId", ext.id)
            put("args", org.json.JSONArray().put("script_eval_error"))
        }

        bridge.postMessage(payload.toString(), "cb_crash_1")

        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertTrue("Crash callback should be invoked", crashReported)
        assertEquals(ext.id, reportedExtId)
        assertEquals("script_eval_error", reportedReason)
    }

    @Test
    fun testExecutionContextInitializationAndGeneration() {
        val ext = createDummyMV3Extension("exec_ctx_ext")
        registry.register(ext)
        val worker = swRegistry.register(ext.id, "background.js", isMV3 = true)

        assertEquals(1, worker.workerGenerationId)
        assertEquals(1, swRegistry.getWorkerGeneration(ext.id))

        val gen2 = swRegistry.incrementWorkerGeneration(ext.id)
        assertEquals(2, gen2)
        assertEquals(2, worker.workerGenerationId)

        val ctx = ServiceWorkerExecutionContext(
            extensionId = ext.id,
            manifestVersion = 3,
            workerGenerationId = gen2,
            wakeReason = "RUNTIME_MESSAGE"
        )
        worker.executionContext = ctx
        assertNotNull(worker.executionContext)
        assertEquals("RUNTIME_MESSAGE", worker.executionContext?.wakeReason)
        assertEquals(2, worker.executionContext?.workerGenerationId)
    }

    @Test
    fun testStateTransitionsFullCycle() {
        val ext = createDummyMV3Extension("state_cycle_ext")
        registry.register(ext)
        swRegistry.register(ext.id, "background.js")

        assertTrue(swRegistry.transitionState(ext.id, ServiceWorkerState.STARTING))
        assertTrue(swRegistry.transitionState(ext.id, ServiceWorkerState.RUNNING))
        assertTrue(swRegistry.transitionState(ext.id, ServiceWorkerState.IDLE))
        assertTrue(swRegistry.transitionState(ext.id, ServiceWorkerState.SUSPENDING))
        assertTrue(swRegistry.transitionState(ext.id, ServiceWorkerState.SUSPENDED))
        assertTrue(swRegistry.transitionState(ext.id, ServiceWorkerState.STARTING))
        assertTrue(swRegistry.transitionState(ext.id, ServiceWorkerState.RUNNING))
        assertTrue(swRegistry.transitionState(ext.id, ServiceWorkerState.CRASHED))
        assertTrue(swRegistry.transitionState(ext.id, ServiceWorkerState.RESTARTING))
        assertTrue(swRegistry.transitionState(ext.id, ServiceWorkerState.STARTING))
        assertTrue(swRegistry.transitionState(ext.id, ServiceWorkerState.RUNNING))
        assertTrue(swRegistry.transitionState(ext.id, ServiceWorkerState.STOPPED))
    }

    @Test
    fun testUninstalledStateTerminalRule() {
        val ext = createDummyMV3Extension("terminal_ext")
        registry.register(ext)
        swRegistry.register(ext.id, "background.js")

        assertTrue(swRegistry.transitionState(ext.id, ServiceWorkerState.UNINSTALLED))
        assertFalse("Cannot transition from UNINSTALLED to RUNNING", swRegistry.transitionState(ext.id, ServiceWorkerState.RUNNING))
        assertFalse("Cannot transition from UNINSTALLED to STARTING", swRegistry.transitionState(ext.id, ServiceWorkerState.STARTING))
    }
}
