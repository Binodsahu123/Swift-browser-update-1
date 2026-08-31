package com.swift.browser.permissionengine

data class AndroidPermissionResult(
    val granted: Boolean,
    val denied: Boolean = !granted,
    val permanentlyDenied: Boolean = false,
    val individuallyGrantedPermissions: Map<String, Boolean> = emptyMap()
)
