package com.swift.browser.extensionengine.origin

import com.swift.browser.extensionengine.ExtensionRegistry
import com.swift.browser.extensionengine.PathSanitizer
import com.swift.browser.extensionengine.PermissionManager
import com.swift.browser.extensionengine.security.ExtensionPageType

/**
 * Production-Grade Origin Validator for Browser Extensions.
 * Validates request origins, extension caller identities, cross-extension calls, and execution contexts.
 */
object ExtensionOriginValidator {

    enum class ValidationResult {
        VALID,
        INVALID_SCHEME,
        INVALID_ID,
        ID_MISMATCH,
        UNKNOWN_EXTENSION,
        EXTENSION_DISABLED,
        PRIVATE_MODE_BLOCKED,
        CROSS_EXTENSION_DENIED,
        FILE_URL_DISALLOWED,
        SANDBOX_PAGE_ISOLATED,
        PATH_TRAVERSAL;

        val isValid: Boolean get() = this == VALID
    }

    fun validate(
        extensionId: String,
        urlStr: String,
        expectedContext: ExtensionPageType = ExtensionPageType.EXTENSION_PAGE,
        isPrivate: Boolean = false,
        registry: ExtensionRegistry? = null,
        permissionManager: PermissionManager? = null
    ): ValidationResult {
        val cleanId = extensionId.lowercase().trim()
        if (cleanId.isBlank() || !PathSanitizer.isSafeExtensionId(cleanId)) {
            return ValidationResult.INVALID_ID
        }

        // 1. Disallow file:// or content:// scheme loads for extension privileged contexts
        val lowerUrl = urlStr.trim().lowercase()
        if (lowerUrl.startsWith("file://") || lowerUrl.startsWith("content://")) {
            return ValidationResult.FILE_URL_DISALLOWED
        }

        // 2. Parse URL and verify origin
        val urlResult = ExtensionUrl.parseExtensionUrl(urlStr)
            ?: return ValidationResult.INVALID_SCHEME

        if (!urlResult.extensionId.equals(cleanId, ignoreCase = true)) {
            return ValidationResult.ID_MISMATCH
        }

        // 3. Check for path traversal signs
        if (!PathSanitizer.isSafeRelativePath(urlResult.resourcePath)) {
            return ValidationResult.PATH_TRAVERSAL
        }

        // 4. Registry check if provided
        if (registry != null) {
            val ext = registry.getExtension(cleanId)
                ?: return ValidationResult.UNKNOWN_EXTENSION

            if (!registry.isExtensionEnabled(cleanId)) {
                return ValidationResult.EXTENSION_DISABLED
            }

            // 5. Check sandbox isolation
            if (expectedContext == ExtensionPageType.SANDBOX_PAGE) {
                // Sandbox context must not execute privileged operations
                return ValidationResult.SANDBOX_PAGE_ISOLATED
            }

            // 6. Private mode policy check
            if (isPrivate) {
                val allowedInPrivate = ext.allowedInPrivate ||
                        (permissionManager?.isAllowedInPrivate(cleanId) == true)
                if (!allowedInPrivate) {
                    return ValidationResult.PRIVATE_MODE_BLOCKED
                }
            }
        }

        return ValidationResult.VALID
    }

    fun validateSender(senderOriginStr: String?, expectedExtensionId: String): Boolean {
        if (senderOriginStr.isNullOrBlank()) return false
        val cleanExpected = expectedExtensionId.lowercase().trim()
        val senderOrigin = ExtensionOrigin.fromUrl(senderOriginStr) ?: return false
        return senderOrigin.host.equals(cleanExpected, ignoreCase = true)
    }

    fun isSameExtensionOrigin(urlA: String?, urlB: String?): Boolean {
        if (urlA.isNullOrBlank() || urlB.isNullOrBlank()) return false
        val idA = ExtensionUrl.getExtensionId(urlA) ?: return false
        val idB = ExtensionUrl.getExtensionId(urlB) ?: return false
        return idA.equals(idB, ignoreCase = true)
    }

    fun isExternalOrigin(urlStr: String?): Boolean {
        if (urlStr.isNullOrBlank()) return true
        val lower = urlStr.trim().lowercase()
        return !lower.startsWith("${ExtensionOrigin.SCHEME_CHROME_EXTENSION}://") &&
                !lower.startsWith("${ExtensionOrigin.SCHEME_SWIFT_EXTENSION}://")
    }
}
