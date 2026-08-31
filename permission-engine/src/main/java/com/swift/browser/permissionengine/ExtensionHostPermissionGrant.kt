package com.swift.browser.permissionengine

/**
 * First-class host permission grant representation for extension security evaluation.
 */
data class ExtensionHostPermissionGrant(
    val extensionId: String,
    val pattern: String,
    val state: String = "GRANTED", // "GRANTED", "WITHHELD", "DENIED"
    val scope: String = "HOST",
    val source: String = "OPTIONAL_RUNTIME",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isPrivateScope: Boolean = false
)
