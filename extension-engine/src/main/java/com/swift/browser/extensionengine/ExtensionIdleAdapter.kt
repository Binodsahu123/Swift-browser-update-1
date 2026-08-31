package com.swift.browser.extensionengine

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import org.json.JSONObject

class ExtensionIdleAdapter(
    private val permissionManager: PermissionManager,
    private val registry: ExtensionRegistry
) {
    private var detectionIntervalSeconds: Int = 60

    private fun validate(sender: ExtensionSender): ParsedExtension {
        val extId = sender.extensionId.lowercase().trim()
        val ext = registry.getExtension(extId) ?: throw SecurityException("Extension $extId not found")
        if (!registry.isExtensionEnabled(extId)) {
            throw SecurityException("Extension $extId is disabled")
        }
        if (sender.isPrivate && !permissionManager.isAllowedInPrivate(extId)) {
            throw SecurityException("Extension $extId is not allowed in private mode")
        }
        if (!permissionManager.hasApiPermission(extId, ext.permissions, "idle")) {
            throw SecurityException("SecurityError: Extension does not have 'idle' permission")
        }
        return ext
    }

    fun queryState(sender: ExtensionSender, detectionIntervalInSeconds: Int, context: Context? = null): JSONObject {
        validate(sender)
        val powerManager = context?.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val keyguardManager = context?.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

        val isInteractive = powerManager?.isInteractive ?: true
        val isLocked = keyguardManager?.isKeyguardLocked ?: false

        val state = when {
            isLocked -> "locked"
            !isInteractive -> "idle"
            else -> "active"
        }

        return JSONObject().put("state", state)
    }

    fun setDetectionInterval(sender: ExtensionSender, intervalInSeconds: Int): JSONObject {
        validate(sender)
        this.detectionIntervalSeconds = intervalInSeconds.coerceAtLeast(15)
        return JSONObject().put("status", "success").put("interval", this.detectionIntervalSeconds)
    }
}
