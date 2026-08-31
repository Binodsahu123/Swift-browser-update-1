package com.swift.browser.permissionengine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SwiftPermissionPrompt(
    val requestId: String,
    val origin: String,
    val permissionType: String, // "CAMERA", "MICROPHONE", "LOCATION", "NOTIFICATIONS", "STORAGE", "CLIPBOARD", "COOKIES", "DOWNLOADS", "FILE_UPLOAD", "PROTECTED_MEDIA"
    val riskLevel: String, // "Low", "Medium", "High"
    val isSecure: Boolean,
    val displayPermissions: List<String> = emptyList(),
    val onResponse: (decision: String) -> Unit // "ALLOW_ONCE", "ALLOW_ALWAYS", "BLOCK", "CANCEL"
)

data class ExtensionPermissionPrompt(
    val requestId: String,
    val extensionId: String,
    val extensionName: String,
    val permissions: List<String> = emptyList(),
    val origins: List<String> = emptyList(),
    val onResponse: (granted: Boolean) -> Unit
)

object PermissionDialogEngine {
    private val _activePrompt = MutableStateFlow<SwiftPermissionPrompt?>(null)
    val activePrompt: StateFlow<SwiftPermissionPrompt?> = _activePrompt.asStateFlow()

    private val _activeCapabilityPrompt = MutableStateFlow<CapabilityPromptModel?>(null)
    val activeCapabilityPrompt: StateFlow<CapabilityPromptModel?> = _activeCapabilityPrompt.asStateFlow()

    private val _activeExtensionPrompt = MutableStateFlow<ExtensionPermissionPrompt?>(null)
    val activeExtensionPrompt: StateFlow<ExtensionPermissionPrompt?> = _activeExtensionPrompt.asStateFlow()

    fun showPrompt(
        requestId: String,
        origin: String,
        permissionType: String,
        riskLevel: String,
        isSecure: Boolean,
        resources: List<String> = emptyList(),
        onResponse: (String) -> Unit
    ) {
        PermissionDiagnostics.updateTraceStage(
            requestId = requestId,
            stage = "DIALOG_SHOWN",
            status = "PENDING",
            reason = "Permission prompt dialog shown in UI."
        )

        val displayPerms = PermissionDescriptorRegistry.getDisplayNames(permissionType, resources)

        _activePrompt.value = SwiftPermissionPrompt(
            requestId = requestId,
            origin = origin,
            permissionType = permissionType,
            riskLevel = riskLevel,
            isSecure = isSecure,
            displayPermissions = displayPerms,
            onResponse = { decision ->
                _activePrompt.value = null
                onResponse(decision)
            }
        )
    }

    fun showCapabilityPrompt(promptModel: CapabilityPromptModel) {
        PermissionDiagnostics.updateTraceStage(
            requestId = promptModel.requestId,
            stage = "DIALOG_SHOWN",
            status = "PENDING",
            reason = "Multi-capability prompt shown for ${promptModel.capabilities.map { it.capabilityId }}"
        )
        _activeCapabilityPrompt.value = promptModel
    }

    fun showExtensionPrompt(
        requestId: String,
        extensionId: String,
        extensionName: String,
        permissions: List<String> = emptyList(),
        origins: List<String> = emptyList(),
        onResponse: (granted: Boolean) -> Unit
    ) {
        _activeExtensionPrompt.value = ExtensionPermissionPrompt(
            requestId = requestId,
            extensionId = extensionId,
            extensionName = extensionName,
            permissions = permissions,
            origins = origins,
            onResponse = { granted ->
                _activeExtensionPrompt.value = null
                onResponse(granted)
            }
        )
    }

    fun dismissPrompt(requestId: String? = null) {
        if (requestId == null || _activePrompt.value?.requestId == requestId) {
            _activePrompt.value = null
        }
        if (requestId == null || _activeCapabilityPrompt.value?.requestId == requestId) {
            _activeCapabilityPrompt.value = null
        }
        if (requestId == null || _activeExtensionPrompt.value?.requestId == requestId) {
            _activeExtensionPrompt.value = null
        }
    }
}
