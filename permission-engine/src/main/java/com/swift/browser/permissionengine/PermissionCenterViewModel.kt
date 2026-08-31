package com.swift.browser.permissionengine

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.*
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PermissionCenterViewModel(private val context: Context) {
    private val engine = PermissionEngineProvider.get(context)

    private val _uiState = MutableStateFlow(PermissionCenterUiState(loading = true))
    val uiState = _uiState.asStateFlow()

    private val _siteStates = MutableStateFlow<Map<String, SitePermissionUiState>>(emptyMap())

    init {
        CoroutineScope(Dispatchers.IO).launch {
            PermissionEngineApi.observeAllPermissions(context).collectLatest { entities ->
                val grouped = entities.groupBy { it.origin }
                val newSites = grouped.map { (origin, perms) ->
                    val uiPerms = perms.map { mapEntityToUi(it) }
                    SitePermissionUiState(
                        origin = origin,
                        displayName = origin,
                        permissions = uiPerms,
                        secure = origin.startsWith("https")
                    )
                }
                
                _siteStates.value = newSites.associateBy { it.origin }
                _uiState.value = PermissionCenterUiState(sites = newSites, loading = false)
            }
        }
    }

    fun observeSitePermissions(origin: String): StateFlow<SitePermissionUiState?> {
        val canonical = OriginNormalizer.normalize(origin)
        return _siteStates.map { 
            val existing = it[canonical] ?: it[engine.getCanonicalHost(origin)]
            if (existing != null) {
                // Ensure all default permissions exist, merge them
                val existingTypes = existing.permissions.map { p -> p.permissionType }
                val defaults = createDefaultSiteState(canonical).permissions.filter { p -> !existingTypes.contains(p.permissionType) }
                existing.copy(permissions = (existing.permissions + defaults).sortedBy { p -> p.displayName })
            } else {
                createDefaultSiteState(canonical)
            }
        }.stateIn(CoroutineScope(Dispatchers.IO), SharingStarted.Eagerly, createDefaultSiteState(canonical))
    }

    private fun createDefaultSiteState(origin: String): SitePermissionUiState {
        val descriptors = PermissionDescriptorRegistry.getAllDescriptors().filter { descriptor ->
            descriptor.capabilityId != "CAMERA_MICROPHONE"
        }

        return SitePermissionUiState(
            origin = origin,
            displayName = origin,
            permissions = descriptors.map { descriptor ->
                val supportLbl = when {
                    descriptor.requestHandlingMode == RequestHandlingMode.UNSUPPORTED -> "UNSUPPORTED"
                    descriptor.supportStatus == CapabilitySupportStatus.UNSUPPORTED_BY_WEBVIEW -> "UNSUPPORTED"
                    descriptor.supportStatus == CapabilitySupportStatus.UNSUPPORTED_BY_ANDROID -> "NOT AVAILABLE"
                    !HardwareValidationEngine.validateHardwareForDescriptor(context, descriptor) -> "NOT AVAILABLE"
                    else -> null
                }
                PermissionItemUiState(
                    permissionType = descriptor.capabilityId,
                    displayName = descriptor.displayName,
                    icon = PermissionIconResolver.getIcon(descriptor.iconKey),
                    decision = "Ask",
                    androidState = checkAndroidPermission(descriptor.capabilityId),
                    supportLabel = supportLbl,
                    riskLevel = descriptor.riskLevel,
                    canChange = supportLbl == null
                )
            }.sortedBy { it.displayName },
            secure = origin.startsWith("https")
        )
    }

    private fun mapEntityToUi(entity: PermissionEntity): PermissionItemUiState {
        val descriptor = PermissionDescriptorRegistry.getDescriptor(entity.permissionType)
        val decisionStr = when (entity.decision) {
            "ALLOW_ALWAYS" -> "Allow"
            "ALLOW_ONCE" -> "Session-only"
            "BLOCK" -> "Block"
            else -> "Ask"
        }
        val supportLbl = when {
            descriptor?.requestHandlingMode == RequestHandlingMode.UNSUPPORTED -> "UNSUPPORTED"
            descriptor?.supportStatus == CapabilitySupportStatus.UNSUPPORTED_BY_WEBVIEW -> "UNSUPPORTED"
            descriptor?.supportStatus == CapabilitySupportStatus.UNSUPPORTED_BY_ANDROID -> "NOT AVAILABLE"
            descriptor != null && !HardwareValidationEngine.validateHardwareForDescriptor(context, descriptor) -> "NOT AVAILABLE"
            else -> null
        }
        return PermissionItemUiState(
            permissionType = entity.permissionType,
            displayName = descriptor?.displayName ?: mapTypeToName(entity.permissionType),
            icon = PermissionIconResolver.getIcon(descriptor?.iconKey),
            decision = decisionStr,
            isTemporary = entity.decision == "ALLOW_ONCE",
            expiresAt = entity.expiresAt,
            androidState = checkAndroidPermission(entity.permissionType),
            supportLabel = supportLbl,
            riskLevel = descriptor?.riskLevel ?: "Medium",
            canChange = supportLbl == null
        )
    }

    private fun checkAndroidPermission(type: String): String? {
        val androidPerms = PermissionDescriptorRegistry.getAndroidPermissionsForType(type)
        if (androidPerms.isEmpty()) return null

        val allGranted = androidPerms.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        return if (allGranted) "GRANTED" else "DENIED"
    }

    private fun mapTypeToName(type: String): String {
        return PermissionDescriptorRegistry.getDescriptor(type)?.displayName
            ?: PermissionDescriptorRegistry.getDisplayNames(type).firstOrNull()
            ?: type
    }

    private fun mapTypeToIcon(type: String): androidx.compose.ui.graphics.vector.ImageVector {
        val descriptor = PermissionDescriptorRegistry.getDescriptor(type)
        return PermissionIconResolver.getIcon(descriptor?.iconKey)
    }

    fun setDecision(origin: String, permissionType: String, decision: String) {
        val mappedDecision = when(decision) {
            "Allow" -> "ALLOW_ALWAYS"
            "Block" -> "BLOCK"
            "Session-only" -> "ALLOW_ONCE"
            else -> "ASK"
        }
        PermissionEngineApi.setDecision(context, origin, permissionType, mappedDecision)
    }

    fun revoke(origin: String, permissionType: String) {
        PermissionEngineApi.revokePermission(context, origin, permissionType)
    }

    fun resetOrigin(origin: String) {
        PermissionEngineApi.resetOrigin(context, origin)
    }

    fun resetAll() {
        PermissionEngineApi.resetAll(context)
    }
}
