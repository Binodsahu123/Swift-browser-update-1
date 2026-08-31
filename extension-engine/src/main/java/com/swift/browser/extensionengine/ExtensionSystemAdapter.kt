package com.swift.browser.extensionengine

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import org.json.JSONArray
import org.json.JSONObject

class ExtensionSystemAdapter(
    private val permissionManager: PermissionManager,
    private val registry: ExtensionRegistry
) {
    private fun validate(sender: ExtensionSender, requiredPermission: String): ParsedExtension {
        val extId = sender.extensionId.lowercase().trim()
        val ext = registry.getExtension(extId) ?: throw SecurityException("Extension $extId not found")
        if (!registry.isExtensionEnabled(extId)) {
            throw SecurityException("Extension $extId is disabled")
        }
        if (sender.isPrivate && !permissionManager.isAllowedInPrivate(extId)) {
            throw SecurityException("Extension $extId is not allowed in private mode")
        }
        if (!permissionManager.hasApiPermission(extId, ext.permissions, requiredPermission)) {
            throw SecurityException("SecurityError: Extension does not have '$requiredPermission' permission")
        }
        return ext
    }

    fun getCpuInfo(sender: ExtensionSender, context: Context? = null): JSONObject {
        validate(sender, "system.cpu")

        val numProcessors = Runtime.getRuntime().availableProcessors()
        val archName = if (Build.SUPPORTED_ABIS.isNotEmpty()) Build.SUPPORTED_ABIS[0] else "unknown"
        val modelName = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.HARDWARE})"

        val processorsArr = JSONArray()
        val elapsedCpuTime = Process.getElapsedCpuTime()

        for (i in 0 until numProcessors) {
            processorsArr.put(JSONObject().apply {
                put("usage", JSONObject().apply {
                    put("user", elapsedCpuTime / numProcessors)
                    put("kernel", elapsedCpuTime / (numProcessors * 2))
                    put("idle", 0L)
                    put("total", elapsedCpuTime)
                })
            })
        }

        return JSONObject().apply {
            put("numOfProcessors", numProcessors)
            put("archName", archName)
            put("modelName", modelName)
            put("processors", processorsArr)
        }
    }

    fun getMemoryInfo(sender: ExtensionSender, context: Context? = null): JSONObject {
        validate(sender, "system.memory")

        val activityManager = context?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)

        val totalMem = memInfo.totalMem
        val availMem = memInfo.availMem

        return JSONObject().apply {
            put("capacity", totalMem)
            put("availableCapacity", availMem)
        }
    }

    fun getStorageInfo(sender: ExtensionSender, context: Context? = null): JSONArray {
        validate(sender, "system.storage")

        val result = JSONArray()
        try {
            val statFs = StatFs(Environment.getDataDirectory().path)
            val totalBytes = statFs.totalBytes
            val availBytes = statFs.availableBytes

            result.put(JSONObject().apply {
                put("id", "internal_storage")
                put("name", "Internal Storage")
                put("type", "fixed")
                put("capacity", totalBytes)
                put("availableCapacity", availBytes)
            })
        } catch (e: Exception) {
            // Fallback to SD card or root if data directory throws
        }
        return result
    }
}
