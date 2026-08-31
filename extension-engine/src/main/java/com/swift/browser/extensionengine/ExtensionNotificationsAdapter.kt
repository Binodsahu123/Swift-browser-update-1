package com.swift.browser.extensionengine

import android.app.NotificationManager
import android.content.Context
import com.swift.browser.notificationengine.NotificationBrowsingContext
import com.swift.browser.notificationengine.showWebNotificationHelper
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class ExtensionNotificationsAdapter(
    private val context: Context? = null,
    private val permissionManager: PermissionManager,
    private val registry: ExtensionRegistry,
    private val eventManager: EventManager
) {
    private val activeNotifications = ConcurrentHashMap<String, JSONObject>() // "extId_notifId"
    private val autoIdGenerator = AtomicInteger(1)

    private fun validate(sender: ExtensionSender): ParsedExtension {
        val extId = sender.extensionId.lowercase().trim()
        val ext = registry.getExtension(extId) ?: throw SecurityException("Extension $extId not found")
        if (!registry.isExtensionEnabled(extId)) {
            throw SecurityException("Extension $extId is disabled")
        }
        if (sender.isPrivate && !permissionManager.isAllowedInPrivate(extId)) {
            throw SecurityException("Extension $extId is not allowed in private mode")
        }
        if (!permissionManager.hasApiPermission(extId, ext.permissions, "notifications")) {
            throw SecurityException("SecurityError: Extension does not have 'notifications' permission")
        }
        return ext
    }

    companion object {
        fun cleanupExtensionState(adapter: ExtensionNotificationsAdapter, extensionId: String) {
            adapter.clearAllForExtension(extensionId)
        }
    }

    fun clearAllForExtension(extensionId: String) {
        val prefix = "${extensionId.lowercase().trim()}_"
        val keysToRemove = activeNotifications.keys().toList().filter { it.startsWith(prefix) }
        for (k in keysToRemove) {
            val notifId = k.removePrefix(prefix)
            activeNotifications.remove(k)
            context?.let { ctx ->
                try {
                    val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    val clickUrl = "chrome-extension://${extensionId.lowercase().trim()}/$notifId"
                    manager?.cancel(clickUrl.hashCode())
                } catch (_: Exception) {}
            }
        }
    }

    fun create(sender: ExtensionSender, notificationId: String?, options: JSONObject, delegate: BrowserDelegate? = null): JSONObject {
        val ext = validate(sender)
        val notifId = notificationId?.takeIf { it.isNotBlank() } ?: "notif_${autoIdGenerator.getAndIncrement()}"
        val title = options.optString("title", ext.name)
        val message = options.optString("message", options.optString("body", ""))

        val key = "${ext.id}_$notifId"
        activeNotifications[key] = options

        context?.let { ctx ->
            try {
                val clickUrl = "chrome-extension://${ext.id}/$notifId"
                showWebNotificationHelper(
                    context = ctx,
                    websiteUrl = "chrome-extension://${ext.id}",
                    websiteName = ext.name,
                    title = title,
                    body = message,
                    clickUrl = clickUrl,
                    contextMode = NotificationBrowsingContext(isPrivate = sender.isPrivate)
                )
            } catch (_: Exception) {
                delegate?.showNotification(title, message)
            }
        } ?: run {
            delegate?.showNotification(title, message)
        }

        return JSONObject().put("status", "created").put("notificationId", notifId)
    }

    fun update(sender: ExtensionSender, notificationId: String, options: JSONObject): JSONObject {
        val ext = validate(sender)
        val key = "${ext.id}_$notificationId"
        val existing = activeNotifications[key] ?: return JSONObject().put("updated", false)

        val keys = options.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            existing.put(k, options.get(k))
        }

        val title = existing.optString("title", ext.name)
        val message = existing.optString("message", existing.optString("body", ""))

        context?.let { ctx ->
            try {
                val clickUrl = "chrome-extension://${ext.id}/$notificationId"
                showWebNotificationHelper(
                    context = ctx,
                    websiteUrl = "chrome-extension://${ext.id}",
                    websiteName = ext.name,
                    title = title,
                    body = message,
                    clickUrl = clickUrl,
                    contextMode = NotificationBrowsingContext(isPrivate = sender.isPrivate)
                )
            } catch (_: Exception) {}
        }

        return JSONObject().put("updated", true)
    }

    fun clear(sender: ExtensionSender, notificationId: String): JSONObject {
        val ext = validate(sender)
        val key = "${ext.id}_$notificationId"
        val existed = activeNotifications.remove(key) != null

        if (existed) {
            context?.let { ctx ->
                try {
                    val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    val clickUrl = "chrome-extension://${ext.id}/$notificationId"
                    manager?.cancel(clickUrl.hashCode())
                } catch (_: Exception) {}
            }
            eventManager.triggerEvent("notifications.onClosed", JSONObject().apply {
                put("notificationId", notificationId)
                put("byUser", true)
            })
        }

        return JSONObject().put("cleared", existed)
    }

    fun getAll(sender: ExtensionSender): JSONObject {
        val ext = validate(sender)
        val result = JSONObject()
        val prefix = "${ext.id}_"
        for ((k, notif) in activeNotifications) {
            if (k.startsWith(prefix)) {
                val notifId = k.removePrefix(prefix)
                result.put(notifId, notif)
            }
        }
        return result
    }

    fun triggerClick(extensionId: String, notificationId: String, buttonIndex: Int? = null) {
        val ext = registry.getExtension(extensionId) ?: return
        if (!registry.isExtensionEnabled(extensionId)) return

        if (buttonIndex != null) {
            eventManager.triggerEvent("notifications.onButtonClicked", JSONObject().apply {
                put("notificationId", notificationId)
                put("buttonIndex", buttonIndex)
            })
        } else {
            eventManager.triggerEvent("notifications.onClicked", JSONObject().put("notificationId", notificationId))
        }
    }
}
