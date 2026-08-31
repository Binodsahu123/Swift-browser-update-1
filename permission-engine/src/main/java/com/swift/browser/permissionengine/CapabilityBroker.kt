package com.swift.browser.permissionengine

import android.content.Context

data class CapabilityEvaluation(
    val capabilityId: String,
    val displayName: String,
    val webApiSource: String,
    val resourceName: String,
    val capabilityState: CapabilityState,
    val androidPermissionsRequired: List<String> = emptyList(),
    val androidPermissionsGranted: List<String> = emptyList(),
    val isHardwareAvailable: Boolean = true,
    val isSecuritySatisfied: Boolean = true,
    val isSiteAllowed: Boolean = false,
    val isSiteBlocked: Boolean = false,
    val requiresUserPrompt: Boolean = false,
    val reason: String = ""
)

object CapabilityBroker {

    fun evaluateCapability(
        resourceOrType: String,
        dynamicOrigin: DynamicOrigin,
        androidContext: Context? = null,
        cachedDecision: String? = null
    ): CapabilityEvaluation {
        val descriptor = PermissionDescriptorRegistry.getDescriptorForResource(resourceOrType)
            ?: PermissionDescriptorRegistry.getDescriptor(resourceOrType)

        val capId = descriptor?.capabilityId ?: resourceOrType.uppercase()
        val displayName = descriptor?.displayName ?: PermissionDescriptorRegistry.getDisplayNames(capId, listOf(resourceOrType)).firstOrNull() ?: capId
        val apiSource = descriptor?.webApiSource ?: "WebView Callback"

        if (descriptor == null) {
            return CapabilityEvaluation(
                capabilityId = capId,
                displayName = displayName,
                webApiSource = apiSource,
                resourceName = resourceOrType,
                capabilityState = CapabilityState.UNSUPPORTED_BY_WEBVIEW,
                reason = "Unknown capability descriptor for $resourceOrType"
            )
        }

        // 1. Check native bridge requirement / unsupported status
        if (descriptor.supportStatus == CapabilitySupportStatus.UNSUPPORTED_BY_WEBVIEW) {
            return CapabilityEvaluation(
                capabilityId = capId,
                displayName = displayName,
                webApiSource = apiSource,
                resourceName = resourceOrType,
                capabilityState = CapabilityState.UNSUPPORTED_BY_WEBVIEW,
                reason = "$displayName is not supported by standard Android WebView"
            )
        }
        if (descriptor.supportStatus == CapabilitySupportStatus.UNSUPPORTED_BY_ANDROID) {
            return CapabilityEvaluation(
                capabilityId = capId,
                displayName = displayName,
                webApiSource = apiSource,
                resourceName = resourceOrType,
                capabilityState = CapabilityState.UNSUPPORTED_BY_ANDROID,
                reason = "$displayName is not supported by Android platform"
            )
        }

        // 2. Security / Context Check
        val isSecure = PermissionPolicyResolver.isSecureOrigin(dynamicOrigin.canonicalOrigin)
        if (descriptor.requiresSecureOrigin && !isSecure) {
            return CapabilityEvaluation(
                capabilityId = capId,
                displayName = displayName,
                webApiSource = apiSource,
                resourceName = resourceOrType,
                capabilityState = CapabilityState.BLOCKED_BY_SECURITY,
                isSecuritySatisfied = false,
                reason = "$displayName requires HTTPS / secure origin ($dynamicOrigin.canonicalOrigin)"
            )
        }
        if (descriptor.requiresTopLevelFrame && (
            (dynamicOrigin.frameOrigin != null && dynamicOrigin.frameOrigin != dynamicOrigin.topLevelOrigin) ||
            (dynamicOrigin.canonicalOrigin != dynamicOrigin.topLevelOrigin)
        )) {
            return CapabilityEvaluation(
                capabilityId = capId,
                displayName = displayName,
                webApiSource = apiSource,
                resourceName = resourceOrType,
                capabilityState = CapabilityState.BLOCKED_BY_SECURITY,
                isSecuritySatisfied = false,
                reason = "$displayName blocked in iframe element"
            )
        }
        if (descriptor.requiresUserGesture && dynamicOrigin.isUserGesture == false) {
            return CapabilityEvaluation(
                capabilityId = capId,
                displayName = displayName,
                webApiSource = apiSource,
                resourceName = resourceOrType,
                capabilityState = CapabilityState.BLOCKED_BY_SECURITY,
                isSecuritySatisfied = false,
                reason = "$displayName requires an explicit user gesture"
            )
        }

        // 3. Hardware check
        val isHwOk = if (androidContext != null) {
            HardwareValidationEngine.validateHardwareForDescriptor(androidContext, descriptor)
        } else true
        if (!isHwOk) {
            return CapabilityEvaluation(
                capabilityId = capId,
                displayName = displayName,
                webApiSource = apiSource,
                resourceName = resourceOrType,
                capabilityState = CapabilityState.DENIED_BY_HARDWARE,
                isHardwareAvailable = false,
                reason = "Hardware feature required for $displayName is unavailable"
            )
        }

        // 4. Site policy check
        val siteDecision = cachedDecision ?: PermissionCache.getCachedDecision(dynamicOrigin.canonicalOrigin, capId, dynamicOrigin.isIncognito) ?: "ASK"
        if (siteDecision == "BLOCK") {
            return CapabilityEvaluation(
                capabilityId = capId,
                displayName = displayName,
                webApiSource = apiSource,
                resourceName = resourceOrType,
                capabilityState = CapabilityState.BLOCKED_BY_USER_POLICY,
                isSiteBlocked = true,
                reason = "Website decision is BLOCK for $dynamicOrigin.canonicalOrigin + $capId"
            )
        }

        // 5. Android Runtime Permission Check
        val reqAndroidPerms = descriptor.androidPermissions
        val grantedAndroidPerms = if (androidContext != null) {
            reqAndroidPerms.filter { perm ->
                AndroidRuntimePermissionManager.hasPermission(androidContext, perm)
            }
        } else {
            emptyList()
        }
        val missingAndroidPerms = reqAndroidPerms.filter { !grantedAndroidPerms.contains(it) }

        if (siteDecision == "ALLOW_ALWAYS" || siteDecision == "ALLOW_ONCE") {
            if (missingAndroidPerms.isNotEmpty()) {
                return CapabilityEvaluation(
                    capabilityId = capId,
                    displayName = displayName,
                    webApiSource = apiSource,
                    resourceName = resourceOrType,
                    capabilityState = CapabilityState.DENIED_BY_ANDROID_PERMISSION,
                    androidPermissionsRequired = reqAndroidPerms,
                    androidPermissionsGranted = grantedAndroidPerms,
                    isSiteAllowed = true,
                    reason = "Android runtime permissions required: $missingAndroidPerms"
                )
            }
            val state = if (descriptor.requiresNativeBridge) CapabilityState.REQUIRES_NATIVE_BRIDGE else CapabilityState.SUPPORTED_WITH_PERMISSION
            return CapabilityEvaluation(
                capabilityId = capId,
                displayName = displayName,
                webApiSource = apiSource,
                resourceName = resourceOrType,
                capabilityState = state,
                androidPermissionsRequired = reqAndroidPerms,
                androidPermissionsGranted = grantedAndroidPerms,
                isSiteAllowed = true,
                reason = "Site decision $siteDecision and Android permissions cleared"
            )
        }

        // 6. User Prompt / Policy Evaluation
        val state = when (descriptor.supportStatus) {
            CapabilitySupportStatus.SUPPORTED -> CapabilityState.SUPPORTED
            CapabilitySupportStatus.SUPPORTED_WITH_POLICY -> CapabilityState.SUPPORTED_WITH_POLICY
            CapabilitySupportStatus.REQUIRES_NATIVE_BRIDGE -> CapabilityState.REQUIRES_NATIVE_BRIDGE
            else -> CapabilityState.SUPPORTED_WITH_PERMISSION
        }

        return CapabilityEvaluation(
            capabilityId = capId,
            displayName = displayName,
            webApiSource = apiSource,
            resourceName = resourceOrType,
            capabilityState = state,
            androidPermissionsRequired = reqAndroidPerms,
            androidPermissionsGranted = grantedAndroidPerms,
            requiresUserPrompt = descriptor.requiresUserPrompt && (state == CapabilityState.SUPPORTED_WITH_PERMISSION || state == CapabilityState.REQUIRES_NATIVE_BRIDGE),
            reason = "Capability state $state for $capId"
        )
    }
}
