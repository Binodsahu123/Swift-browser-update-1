package com.swift.browser.permissionengine

import kotlinx.coroutines.flow.Flow

class SitePermissionRepository(private val permissionDao: PermissionDao) {
    val allPermissionsFlow: Flow<List<PermissionEntity>> = permissionDao.getAllPermissionsFlow()

    suspend fun getAllPermissions(): List<PermissionEntity> = permissionDao.getAllPermissions()

    suspend fun getPermissionsByOrigin(origin: String): List<PermissionEntity> {
        val norm = OriginNormalizer.normalize(origin)
        return permissionDao.getPermissionsByOrigin(norm)
    }

    suspend fun getPermission(origin: String, permissionType: String): PermissionEntity? {
        val norm = OriginNormalizer.normalize(origin)
        return permissionDao.getPermission(norm, permissionType)
    }

    suspend fun savePermission(
        origin: String,
        permissionType: String,
        decision: String,
        isTemporary: Boolean = false,
        expiryMs: Long = 0L
    ) {
        val norm = OriginNormalizer.normalize(origin)
        val existing = permissionDao.getPermission(norm, permissionType)
        val entity = PermissionEntity(
            id = existing?.id ?: 0,
            origin = norm,
            permissionType = permissionType,
            decision = decision,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            expiresAt = expiryMs,
            isTemporary = isTemporary
        )
        permissionDao.insertPermission(entity)
    }

    suspend fun deletePermission(origin: String, permissionType: String) {
        val norm = OriginNormalizer.normalize(origin)
        permissionDao.deletePermission(norm, permissionType)
    }

    suspend fun deletePermissionsForOrigin(origin: String) {
        val norm = OriginNormalizer.normalize(origin)
        permissionDao.deletePermissionsForOrigin(norm)
    }

    suspend fun clearAll() = permissionDao.clearAll()
}

