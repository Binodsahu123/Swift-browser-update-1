package com.swift.browser.extensionengine

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

class ExtensionMessageRouter(
    private val registry: ExtensionRegistry,
    private val permissionManager: PermissionManager,
    private val messageBus: MessageBus,
    private val portManager: PortManager,
    private val swRegistry: ServiceWorkerRegistry,
    private val swWakeController: ServiceWorkerWakeController
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingMessages = ConcurrentHashMap<String, PendingMessage>()

    // For keeping track of active port states
    private val portStates = ConcurrentHashMap<String, PortState>()

    fun getPortState(channelId: String): PortState {
        return portStates[channelId] ?: PortState.DISCONNECTED
    }

    fun setPortState(channelId: String, state: PortState) {
        portStates[channelId] = state
    }

    /**
     * Checks if a web page is allowed to connect/message the target extension based on externally_connectable.
     */
    fun checkExternallyConnectable(sender: ExtensionSender, targetExtId: String): Boolean {
        val targetExt = registry.getExtension(targetExtId) ?: return false
        val spec = targetExt.externallyConnectable

        // If externally_connectable is not declared in manifest, default is deny for web pages
        if (sender.contextType == ExtensionContextType.WEB_PAGE) {
            val url = sender.url ?: return false
            if (spec.matches.isEmpty()) return false
            return spec.matches.any { pattern ->
                permissionManager.hasHostPermission(targetExtId, listOf(pattern), emptyList(), url)
            }
        }

        // If another extension is trying to connect
        if (sender.extensionId != targetExtId) {
            if (spec.ids.isEmpty()) {
                // If spec is entirely empty, standard Chrome allows cross-extension connections by default
                return true
            }
            return spec.ids.contains(sender.extensionId)
        }

        return true
    }

    /**
     * Entry point to handle runtime.sendMessage
     */
    fun handleSendMessage(
        sender: ExtensionSender,
        targetExtensionId: String,
        payload: JSONObject,
        callbackId: String?,
        expectsResponse: Boolean,
        onResponse: (Any?, String?) -> Unit
    ) {
        val cleanTarget = targetExtensionId.lowercase().trim()
        val ext = registry.getExtension(cleanTarget)

        if (ext == null) {
            onResponse(null, MessagingError.EXTENSION_NOT_FOUND)
            return
        }

        if (!registry.isExtensionEnabled(cleanTarget)) {
            onResponse(null, MessagingError.EXTENSION_DISABLED)
            return
        }

        // Enforce private-mode context propagation (Section 30)
        if (sender.isPrivate && !permissionManager.isAllowedInPrivate(cleanTarget)) {
            onResponse(null, MessagingError.PRIVATE_CONTEXT_INVALID)
            return
        }

        // Validate Sender (Section 7 / 13)
        val senderVal = ExtensionMessagingValidator.validateSender(sender, registry, permissionManager)
        if (senderVal is ExtensionMessagingValidator.ValidationResult.Denied) {
            onResponse(null, MessagingError.PERMISSION_DENIED)
            return
        }

        // Validate Cross-Extension / Externally Connectable
        if (sender.contextType == ExtensionContextType.WEB_PAGE) {
            if (!checkExternallyConnectable(sender, cleanTarget)) {
                onResponse(null, MessagingError.EXTERNAL_CONNECTION_DENIED)
                return
            }
        } else if (sender.extensionId != cleanTarget) {
            val crossVal = ExtensionMessagingValidator.validateCrossExtension(sender, cleanTarget, registry, permissionManager)
            if (crossVal is ExtensionMessagingValidator.ValidationResult.Denied) {
                onResponse(null, MessagingError.EXTERNAL_CONNECTION_DENIED)
                return
            }
            if (!checkExternallyConnectable(sender, cleanTarget)) {
                onResponse(null, MessagingError.EXTERNAL_CONNECTION_DENIED)
                return
            }
        }

        // Create the canonical ExtensionMessage object
        val messageId = UUID.randomUUID().toString()
        val targetGen = registry.getExtensionGeneration(cleanTarget).toString()
        val msg = ExtensionMessage(
            messageId = messageId,
            extensionId = sender.extensionId,
            targetExtensionId = cleanTarget,
            sender = sender,
            payload = payload,
            privateSessionId = sender.privateSessionId,
            runtimeGenerationId = targetGen,
            callbackId = callbackId,
            expectsResponse = expectsResponse
        )

        // Track pending message for asynchronous response lifecycle
        if (expectsResponse && callbackId != null) {
            // Composite key to prevent global ID collisions (Section 16)
            val key = "${cleanTarget}_${targetGen}_$callbackId"
            val pending = PendingMessage(
                messageId = messageId,
                callbackId = callbackId,
                extensionId = cleanTarget,
                runtimeGenerationId = targetGen,
                deadline = msg.deadline,
                onResponse = onResponse
            )
            pendingMessages[key] = pending

            // Bounded safety timeout handler (Section 15)
            mainHandler.postDelayed({
                val expired = pendingMessages.remove(key)
                if (expired != null) {
                    onResponse(null, MessagingError.MESSAGE_TIMEOUT)
                }
            }, msg.deadline - msg.createdAt)
        }

        // Target Context Resolution (Section 7, 8, 9, 10)
        if (ext.isServiceWorker) {
            // Wake dormant worker if necessary
            val event = QueuedServiceWorkerEvent(
                eventId = "msg_$messageId",
                eventName = "runtime.onMessage",
                payload = payload
            )
            swWakeController.wakeAndExecute(cleanTarget, event, object : ServiceWorkerWakeController.WakeCallback {
                override fun onWoken(extensionId: String, success: Boolean) {
                    if (success) {
                        deliverToListeners(msg)
                    } else {
                        onResponse(null, MessagingError.MESSAGE_TARGET_UNAVAILABLE)
                    }
                }
            })
        } else {
            deliverToListeners(msg)
        }
    }

    /**
     * Entry point to deliver a successful response back to the sender
     */
    fun handleSendResponse(extensionId: String, targetCallbackId: String, responseData: Any) {
        val gen = registry.getExtensionGeneration(extensionId).toString()
        val key = "${extensionId}_${gen}_$targetCallbackId"
        val pending = pendingMessages.remove(key)
        if (pending != null) {
            pending.onResponse(responseData, null)
        }
    }

    private fun deliverToListeners(msg: ExtensionMessage) {
        // Use the MessageBus transport to broadcast cleanly
        // We will include targetExtensionId in the payload or targetTabId so listeners can filter precisely
        val wrapped = JSONObject().apply {
            put("__targetExtensionId__", msg.targetExtensionId)
            put("__targetGenerationId__", msg.runtimeGenerationId)
            put("__payload__", msg.payload)
        }
        messageBus.broadcastMessage(msg.sender, wrapped, msg.callbackId, msg.sender.tabId)
    }

    /**
     * Clean up private sessions
     */
    fun cleanupPrivateSession(privateSessionId: String) {
        val keysToRemove = pendingMessages.filter { it.value.runtimeGenerationId == privateSessionId }.keys
        keysToRemove.forEach { pendingMessages.remove(it) }

        val activePorts = portManager.registry.getAllActive()
        activePorts.forEach { port ->
            if (port.sender?.privateSessionId == privateSessionId) {
                portManager.disconnect(port.channelId)
                portStates.remove(port.channelId)
            }
        }
    }
}
