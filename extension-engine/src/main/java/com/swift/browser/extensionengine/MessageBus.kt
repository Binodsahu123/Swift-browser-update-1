package com.swift.browser.extensionengine

import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList
import java.lang.ref.WeakReference

interface MessageListener {
    fun onMessageReceived(extensionId: String, senderTabId: String?, message: JSONObject, callbackId: String?, targetTabId: String?)
    fun onStructuredMessageReceived(sender: ExtensionSender, message: JSONObject, callbackId: String?, targetTabId: String?) {
        onMessageReceived(sender.extensionId, sender.tabId, message, callbackId, targetTabId)
    }
    fun onResponseReceived(extensionId: String, callbackId: String, response: Any)
}

interface PortConnectionListener {
    fun onPortConnect(extensionId: String, channelId: String, portName: String, senderId: String)
    fun onPortMessage(channelId: String, message: JSONObject)
    fun onPortDisconnect(channelId: String)
}

class MessageBus {
    private val listeners = CopyOnWriteArrayList<WeakReference<MessageListener>>()
    private val portListeners = CopyOnWriteArrayList<WeakReference<PortConnectionListener>>()

    fun registerListener(listener: MessageListener) {
        removeGarbageCollected()
        if (listeners.none { it.get() === listener }) {
            listeners.add(WeakReference(listener))
        }
    }

    fun unregisterListener(listener: MessageListener) {
        listeners.removeAll { it.get() === listener || it.get() == null }
    }

    fun registerPortListener(listener: PortConnectionListener) {
        removeGarbageCollected()
        if (portListeners.none { it.get() === listener }) {
            portListeners.add(WeakReference(listener))
        }
    }

    fun unregisterPortListener(listener: PortConnectionListener) {
        portListeners.removeAll { it.get() === listener || it.get() == null }
    }

    private fun removeGarbageCollected() {
        listeners.removeAll { it.get() == null }
        portListeners.removeAll { it.get() == null }
    }

    /**
     * Propagates a standard chrome.runtime message payload with structured sender context
     */
    fun broadcastMessage(sender: ExtensionSender, message: JSONObject, callbackId: String? = null, targetTabId: String? = null) {
        removeGarbageCollected()
        listeners.forEach { ref ->
            ref.get()?.let { listener ->
                try {
                    listener.onStructuredMessageReceived(sender, message, callbackId, targetTabId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun broadcastMessage(extensionId: String, senderTabId: String?, message: JSONObject, callbackId: String? = null, targetTabId: String? = null) {
        val sender = ExtensionSender(extensionId = extensionId, tabId = senderTabId)
        broadcastMessage(sender, message, callbackId, targetTabId)
    }

    fun broadcastResponse(extensionId: String, callbackId: String, response: Any) {
        removeGarbageCollected()
        listeners.forEach { ref ->
            ref.get()?.let { listener ->
                try {
                    listener.onResponseReceived(extensionId, callbackId, response)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun broadcastPortConnect(extensionId: String, channelId: String, portName: String, senderId: String) {
        removeGarbageCollected()
        portListeners.forEach { ref ->
            ref.get()?.let { listener ->
                try {
                    listener.onPortConnect(extensionId, channelId, portName, senderId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun broadcastPortMessage(channelId: String, message: JSONObject) {
        removeGarbageCollected()
        portListeners.forEach { ref ->
            ref.get()?.let { listener ->
                try {
                    listener.onPortMessage(channelId, message)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun broadcastPortDisconnect(channelId: String) {
        removeGarbageCollected()
        portListeners.forEach { ref ->
            ref.get()?.let { listener ->
                try {
                    listener.onPortDisconnect(channelId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
