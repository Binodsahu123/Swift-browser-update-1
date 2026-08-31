package com.swift.browser.extensionengine

import android.os.Handler
import android.os.Looper

/**
 * Controller responsible for waking dormant/suspended MV3 extension service workers when events arrive.
 */
class ServiceWorkerWakeController(
    private val serviceWorkerRegistry: ServiceWorkerRegistry,
    private val swJsRuntime: ServiceWorkerJsRuntime,
    private val extensionRegistry: ExtensionRegistry,
    private val bootstrapScriptProvider: (String) -> String,
    private val permissionAdapter: ExtensionPermissionAdapter? = null
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    // Overloaded secondary constructor for backward compatibility with legacy BackgroundScriptManager passing
    constructor(
        serviceWorkerRegistry: ServiceWorkerRegistry,
        backgroundScriptManager: BackgroundScriptManager,
        extensionRegistry: ExtensionRegistry,
        bootstrapScriptProvider: (String) -> String
    ) : this(
        serviceWorkerRegistry = serviceWorkerRegistry,
        swJsRuntime = ServiceWorkerJsRuntime(backgroundScriptManager.context, backgroundScriptManager.scriptInjector, backgroundScriptManager.messageBus).apply {
            setRuntimeBridgeProvider { wv -> backgroundScriptManager.runtimeBridgeProvider?.invoke(wv) ?: Any() }
        },
        extensionRegistry = extensionRegistry,
        bootstrapScriptProvider = bootstrapScriptProvider
    )

    interface WakeCallback {
        fun onWoken(extensionId: String, success: Boolean)
    }

    /**
     * Attempts to wake up a service worker if dormant/suspended, queueing the event.
     */
    fun wakeAndExecute(
        extensionId: String,
        event: QueuedServiceWorkerEvent?,
        callback: WakeCallback? = null,
        isPrivate: Boolean = false
    ) {
        val worker = serviceWorkerRegistry.getWorker(extensionId)
        val ext = extensionRegistry.getExtension(extensionId)

        if (worker == null || ext == null || !extensionRegistry.isExtensionEnabled(extensionId)) {
            callback?.onWoken(extensionId, false)
            return
        }

        // Enforce private mode permission check
        if (isPrivate) {
            val allowedInPrivate = permissionAdapter?.isAllowedInPrivate(extensionId) ?: ext.allowedInPrivate
            if (!allowedInPrivate) {
                callback?.onWoken(extensionId, false)
                return
            }
        }

        // Bounded queue handling (max 50)
        if (event != null) {
            val maxQueueSize = 50
            if (worker.pendingEvents.size >= maxQueueSize) {
                val indexToDrop = worker.pendingEvents.indexOfFirst {
                    it.eventName != "runtime.onInstalled" &&
                    it.eventName != "runtime.onStartup" &&
                    it.eventName != "runtime.onMessage" &&
                    it.eventName != "runtime.onConnect"
                }
                if (indexToDrop >= 0) {
                    worker.pendingEvents.removeAt(indexToDrop)
                } else {
                    worker.pendingEvents.removeAt(0)
                }
            }
            worker.pendingEvents.add(event)
        }

        val wakeReason = when {
            event?.eventName == "runtime.onMessage" -> "RUNTIME_MESSAGE"
            event?.eventName == "runtime.onConnect" -> "PORT_MESSAGE"
            event?.eventName == "runtime.onInstalled" -> "INSTALL_EVENT"
            event?.eventName == "runtime.onStartup" -> "STARTUP_EVENT"
            event?.eventName == "alarms.onAlarm" -> "ALARM_EVENT"
            event?.eventName?.startsWith("tabs.") == true -> "TAB_EVENT"
            event?.eventName?.startsWith("windows.") == true -> "WINDOW_EVENT"
            event?.eventName?.startsWith("cookies.") == true -> "COOKIE_EVENT"
            event?.eventName?.startsWith("webRequest.") == true -> "WEBREQUEST_EVENT"
            event?.eventName?.startsWith("declarativeNetRequest.") == true -> "DNR_EVENT"
            event?.eventName?.startsWith("bookmarks.") == true -> "BOOKMARK_EVENT"
            event?.eventName?.startsWith("history.") == true -> "HISTORY_EVENT"
            event?.eventName?.startsWith("downloads.") == true -> "DOWNLOAD_EVENT"
            event?.eventName?.startsWith("action.") == true || event?.eventName?.startsWith("browserAction.") == true -> "ACTION_EVENT"
            event?.eventName?.startsWith("contextMenus.") == true -> "CONTEXT_MENU_EVENT"
            event?.eventName?.startsWith("commands.") == true -> "COMMAND_EVENT"
            event?.eventName?.startsWith("omnibox.") == true -> "OMNIBOX_EVENT"
            else -> "OTHER_REGISTERED_EVENT"
        }
        worker.wakeReason = wakeReason
        worker.lastEventAt = System.currentTimeMillis()
        worker.lastActiveTimestamp = System.currentTimeMillis()

        val currentState = serviceWorkerRegistry.getState(extensionId)

        if (currentState == ServiceWorkerState.WAKE || currentState == ServiceWorkerState.STARTING) {
            // Startup already in progress, coalesce wake requests and wait
            if (callback != null) {
                val poller = object : Runnable {
                    var attempts = 0
                    override fun run() {
                        val state = serviceWorkerRegistry.getState(extensionId)
                        if (state == ServiceWorkerState.ACTIVE || state == ServiceWorkerState.RUNNING || state == ServiceWorkerState.IDLE) {
                            dispatchPendingEvents(extensionId)
                            callback.onWoken(extensionId, true)
                        } else if (state == ServiceWorkerState.DORMANT || state == ServiceWorkerState.STOPPED || state == ServiceWorkerState.FAILED || state == ServiceWorkerState.CRASHED || state == ServiceWorkerState.UNINSTALLED || state == ServiceWorkerState.DISABLED) {
                            callback.onWoken(extensionId, false)
                        } else {
                            attempts++
                            if (attempts < 50) { // 5 seconds timeout
                                mainHandler.postDelayed(this, 100L)
                            } else {
                                callback.onWoken(extensionId, false)
                            }
                        }
                    }
                }
                mainHandler.postDelayed(poller, 100L)
            }
            return
        }

        when (currentState) {
            ServiceWorkerState.ACTIVE, ServiceWorkerState.EVENT, ServiceWorkerState.RUNNING, ServiceWorkerState.IDLE -> {
                // Already running
                dispatchPendingEvents(extensionId)
                callback?.onWoken(extensionId, true)
            }
            ServiceWorkerState.REGISTERED, ServiceWorkerState.DORMANT, ServiceWorkerState.SUSPEND, ServiceWorkerState.STOPPED, ServiceWorkerState.SUSPENDED, ServiceWorkerState.CRASHED, ServiceWorkerState.RESTARTING, ServiceWorkerState.RESTART_PENDING, ServiceWorkerState.WAKE -> {
                // Increment generation ID and setup execution context
                val genId = serviceWorkerRegistry.incrementWorkerGeneration(extensionId)
                worker.executionContext = ServiceWorkerExecutionContext(
                    extensionId = extensionId,
                    manifestVersion = ext.manifestVersion,
                    workerGenerationId = genId,
                    startTime = System.currentTimeMillis(),
                    wakeReason = wakeReason,
                    isPrivate = isPrivate,
                    enabled = true,
                    state = ServiceWorkerState.STARTING
                )
                serviceWorkerRegistry.transitionState(extensionId, ServiceWorkerState.STARTING)

                val scriptPath = ext.backgroundSpec.serviceWorker.ifBlank { worker.scriptPath }
                val bootstrap = bootstrapScriptProvider(extensionId)

                swJsRuntime.startWorker(
                    parsedExtension = ext,
                    scriptPath = scriptPath,
                    generationId = genId,
                    bootstrapScript = bootstrap,
                    isPrivate = isPrivate,
                    onStartupResult = { result, errorMsg ->
                        if (result == WorkerStartupResult.SUCCESS) {
                            worker.lastStartAt = System.currentTimeMillis()
                            serviceWorkerRegistry.transitionState(extensionId, ServiceWorkerState.RUNNING)
                            worker.executionContext?.state = ServiceWorkerState.RUNNING
                            dispatchPendingEvents(extensionId)
                            callback?.onWoken(extensionId, true)
                        } else {
                            serviceWorkerRegistry.transitionState(extensionId, ServiceWorkerState.CRASHED)
                            worker.executionContext?.state = ServiceWorkerState.CRASHED
                            callback?.onWoken(extensionId, false)
                        }
                    }
                )
            }
            else -> {
                callback?.onWoken(extensionId, false)
            }
        }
    }

    private fun dispatchPendingEvents(extensionId: String) {
        val worker = serviceWorkerRegistry.getWorker(extensionId) ?: return
        val genId = worker.workerGenerationId
        val pendingCopy = ArrayList(worker.pendingEvents)

        for (event in pendingCopy) {
            event.deliveryState = WorkerEventDeliveryState.DELIVERING
            swJsRuntime.evaluateEvent(extensionId, genId, event.eventName, event.payload) { success ->
                if (success) {
                    event.deliveryState = WorkerEventDeliveryState.DELIVERED
                    worker.pendingEvents.remove(event)
                } else {
                    event.deliveryState = WorkerEventDeliveryState.FAILED
                }
            }
        }
    }

    fun flushPendingEvents(extensionId: String): List<QueuedServiceWorkerEvent> {
        val worker = serviceWorkerRegistry.getWorker(extensionId) ?: return emptyList()
        return ArrayList(worker.pendingEvents)
    }
}

