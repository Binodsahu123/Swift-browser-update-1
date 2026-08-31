package com.swift.browser.extensionengine

/**
 * Production-Grade Typed Extension Error Hierarchy.
 * Replaces generic exceptions with structured, typed errors for Manifest, Installer, Identity, and Registry failures.
 */
sealed class ExtensionError(
    override val message: String,
    val errorCode: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    // --- Manifest Errors ---
    sealed class ManifestError(
        message: String,
        errorCode: String,
        cause: Throwable? = null
    ) : ExtensionError(message, errorCode, cause) {

        class InvalidJson(val rawJson: String, cause: Throwable) :
            ManifestError("Manifest JSON is malformed or invalid: ${cause.message}", "MANIFEST_INVALID_JSON", cause)

        class MissingRequiredField(val fieldName: String) :
            ManifestError("Manifest is missing required field '$fieldName'", "MANIFEST_MISSING_REQUIRED_FIELD")

        class UnsupportedVersion(val version: Int) :
            ManifestError("Unsupported manifest_version: $version. Only MV2 (2) and MV3 (3) are supported.", "MANIFEST_UNSUPPORTED_VERSION")

        class InvalidFieldType(val fieldName: String, val expectedType: String, val actualValue: String) :
            ManifestError("Manifest field '$fieldName' must be $expectedType, got: '$actualValue'", "MANIFEST_INVALID_FIELD_TYPE")

        class InvalidVersionFormat(val versionStr: String) :
            ManifestError("Manifest version string '$versionStr' does not follow valid format (1 to 4 dot-separated integers)", "MANIFEST_INVALID_VERSION_FORMAT")

        class InvalidContentScriptSpec(val reason: String) :
            ManifestError("Invalid content_scripts declaration: $reason", "MANIFEST_INVALID_CONTENT_SCRIPT")

        class SecurityPolicyViolation(val reason: String) :
            ManifestError("Manifest CSP or security policy violation: $reason", "MANIFEST_SECURITY_POLICY_VIOLATION")

        class LocaleError(val locale: String, val reason: String) :
            ManifestError("Failed resolving locale '$locale': $reason", "MANIFEST_LOCALE_ERROR")
    }

    // --- Installer Errors ---
    sealed class InstallerError(
        message: String,
        errorCode: String,
        cause: Throwable? = null
    ) : ExtensionError(message, errorCode, cause) {

        class InvalidArchiveFormat(val detail: String) :
            InstallerError("Invalid extension package format: $detail", "INSTALLER_INVALID_ARCHIVE")

        class CrxHeaderCorrupt(val detail: String) :
            InstallerError("CRX header is corrupt or unrecognized: $detail", "INSTALLER_CRX_HEADER_CORRUPT")

        class SignatureInvalid(val detail: String) :
            InstallerError("CRX signature verification failed: $detail", "INSTALLER_SIGNATURE_INVALID")

        class InstallationRejected(val detail: String) :
            InstallerError("INSTALLATION_REJECTED: $detail", "INSTALLATION_REJECTED")

        class PathTraversalDetected(val path: String) :
            InstallerError("Security risk: Path traversal attempt detected in entry '$path'", "INSTALLER_PATH_TRAVERSAL")

        class FileSystemError(val path: String, cause: Throwable) :
            InstallerError("File system operation failed at '$path': ${cause.message}", "INSTALLER_FILE_SYSTEM_ERROR", cause)

        class StagingFailed(val detail: String) :
            InstallerError("Extension staging pipeline failed: $detail", "INSTALLER_STAGING_FAILED")

        class QuotaExceeded(val sizeBytes: Long, val maxBytes: Long) :
            InstallerError("Extension package size ($sizeBytes bytes) exceeds quota ($maxBytes bytes)", "INSTALLER_QUOTA_EXCEEDED")
    }

    // --- Identity Errors ---
    sealed class IdentityError(
        message: String,
        errorCode: String,
        cause: Throwable? = null
    ) : ExtensionError(message, errorCode, cause) {

        class InvalidKey(val keyStr: String, cause: Throwable? = null) :
            IdentityError("Public key in manifest or CRX header is invalid: ${cause?.message ?: keyStr}", "IDENTITY_INVALID_KEY", cause)

        class IdentityMismatch(val expectedId: String, val actualId: String) :
            IdentityError("Derived extension identity '$actualId' does not match expected identity '$expectedId'", "IDENTITY_MISMATCH")

        class UnsignedDisallowed(val detail: String) :
            IdentityError("Unsigned local extension installation is disallowed by policy: $detail", "IDENTITY_UNSIGNED_DISALLOWED")
    }

    // --- Registry Errors ---
    sealed class RegistryError(
        message: String,
        errorCode: String,
        cause: Throwable? = null
    ) : ExtensionError(message, errorCode, cause) {

        class ExtensionAlreadyExists(val extensionId: String) :
            RegistryError("Extension with ID '$extensionId' is already registered", "REGISTRY_DUPLICATE_ID")

        class ExtensionNotFound(val extensionId: String) :
            RegistryError("Extension with ID '$extensionId' was not found in registry", "REGISTRY_NOT_FOUND")

        class InvalidStateTransition(
            val extensionId: String,
            val currentState: String,
            val targetState: String
        ) : RegistryError(
            "Invalid extension state transition for '$extensionId': $currentState -> $targetState",
            "REGISTRY_INVALID_STATE_TRANSITION"
        )
    }

    // --- Security & Permission Errors ---
    sealed class SecurityError(
        message: String,
        errorCode: String,
        cause: Throwable? = null
    ) : ExtensionError(message, errorCode, cause) {

        class AccessDenied(val extensionId: String, val permission: String) :
            SecurityError("Extension '$extensionId' was denied permission '$permission'", "SECURITY_ACCESS_DENIED")

        class IsolatedContextViolation(val detail: String) :
            SecurityError("Isolated world execution boundary violation: $detail", "SECURITY_ISOLATION_VIOLATION")

        class InvalidExtensionOrigin(val originStr: String, val detail: String) :
            SecurityError("Invalid extension origin or URL '$originStr': $detail", "SECURITY_INVALID_ORIGIN")

        class ExtensionNotFound(val extensionId: String) :
            SecurityError("Extension '$extensionId' not found or not active in registry", "SECURITY_EXTENSION_NOT_FOUND")

        class ResourceNotAccessible(val extensionId: String, val path: String, val reason: String) :
            SecurityError("Resource '$path' in extension '$extensionId' is not accessible: $reason", "SECURITY_RESOURCE_NOT_ACCESSIBLE")

        class SandboxPolicyViolation(val extensionId: String, val path: String, val detail: String) :
            SecurityError("Sandbox policy violation in extension '$extensionId' for '$path': $detail", "SECURITY_SANDBOX_VIOLATION")

        class CrossExtensionAccessDenied(val callerId: String, val targetId: String) :
            SecurityError("Cross-extension access denied from '$callerId' to '$targetId'", "SECURITY_CROSS_EXTENSION_DENIED")

        class PathTraversalDetected(val path: String) :
            SecurityError("Path traversal attempt blocked for path '$path'", "SECURITY_PATH_TRAVERSAL")

        class BridgeAccessDenied(val extensionId: String, val detail: String) :
            SecurityError("Privileged bridge access denied for extension '$extensionId': $detail", "SECURITY_BRIDGE_ACCESS_DENIED")

        class InsecureProtocolBlocked(val url: String) :
            SecurityError("Insecure protocol load blocked for URL '$url'", "SECURITY_INSECURE_PROTOCOL")
    }
}
