package com.swift.browser.permissionengine

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExtensionPermissionDao {
    @Query("SELECT * FROM swift_extension_permissions")
    fun getAllExtensionPermissionsFlow(): Flow<List<ExtensionPermissionEntity>>

    @Query("SELECT * FROM swift_extension_permissions")
    suspend fun getAllExtensionPermissions(): List<ExtensionPermissionEntity>

    @Query("SELECT * FROM swift_extension_permissions WHERE extension_id = :extensionId")
    suspend fun getPermissionsForExtension(extensionId: String): List<ExtensionPermissionEntity>

    @Query("SELECT * FROM swift_extension_permissions WHERE extension_id = :extensionId AND is_private_scope = :isPrivateScope")
    suspend fun getPermissionsForExtensionScoped(extensionId: String, isPrivateScope: Boolean): List<ExtensionPermissionEntity>

    @Query("SELECT * FROM swift_extension_permissions WHERE extension_id = :extensionId AND permission = :permission AND is_private_scope = :isPrivateScope LIMIT 1")
    suspend fun getPermission(extensionId: String, permission: String, isPrivateScope: Boolean): ExtensionPermissionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(permission: ExtensionPermissionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(permissions: List<ExtensionPermissionEntity>)

    @Query("DELETE FROM swift_extension_permissions WHERE extension_id = :extensionId AND permission = :permission AND is_private_scope = :isPrivateScope")
    suspend fun deletePermission(extensionId: String, permission: String, isPrivateScope: Boolean)

    @Query("DELETE FROM swift_extension_permissions WHERE extension_id = :extensionId")
    suspend fun deleteAllForExtension(extensionId: String)

    @Query("DELETE FROM swift_extension_permissions WHERE is_private_scope = 1")
    suspend fun clearAllPrivateScope()

    @Query("DELETE FROM swift_extension_permissions")
    suspend fun clearAll()
}
