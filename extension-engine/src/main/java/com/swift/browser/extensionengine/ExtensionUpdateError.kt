package com.swift.browser.extensionengine

/**
 * Typed error hierarchy for extension package verification and update operations.
 */
sealed class ExtensionUpdateError(
    override val message: String,
    val errorCode: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    class NetworkFailed(val url: String, val detail: String, cause: Throwable? = null) :
        ExtensionUpdateError("Network request to '$url' failed: $detail", "NETWORK_FAILED", cause)

    class UpdateManifestInvalid(val reason: String, cause: Throwable? = null) :
        ExtensionUpdateError("Update manifest is invalid or malformed: $reason", "UPDATE_MANIFEST_INVALID", cause)

    class NoUpdateAvailable(val extensionId: String, val version: String) :
        ExtensionUpdateError("No update available for extension '$extensionId' (current version: $version)", "NO_UPDATE")

    class UpdateNotNewer(val candidateVersion: String, val currentVersion: String) :
        ExtensionUpdateError("Candidate version '$candidateVersion' is not newer than current version '$currentVersion'", "UPDATE_NOT_NEWER")

    class CrxHeaderInvalid(val detail: String) :
        ExtensionUpdateError("CRX header is invalid or corrupt: $detail", "CRX_HEADER_INVALID")

    class Crx3ParseFailed(val detail: String, cause: Throwable? = null) :
        ExtensionUpdateError("Failed to parse CRX3 signed-header protobuf: $detail", "CRX3_PARSE_FAILED", cause)

    class CrxSignatureInvalid(val detail: String, cause: Throwable? = null) :
        ExtensionUpdateError("CRX3 signature verification failed: $detail", "CRX_SIGNATURE_INVALID", cause)

    class CrxIdMismatch(val expectedId: String, val actualId: String) :
        ExtensionUpdateError("CRX signed header crx_id '$actualId' does not match derived identity '$expectedId'", "CRX_ID_MISMATCH")

    class PublicKeyInvalid(val detail: String, cause: Throwable? = null) :
        ExtensionUpdateError("Package public key is invalid or unsupported: $detail", "PUBLIC_KEY_INVALID", cause)

    class ManifestInvalid(val detail: String, cause: Throwable? = null) :
        ExtensionUpdateError("Extension manifest in package is invalid: $detail", "MANIFEST_INVALID", cause)

    class ResourceInvalid(val resourcePath: String, val detail: String) :
        ExtensionUpdateError("Required package resource '$resourcePath' is invalid or missing: $detail", "RESOURCE_INVALID")

    class IdentityMismatch(val expectedId: String, val actualId: String) :
        ExtensionUpdateError("Update package identity '$actualId' does not match installed extension identity '$expectedId'", "IDENTITY_MISMATCH")

    class KeyRotationRejected(val extensionId: String, val detail: String = "Signing public key does not match installed trusted key") :
        ExtensionUpdateError("Key rotation rejected for extension '$extensionId': $detail", "KEY_ROTATION_REJECTED")

    class PackageTooLarge(val sizeBytes: Long, val maxBytes: Long) :
        ExtensionUpdateError("Extension package size ($sizeBytes bytes) exceeds maximum limit ($maxBytes bytes)", "PACKAGE_TOO_LARGE")

    class InstallFailed(val detail: String, cause: Throwable? = null) :
        ExtensionUpdateError("Failed to install extension update: $detail", "INSTALL_FAILED", cause)

    class CommitFailed(val detail: String, cause: Throwable? = null) :
        ExtensionUpdateError("Atomic commit failed during extension update: $detail", "COMMIT_FAILED", cause)

    class RollbackFailed(val detail: String, cause: Throwable? = null) :
        ExtensionUpdateError("Rollback operation failed: $detail", "ROLLBACK_FAILED", cause)
}
