package com.swift.browser.permissionengine

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "swift_site_permissions")
data class PermissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "origin") val origin: String,
    @ColumnInfo(name = "permission_type") val permissionType: String,
    @ColumnInfo(name = "decision") val decision: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "expires_at") val expiresAt: Long = 0L,
    @ColumnInfo(name = "is_temporary") val isTemporary: Boolean = false
)
