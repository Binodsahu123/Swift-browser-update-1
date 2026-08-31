package com.swift.browser.extensionengine

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

data class ExtensionAlarm(
    val name: String,
    val scheduledTime: Long,
    val periodInMinutes: Double?
)

class ExtensionAlarmsAdapter(
    private val permissionManager: PermissionManager,
    private val registry: ExtensionRegistry,
    private val eventManager: EventManager
) {
    var serviceWorkerEventDispatcher: ServiceWorkerEventDispatcher? = null
    private val alarmsMap = ConcurrentHashMap<String, ExtensionAlarm>() // "extId_alarmName"
    private val handlerMap = ConcurrentHashMap<String, Runnable>()

    private fun validate(sender: ExtensionSender): ParsedExtension {
        val extId = sender.extensionId.lowercase().trim()
        val ext = registry.getExtension(extId) ?: throw SecurityException("Extension $extId not found")
        if (!registry.isExtensionEnabled(extId)) {
            throw SecurityException("Extension $extId is disabled")
        }
        if (sender.isPrivate && !permissionManager.isAllowedInPrivate(extId)) {
            throw SecurityException("Extension $extId is not allowed in private mode")
        }
        if (!permissionManager.hasApiPermission(extId, ext.permissions, "alarms")) {
            throw SecurityException("SecurityError: Extension does not have 'alarms' permission")
        }
        return ext
    }

    fun create(sender: ExtensionSender, name: String, alarmInfo: JSONObject): JSONObject {
        val ext = validate(sender)
        val alarmName = name.ifBlank { "default_alarm" }
        val key = "${ext.id}_$alarmName"

        clearInternal(key)

        val delayInMinutes = if (alarmInfo.has("delayInMinutes")) alarmInfo.optDouble("delayInMinutes", 1.0) else null
        val whenMs = if (alarmInfo.has("when")) alarmInfo.optLong("when", System.currentTimeMillis() + 60000L) else null
        val periodInMinutes = if (alarmInfo.has("periodInMinutes")) alarmInfo.optDouble("periodInMinutes") else null

        val now = System.currentTimeMillis()
        val scheduledTime = when {
            delayInMinutes != null -> now + (delayInMinutes * 60000).toLong()
            whenMs != null -> whenMs
            else -> now + 60000L
        }

        val alarm = ExtensionAlarm(name = alarmName, scheduledTime = scheduledTime, periodInMinutes = periodInMinutes)
        alarmsMap[key] = alarm

        val delayMs = (scheduledTime - now).coerceAtLeast(0L)
        val mainHandler = Handler(Looper.getMainLooper())

        val runnable = object : Runnable {
            override fun run() {
                if (!registry.isExtensionEnabled(ext.id)) return
                
                if (ext.manifestVersion >= 3 && serviceWorkerEventDispatcher != null) {
                    serviceWorkerEventDispatcher?.dispatchAlarmEvent(ext.id, alarmName, alarm.scheduledTime)
                } else {
                    eventManager.triggerEvent("alarms.onAlarm", JSONObject().apply {
                        put("name", alarmName)
                        put("scheduledTime", alarm.scheduledTime)
                        alarm.periodInMinutes?.let { put("periodInMinutes", it) }
                    })
                }

                if (alarm.periodInMinutes != null && alarm.periodInMinutes > 0) {
                    val nextScheduled = System.currentTimeMillis() + (alarm.periodInMinutes * 60000).toLong()
                    alarmsMap[key] = alarm.copy(scheduledTime = nextScheduled)
                    mainHandler.postDelayed(this, (alarm.periodInMinutes * 60000).toLong())
                } else {
                    alarmsMap.remove(key)
                    handlerMap.remove(key)
                }
            }
        }

        handlerMap[key] = runnable
        mainHandler.postDelayed(runnable, delayMs)

        return JSONObject().put("status", "created").put("name", alarmName)
    }

    fun get(sender: ExtensionSender, name: String): JSONObject? {
        val ext = validate(sender)
        val alarmName = name.ifBlank { "default_alarm" }
        val alarm = alarmsMap["${ext.id}_$alarmName"] ?: return null
        return JSONObject().apply {
            put("name", alarm.name)
            put("scheduledTime", alarm.scheduledTime)
            alarm.periodInMinutes?.let { put("periodInMinutes", it) }
        }
    }

    fun getAll(sender: ExtensionSender): JSONArray {
        val ext = validate(sender)
        val result = JSONArray()
        val prefix = "${ext.id}_"
        for ((k, alarm) in alarmsMap) {
            if (k.startsWith(prefix)) {
                result.put(JSONObject().apply {
                    put("name", alarm.name)
                    put("scheduledTime", alarm.scheduledTime)
                    alarm.periodInMinutes?.let { put("periodInMinutes", it) }
                })
            }
        }
        return result
    }

    fun clear(sender: ExtensionSender, name: String): JSONObject {
        val ext = validate(sender)
        val alarmName = name.ifBlank { "default_alarm" }
        val key = "${ext.id}_$alarmName"
        val existed = alarmsMap.containsKey(key)
        clearInternal(key)
        return JSONObject().put("status", if (existed) "cleared" else "not_found").put("cleared", existed)
    }

    fun clearAll(sender: ExtensionSender): JSONObject {
        val ext = validate(sender)
        val prefix = "${ext.id}_"
        val keys = alarmsMap.keys().toList().filter { it.startsWith(prefix) }
        for (k in keys) {
            clearInternal(k)
        }
        return JSONObject().put("status", "cleared").put("count", keys.size)
    }

    private fun clearInternal(key: String) {
        val runnable = handlerMap.remove(key)
        if (runnable != null) {
            Handler(Looper.getMainLooper()).removeCallbacks(runnable)
        }
        alarmsMap.remove(key)
    }
}
