package com.swift.browser.extensionengine

object ExtensionMessagingValidator {

    sealed class ValidationResult {
        object Allowed : ValidationResult()
        data class Denied(val reason: String) : ValidationResult()
    }

    fun validateSender(
        sender: ExtensionSender,
        registry: ExtensionRegistry,
        permissionManager: PermissionManager
    ): ValidationResult {
        val cleanId = sender.extensionId.lowercase().trim()
        if (cleanId.isBlank()) {
            return ValidationResult.Denied("Blank extension ID")
        }

        // Web pages cannot claim extension privileges or impersonate an extension sender
        if (sender.contextType == ExtensionContextType.WEB_PAGE) {
            val url = sender.url ?: ""
            if (!url.startsWith("chrome-extension://") && !url.startsWith("sw-extension://")) {
                return ValidationResult.Denied("Web page ($url) cannot impersonate extension sender '$cleanId'")
            }
        }

        val ext = registry.getExtension(cleanId)
            ?: return ValidationResult.Denied("Extension '$cleanId' is not registered")

        if (!registry.isExtensionEnabled(cleanId)) {
            return ValidationResult.Denied("Extension '$cleanId' is disabled")
        }

        if (sender.isPrivate && !permissionManager.isAllowedInPrivate(cleanId)) {
            return ValidationResult.Denied("Extension '$cleanId' is not allowed in private mode")
        }

        // Content scripts running on untrusted pages must have valid host permissions
        if (sender.contextType == ExtensionContextType.CONTENT_SCRIPT && !sender.url.isNull_or_blank()) {
            val pageUrl = sender.url
            if (!permissionManager.hasHostPermission(cleanId, ext.hostPermissions, ext.permissions, pageUrl)) {
                return ValidationResult.Denied("Content script lacks host permission for page: $pageUrl")
            }
        }

        return ValidationResult.Allowed
    }

    fun validateCrossExtension(
        sender: ExtensionSender,
        targetExtensionId: String,
        registry: ExtensionRegistry,
        permissionManager: PermissionManager
    ): ValidationResult {
        val senderVal = validateSender(sender, registry, permissionManager)
        if (senderVal is ValidationResult.Denied) return senderVal

        val targetClean = targetExtensionId.lowercase().trim()
        val targetExt = registry.getExtension(targetClean)
            ?: return ValidationResult.Denied("Target extension '$targetClean' not found")

        if (!registry.isExtensionEnabled(targetClean)) {
            return ValidationResult.Denied("Target extension '$targetClean' is disabled")
        }

        if (sender.isPrivate && !permissionManager.isAllowedInPrivate(targetClean)) {
            return ValidationResult.Denied("Target extension '$targetClean' is not allowed in private mode")
        }

        // If target extension is different from sender, check if cross-extension messaging is permitted
        if (!targetClean.equals(sender.extensionId, ignoreCase = true)) {
            // Unregistered or untrusted senders cannot cross-communicate
            if (sender.contextType == ExtensionContextType.WEB_PAGE) {
                return ValidationResult.Denied("Untrusted web page cannot send cross-extension messages to '$targetClean'")
            }
        }

        return ValidationResult.Allowed
    }
}

private fun String?.isNull_or_blank(): Boolean = this == null || this.isBlank()
