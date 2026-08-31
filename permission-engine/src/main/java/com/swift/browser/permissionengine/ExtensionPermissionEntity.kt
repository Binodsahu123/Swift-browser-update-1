package com.swift.browser.permissionengine

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing an extension-scoped permission grant or withholding in the canonical
 * permission database.
 */
@Entity(
    tableName = "swift_extension_permissions",
    indices = [
        Index(value = ["extension_id", "permission", "is_private_scope"], unique = true)
    ]
)
data class ExtensionPermissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "extension_id") val extensionId: String,
    @ColumnInfo(name = "permission") val permission: String, // e.g. "tabs", "history", "*://*.example.com/*"
    @ColumnInfo(name = "scope") val scope: String, // "API", "HOST"
    @ColumnInfo(name = "state") val state: String, // "GRANTED", "WITHHELD", "DENIED"
    @ColumnInfo(name = "source") val source: String = "MANIFEST", // "MANIFEST", "OPTIONAL_RUNTIME", "USER_OVERRIDE"
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "expires_at") val expiresAt: Long = 0L,
    @ColumnInfo(name = "is_private_scope") val isPrivateScope: Boolean = false
)
