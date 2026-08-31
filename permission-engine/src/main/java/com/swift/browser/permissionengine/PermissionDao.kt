package com.swift.browser.permissionengine

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PermissionDao {
    @Query("SELECT * FROM swift_site_permissions")
    fun getAllPermissionsFlow(): Flow<List<PermissionEntity>>

    @Query("SELECT * FROM swift_site_permissions")
    suspend fun getAllPermissions(): List<PermissionEntity>

    @Query("SELECT * FROM swift_site_permissions WHERE origin = :origin")
    suspend fun getPermissionsByOrigin(origin: String): List<PermissionEntity>

    @Query("SELECT * FROM swift_site_permissions WHERE origin = :origin AND permission_type = :permissionType LIMIT 1")
    suspend fun getPermission(origin: String, permissionType: String): PermissionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPermission(permission: PermissionEntity)

    @Query("DELETE FROM swift_site_permissions WHERE origin = :origin AND permission_type = :permissionType")
    suspend fun deletePermission(origin: String, permissionType: String)

    @Query("DELETE FROM swift_site_permissions WHERE origin = :origin")
    suspend fun deletePermissionsForOrigin(origin: String)

    @Query("DELETE FROM swift_site_permissions")
    suspend fun clearAll()
}
