package com.swift.browser.extensionengine

data class PendingExtensionPermissionRequest(
    val extId: String,
    val extName: String,
    val permission: String,
    val onResult: (String) -> Unit
)
