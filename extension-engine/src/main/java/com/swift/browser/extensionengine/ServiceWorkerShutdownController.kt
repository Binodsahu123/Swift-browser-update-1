package com.swift.browser.extensionengine

import android.os.Handler
import android.os.Looper

/**
 * Controller enforcing real resource shutdown/suspension of idle MV3 extension service workers.
 * Ensures worker JS runtime instances and hidden WebView resources are actually stopped and destroyed upon suspension.
 */
class ServiceWorkerShutdownController(
    private val serviceWorkerRegistry: ServiceWorkerRegistry,
    private val backgroundScriptManager: BackgroundScriptManager,
    private val messageBus: MessageBus,
    private val portManager: PortManager? = null,
    private val swJsRuntime: ServiceWorkerJsRuntime? = null
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    interface SuspendListener {
        fun onWorkerSuspended(extensionId: String)
    }

    private val suspendListeners = mutableListOf<SuspendListener>()

    fun addSuspendListener(listener: SuspendListener) {
        suspendListeners.add(listener)
    }

    fun removeSuspendListener(listener: SuspendListener) {
        suspendListeners.remove(listener)
    }

    /**
     * Suspends and shuts down the background worker for an extension, destroying its background execution context.
     */
    fun suspendWorker(extensionId: String, notifyOnSuspend: Boolean = true): Boolean {
        val worker = serviceWorkerRegistry.getWorker(extensionId) ?: return false

        // Do not repeat suspend if already suspended or uninstalled
        if (worker.state == ServiceWorkerState.SUSPENDED || worker.state == ServiceWorkerState.STOPPED || worker.state == ServiceWorkerState.UNINSTALLED) return true

        // If there are active async callbacks, do not suspend
        if (worker.activeCallbacks.isNotEmpty()) return false

        serviceWorkerRegistry.transitionState(extensionId, ServiceWorkerState.SUSPENDING)

        if (notifyOnSuspend) {
            try {
                // Dispatch runtime.onSuspend event prior to resource destruction
                messageBus.broadcastMessage(
                    extensionId = extensionId,
                    senderTabId = null,
                    message = org.json.JSONObject().apply {
                        put("event", "runtime.onSuspend")
                        put("timestamp", System.currentTimeMillis())
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Port cleanup on suspend
        try {
            portManager?.cleanupForExtension(extensionId)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Perform real destruction of background WebView and worker JS runtime
        mainHandler.post {
            try {
                swJsRuntime?.stopWorker(extensionId)
                backgroundScriptManager.stopBackgroundWorker(extensionId)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                serviceWorkerRegistry.transitionState(extensionId, ServiceWorkerState.SUSPENDED)
                worker.executionContext?.state = ServiceWorkerState.SUSPENDED
                suspendListeners.forEach { it.onWorkerSuspended(extensionId) }
            }
        }

        return true
    }

    /**
     * Checks idle service workers and suspends them if they exceed maximum idle duration.
     */
    fun checkAndSuspendIdleWorkers(maxIdleMs: Long = 30_000L) {
        val now = System.currentTimeMillis()
        val workers = serviceWorkerRegistry.getAllWorkers()

        for (worker in workers) {
            if (worker.state == ServiceWorkerState.ACTIVE || worker.state == ServiceWorkerState.IDLE || worker.state == ServiceWorkerState.RUNNING || worker.state == ServiceWorkerState.EVENT) {
                if (worker.activeCallbacks.isEmpty() && now - worker.lastActiveTimestamp > maxIdleMs && worker.pendingEvents.isEmpty()) {
                    suspendWorker(worker.extensionId)
                }
            }
        }
    }
}
