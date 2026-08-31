package com.swift.browser.extensionengine

import org.json.JSONObject

/**
 * Lifecycle Manager governing MV3 extension service worker lifecycle transitions.
 */
class ServiceWorkerLifecycleManager(
    private val serviceWorkerRegistry: ServiceWorkerRegistry,
    private val wakeController: ServiceWorkerWakeController,
    private val shutdownController: ServiceWorkerShutdownController,
    private val extensionRegistry: ExtensionRegistry,
    private val permissionAdapter: ExtensionPermissionAdapter? = null
) {
    // Secondary constructor to maintain compatibility with legacy callers passing PermissionManager
    constructor(
        serviceWorkerRegistry: ServiceWorkerRegistry,
        wakeController: ServiceWorkerWakeController,
        shutdownController: ServiceWorkerShutdownController,
        extensionRegistry: ExtensionRegistry,
        permissionManager: PermissionManager
    ) : this(
        serviceWorkerRegistry = serviceWorkerRegistry,
        wakeController = wakeController,
        shutdownController = shutdownController,
        extensionRegistry = extensionRegistry,
        permissionAdapter = ExtensionPermissionAdapter(permissionManager.context)
    )

    fun onExtensionInstalled(
        extension: ParsedExtension,
        reason: String = "install",
        previousVersion: String = ""
    ) {
        val serviceWorkerPath = extension.backgroundSpec.serviceWorker.ifBlank {
            if (extension.isServiceWorker && extension.backgroundScripts.isNotEmpty()) {
                extension.backgroundScripts.first()
            } else ""
        }

        if (serviceWorkerPath.isBlank()) return

        val existing = serviceWorkerRegistry.getWorker(extension.id)
        if (existing != null) {
            shutdownController.suspendWorker(extension.id, notifyOnSuspend = false)
            serviceWorkerRegistry.unregister(extension.id)
        }

        serviceWorkerRegistry.register(extension.id, serviceWorkerPath, isMV3 = extension.manifestVersion >= 3)
        serviceWorkerRegistry.transitionState(extension.id, ServiceWorkerState.DORMANT)

        // Wake to fire runtime.onInstalled
        val installedEvent = QueuedServiceWorkerEvent(
            eventId = "installed_${System.currentTimeMillis()}",
            eventName = "runtime.onInstalled",
            payload = JSONObject().apply {
                put("reason", reason)
                if (previousVersion.isNotBlank()) {
                    put("previousVersion", previousVersion)
                }
            }
        )

        wakeController.wakeAndExecute(extension.id, installedEvent)
    }

    fun onExtensionStartup(extension: ParsedExtension) {
        val worker = serviceWorkerRegistry.getWorker(extension.id) ?: return
        if (!extensionRegistry.isExtensionEnabled(extension.id)) return

        if (worker.state == ServiceWorkerState.DORMANT || worker.state == ServiceWorkerState.SUSPEND || worker.state == ServiceWorkerState.SUSPENDED || worker.state == ServiceWorkerState.STOPPED || worker.state == ServiceWorkerState.REGISTERED) {
            val startupEvent = QueuedServiceWorkerEvent(
                eventId = "startup_${System.currentTimeMillis()}",
                eventName = "runtime.onStartup",
                payload = JSONObject()
            )
            wakeController.wakeAndExecute(extension.id, startupEvent)
        }
    }

    fun onExtensionDisabled(extensionId: String) {
        shutdownController.suspendWorker(extensionId, notifyOnSuspend = false)
        serviceWorkerRegistry.transitionState(extensionId, ServiceWorkerState.DISABLED)
    }

    fun onExtensionUninstalled(extensionId: String) {
        shutdownController.suspendWorker(extensionId, notifyOnSuspend = false)
        serviceWorkerRegistry.transitionState(extensionId, ServiceWorkerState.UNINSTALLED)
        serviceWorkerRegistry.unregister(extensionId)
    }

    fun onProcessRestart() {
        val allExts = extensionRegistry.getAllRegisteredExtensions()
        for (info in allExts) {
            val ext = info.extension
            val swPath = ext.backgroundSpec.serviceWorker.ifBlank {
                if (ext.isServiceWorker && ext.backgroundScripts.isNotEmpty()) ext.backgroundScripts.first() else ""
            }
            if (swPath.isNotBlank()) {
                serviceWorkerRegistry.register(ext.id, swPath, isMV3 = ext.manifestVersion >= 3)
                serviceWorkerRegistry.transitionState(ext.id, ServiceWorkerState.DORMANT)
            }
        }
    }

    fun onTabClose(tabId: String) {
        // Process tab close events for ALL registered MV3 service workers (wake dormant ones as required)
        val allRegisteredWorkers = serviceWorkerRegistry.getAllWorkers()
        for (w in allRegisteredWorkers) {
            if (w.state == ServiceWorkerState.DISABLED || w.state == ServiceWorkerState.UNINSTALLED || w.state == ServiceWorkerState.FAILED) {
                continue
            }
            val tabRemovedEvent = QueuedServiceWorkerEvent(
                eventId = "tab_close_${tabId}_${System.currentTimeMillis()}",
                eventName = "tabs.onRemoved",
                payload = JSONObject().apply { put("tabId", tabId) }
            )
            wakeController.wakeAndExecute(w.extensionId, tabRemovedEvent)
        }
    }

    fun onPrivateModeToggle(extensionId: String, isPrivate: Boolean) {
        if (isPrivate) {
            val allowed = permissionAdapter?.isAllowedInPrivate(extensionId)
                ?: extensionRegistry.getExtension(extensionId)?.allowedInPrivate
                ?: false
            if (!allowed) {
                shutdownController.suspendWorker(extensionId, notifyOnSuspend = false)
            }
        }
    }
}
