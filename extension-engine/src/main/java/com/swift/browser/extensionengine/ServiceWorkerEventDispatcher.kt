package com.swift.browser.extensionengine

import org.json.JSONObject

/**
 * Event dispatcher for MV3 extension service worker events.
 * Listens for system and extension events and wakes dormant workers as needed before dispatching.
 */
class ServiceWorkerEventDispatcher(
    private val serviceWorkerRegistry: ServiceWorkerRegistry,
    private val wakeController: ServiceWorkerWakeController,
    private val messageBus: MessageBus,
    private val portManager: PortManager
) : MessageListener {

    init {
        messageBus.registerListener(this)
    }

    override fun onMessageReceived(
        extensionId: String,
        senderTabId: String?,
        message: JSONObject,
        callbackId: String?,
        targetTabId: String?
    ) {
        // Handled via broadcast/direct dispatch
    }

    override fun onResponseReceived(extensionId: String, callbackId: String, response: Any) {
        val worker = serviceWorkerRegistry.getWorker(extensionId)
        if (worker != null && worker.activeCallbacks.contains(callbackId)) {
            worker.activeCallbacks.remove(callbackId)
            if (worker.activeCallbacks.isEmpty()) {
                worker.lastActiveTimestamp = System.currentTimeMillis()
                serviceWorkerRegistry.transitionState(extensionId, ServiceWorkerState.IDLE)
            }
        }
    }

    fun dispatchRuntimeEvent(
        extensionId: String,
        eventName: String,
        payload: JSONObject = JSONObject()
    ) {
        val event = QueuedServiceWorkerEvent(
            eventId = "${eventName}_${System.currentTimeMillis()}",
            eventName = eventName,
            payload = payload
        )

        wakeController.wakeAndExecute(extensionId, event, object : ServiceWorkerWakeController.WakeCallback {
            override fun onWoken(extensionId: String, success: Boolean) {
                if (success) {
                    serviceWorkerRegistry.transitionState(extensionId, ServiceWorkerState.EVENT)
                    messageBus.broadcastMessage(
                        extensionId = extensionId,
                        senderTabId = null,
                        message = JSONObject().apply {
                            put("type", "EVENT_DISPATCH")
                            put("eventName", eventName)
                            put("data", payload)
                            put("event", eventName)
                        }
                    )
                    serviceWorkerRegistry.transitionState(extensionId, ServiceWorkerState.ACTIVE)
                }
            }
        })
    }

    fun dispatchAlarmEvent(extensionId: String, alarmName: String, scheduledTime: Long) {
        val payload = JSONObject().apply {
            put("name", alarmName)
            put("scheduledTime", scheduledTime)
        }
        dispatchRuntimeEvent(extensionId, "alarms.onAlarm", payload)
    }

    fun dispatchMessageEvent(
        extensionId: String,
        senderTabId: String?,
        message: JSONObject,
        callbackId: String? = null
    ) {
        val event = QueuedServiceWorkerEvent(
            eventId = "message_${System.currentTimeMillis()}",
            eventName = "runtime.onMessage",
            payload = message
        )

        val worker = serviceWorkerRegistry.getWorker(extensionId)
        if (callbackId != null && worker != null) {
            worker.activeCallbacks.add(callbackId)
            
            // Set up a 30 seconds timeout to clean up stale callbacks
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val w = serviceWorkerRegistry.getWorker(extensionId)
                if (w != null && w.activeCallbacks.contains(callbackId)) {
                    w.activeCallbacks.remove(callbackId)
                    if (w.activeCallbacks.isEmpty()) {
                        w.lastActiveTimestamp = System.currentTimeMillis()
                        serviceWorkerRegistry.transitionState(extensionId, ServiceWorkerState.IDLE)
                    }
                    com.swift.browser.extensionengine.ExtensionDebuggerEngine.instance.logError(
                        extensionId,
                        "Extension Service Worker",
                        com.swift.browser.extensionengine.DebugErrorType.RUNTIME,
                        "STALE_CALLBACK_IGNORED: callbackId $callbackId timed out after 30s"
                    )
                }
            }, 30_000L)
        }

        wakeController.wakeAndExecute(extensionId, event, object : ServiceWorkerWakeController.WakeCallback {
            override fun onWoken(extensionId: String, success: Boolean) {
                if (success) {
                    serviceWorkerRegistry.transitionState(extensionId, ServiceWorkerState.EVENT)
                    messageBus.broadcastMessage(extensionId, senderTabId, message, callbackId)
                    serviceWorkerRegistry.transitionState(extensionId, ServiceWorkerState.ACTIVE)
                } else {
                    if (callbackId != null && worker != null) {
                        worker.activeCallbacks.remove(callbackId)
                    }
                }
            }
        })
    }

    fun dispatchPortConnectEvent(
        extensionId: String,
        channelId: String,
        portName: String,
        senderId: String
    ) {
        val payload = JSONObject().apply {
            put("channelId", channelId)
            put("portName", portName)
            put("senderId", senderId)
        }
        val event = QueuedServiceWorkerEvent(
            eventId = "port_connect_${channelId}",
            eventName = "runtime.onConnect",
            payload = payload
        )

        wakeController.wakeAndExecute(extensionId, event, object : ServiceWorkerWakeController.WakeCallback {
            override fun onWoken(extensionId: String, success: Boolean) {
                if (success) {
                    serviceWorkerRegistry.transitionState(extensionId, ServiceWorkerState.EVENT)
                    messageBus.broadcastPortConnect(extensionId, channelId, portName, senderId)
                    serviceWorkerRegistry.transitionState(extensionId, ServiceWorkerState.ACTIVE)
                }
            }
        })
    }
}
