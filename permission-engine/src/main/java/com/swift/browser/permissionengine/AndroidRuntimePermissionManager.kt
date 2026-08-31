package com.swift.browser.permissionengine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object AndroidRuntimePermissionManager {

    fun interface SystemPermissionRequester {
        fun requestPermissions(
            requestId: String,
            permissions: List<String>,
            callback: (AndroidPermissionResult) -> Unit
        )
    }

    private var systemRequester: SystemPermissionRequester? = null

    fun registerSystemRequester(requester: SystemPermissionRequester) {
        systemRequester = requester
    }

    fun unregisterSystemRequester(requester: SystemPermissionRequester) {
        if (systemRequester == requester) {
            systemRequester = null
        }
    }

    fun requestAndroidPermissions(
        context: Context,
        requestId: String,
        permissions: List<String>,
        callback: (AndroidPermissionResult) -> Unit
    ) {
        val requester = systemRequester
        if (requester != null) {
            requester.requestPermissions(requestId, permissions, callback)
        } else {
            val missing = permissions.filter { !hasPermission(context, it) }
            if (missing.isEmpty()) {
                callback(
                    AndroidPermissionResult(
                        granted = true,
                        denied = false,
                        permanentlyDenied = false,
                        individuallyGrantedPermissions = permissions.associateWith { true }
                    )
                )
            } else {
                callback(
                    AndroidPermissionResult(
                        granted = false,
                        denied = true,
                        permanentlyDenied = false,
                        individuallyGrantedPermissions = permissions.associateWith { hasPermission(context, it) }
                    )
                )
            }
        }
    }

    fun hasPermission(context: Context, androidPermission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, androidPermission) == PackageManager.PERMISSION_GRANTED
    }

    fun mapToAndroidPermissions(resources: Array<String>): List<String> {
        return PermissionDescriptorRegistry.getAndroidPermissionsForResources(resources.toList())
    }

    fun mapToAndroidPermissionsFromType(permissionType: String): List<String> {
        return PermissionDescriptorRegistry.getAndroidPermissionsForType(permissionType)
    }

    fun mapToAndroidPermission(permissionType: String): String? {
        return PermissionDescriptorRegistry.getAndroidPermissionsForType(permissionType).firstOrNull()
    }

    fun hasRecordAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}


