package com.swift.browser.extensionengine.security

import com.swift.browser.extensionengine.ExtensionError
import com.swift.browser.extensionengine.ExtensionRegistry
import com.swift.browser.extensionengine.PermissionManager
import com.swift.browser.extensionengine.origin.ExtensionOriginValidator

enum class BridgeAccessDecision {
    ALLOW,
    DENY_UNREGISTERED_EXTENSION,
    DENY_DISABLED_EXTENSION,
    DENY_SANDBOX_CONTEXT,
    DENY_WEB_CONTEXT,
    DENY_FILE_SCHEME,
    DENY_CROSS_EXTENSION,
    DENY_PRIVATE_MODE_RESTRICTED,
    DENY_INVALID_ORIGIN;

    val isAllowed: Boolean get() = this == ALLOW
}

/**
 * Policy engine governing access to privileged extension bridge APIs.
 */
object ExtensionBridgePolicy {

    fun evaluate(
        context: ExtensionBridgeSecurityContext,
        currentUrl: String?,
        registry: ExtensionRegistry?,
        permissionManager: PermissionManager?
    ): BridgeAccessDecision {
        if (!context.enabled) return BridgeAccessDecision.DENY_DISABLED_EXTENSION

        // 1. Sandbox page check: Sandbox pages are strictly denied privileged bridge access
        if (context.isSandbox || context.contextType == ExtensionPageType.SANDBOX_PAGE) {
            return BridgeAccessDecision.DENY_SANDBOX_CONTEXT
        }

        // 2. Web context check: Plain web pages must not invoke privileged bridge
        if (context.contextType == ExtensionPageType.WEB_PAGE) {
            return BridgeAccessDecision.DENY_WEB_CONTEXT
        }

        // 3. File URL check: file:// or content:// origins are denied privileged access
        val url = currentUrl?.trim()?.lowercase() ?: ""
        if (url.startsWith("file://") || url.startsWith("content://")) {
            return BridgeAccessDecision.DENY_FILE_SCHEME
        }

        // 4. Validate extension in registry
        val cleanId = context.extensionId.lowercase().trim()
        if (registry != null) {
            val ext = registry.getExtension(cleanId)
                ?: return BridgeAccessDecision.DENY_UNREGISTERED_EXTENSION

            if (!registry.isExtensionEnabled(cleanId)) {
                return BridgeAccessDecision.DENY_DISABLED_EXTENSION
            }

            // 5. Check private mode permissions
            if (context.isPrivate) {
                val allowedInPrivate = ext.allowedInPrivate ||
                        (permissionManager?.isAllowedInPrivate(cleanId) == true)
                if (!allowedInPrivate) {
                    return BridgeAccessDecision.DENY_PRIVATE_MODE_RESTRICTED
                }
            }
        }

        // 6. Validate origin consistency if currentUrl is available and context is extension page
        if (url.isNotBlank() && context.contextType.isPrivilegedContext) {
            val validationResult = ExtensionOriginValidator.validate(
                extensionId = cleanId,
                urlStr = url,
                expectedContext = context.contextType,
                isPrivate = context.isPrivate,
                registry = registry,
                permissionManager = permissionManager
            )
            if (!validationResult.isValid) {
                return when (validationResult) {
                    ExtensionOriginValidator.ValidationResult.SANDBOX_PAGE_ISOLATED -> BridgeAccessDecision.DENY_SANDBOX_CONTEXT
                    ExtensionOriginValidator.ValidationResult.FILE_URL_DISALLOWED -> BridgeAccessDecision.DENY_FILE_SCHEME
                    ExtensionOriginValidator.ValidationResult.EXTENSION_DISABLED -> BridgeAccessDecision.DENY_DISABLED_EXTENSION
                    ExtensionOriginValidator.ValidationResult.PRIVATE_MODE_BLOCKED -> BridgeAccessDecision.DENY_PRIVATE_MODE_RESTRICTED
                    ExtensionOriginValidator.ValidationResult.ID_MISMATCH -> BridgeAccessDecision.DENY_CROSS_EXTENSION
                    ExtensionOriginValidator.ValidationResult.CROSS_EXTENSION_DENIED -> BridgeAccessDecision.DENY_CROSS_EXTENSION
                    else -> BridgeAccessDecision.DENY_INVALID_ORIGIN
                }
            }
        }

        return BridgeAccessDecision.ALLOW
    }

    fun verifyBridgeAccessOrThrow(
        context: ExtensionBridgeSecurityContext,
        currentUrl: String?,
        registry: ExtensionRegistry?,
        permissionManager: PermissionManager?
    ) {
        val decision = evaluate(context, currentUrl, registry, permissionManager)
        if (!decision.isAllowed) {
            throw ExtensionError.SecurityError.BridgeAccessDenied(
                context.extensionId,
                "Privileged extension bridge access denied: ${decision.name}"
            )
        }
    }
}
