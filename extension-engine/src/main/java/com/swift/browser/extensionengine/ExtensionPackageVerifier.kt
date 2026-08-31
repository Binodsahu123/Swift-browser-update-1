package com.swift.browser.extensionengine

import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import org.json.JSONObject

/**
 * Detailed verification report for an extension package.
 */
data class PackageVerificationSummary(
    val verificationState: PackageVerificationState,
    val trustState: ExtensionTrustState,
    val derivedId: String,
    val publicKeyBytes: ByteArray?,
    val rawSignatureBytes: ByteArray?,
    val zipPayloadBytes: ByteArray,
    val manifestContent: String?,
    val extensionVersion: String?,
    val isSignatureVerified: Boolean,
    val verifiedContentsSupported: Boolean,
    val algorithm: String?,
    val error: Throwable? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PackageVerificationSummary
        return verificationState == other.verificationState &&
                trustState == other.trustState &&
                derivedId == other.derivedId &&
                publicKeyBytes.contentEquals(other.publicKeyBytes) &&
                rawSignatureBytes.contentEquals(other.rawSignatureBytes) &&
                manifestContent == other.manifestContent &&
                extensionVersion == other.extensionVersion &&
                isSignatureVerified == other.isSignatureVerified &&
                algorithm == other.algorithm
    }

    override fun hashCode(): Int {
        var result = verificationState.hashCode()
        result = 31 * result + trustState.hashCode()
        result = 31 * result + derivedId.hashCode()
        result = 31 * result + (publicKeyBytes?.contentHashCode() ?: 0)
        result = 31 * result + (rawSignatureBytes?.contentHashCode() ?: 0)
        result = 31 * result + (manifestContent?.hashCode() ?: 0)
        result = 31 * result + (extensionVersion?.hashCode() ?: 0)
        result = 31 * result + isSignatureVerified.hashCode()
        result = 31 * result + (algorithm?.hashCode() ?: 0)
        return result
    }
}

/**
 * Unified verifier for extension packages (CRX3, CRX2, and Unsigned ZIPs).
 * Enforces step-by-step state machine transitions, cryptographic validation,
 * identity continuity, zip-bomb prevention, and manifest integrity.
 */
object ExtensionPackageVerifier {

    private const val MAX_ZIP_ENTRIES = 10000
    private const val MAX_UNCOMPRESSED_SIZE_BYTES = 200 * 1024 * 1024L // 200 MB
    private const val MAX_COMPRESSION_RATIO = 100.0

    /**
     * Fully validates an extension package.
     */
    fun verifyPackage(
        packageBytes: ByteArray,
        sourceName: String = "local_package",
        expectedExtensionId: String? = null,
        expectedPublicKeyBytes: ByteArray? = null
    ): PackageVerificationSummary {
        if (packageBytes.isEmpty()) {
            return PackageVerificationSummary(
                verificationState = PackageVerificationState.REJECTED,
                trustState = ExtensionTrustState.UNTRUSTED_REJECTED,
                derivedId = "",
                publicKeyBytes = null,
                rawSignatureBytes = null,
                zipPayloadBytes = ByteArray(0),
                manifestContent = null,
                extensionVersion = null,
                isSignatureVerified = false,
                verifiedContentsSupported = false,
                algorithm = null,
                error = ExtensionUpdateError.CrxHeaderInvalid("Package bytes are empty (0 bytes)")
            )
        }

        var verificationState = PackageVerificationState.UNVERIFIED
        var trustState = ExtensionTrustState.UNTRUSTED_REJECTED
        var zipBytes: ByteArray = packageBytes
        var publicKeyBytes: ByteArray? = null
        var signatureBytes: ByteArray? = null
        var isSignatureVerified = false
        var verifiedContentsSupported = false
        var algorithm: String? = null
        var derivedId = ""

        // 1. Detect format & verify header
        val isCrx = Crx3Verifier.isCrxPackage(packageBytes)
        if (isCrx) {
            val version = readUint32LE(packageBytes, 4)
            verificationState = PackageVerificationState.HEADER_VALID

            if (version == 3L) {
                // CRX3 Verification
                val crx3Result = Crx3Verifier.verifyCrx3(packageBytes)
                if (!crx3Result.isValid) {
                    return PackageVerificationSummary(
                        verificationState = PackageVerificationState.REJECTED,
                        trustState = ExtensionTrustState.UNTRUSTED_REJECTED,
                        derivedId = crx3Result.extensionId ?: "",
                        publicKeyBytes = crx3Result.publicKeyBytes,
                        rawSignatureBytes = crx3Result.signatureBytes,
                        zipPayloadBytes = crx3Result.zipPayloadBytes,
                        manifestContent = null,
                        extensionVersion = null,
                        isSignatureVerified = false,
                        verifiedContentsSupported = false,
                        algorithm = crx3Result.algorithm,
                        error = ExtensionUpdateError.CrxSignatureInvalid(crx3Result.errorMessage ?: "CRX3 signature verification failed")
                    )
                }

                verificationState = PackageVerificationState.SIGNATURE_VALID
                trustState = ExtensionTrustState.TRUSTED_CRX3
                zipBytes = crx3Result.zipPayloadBytes
                publicKeyBytes = crx3Result.publicKeyBytes
                signatureBytes = crx3Result.signatureBytes
                isSignatureVerified = true
                verifiedContentsSupported = crx3Result.verifiedContentsSupported
                algorithm = crx3Result.algorithm
                derivedId = crx3Result.extensionId ?: ""

            } else if (version == 2L) {
                // CRX2 Legacy Verification
                val crx2Result = verifyCrx2(packageBytes)
                if (!crx2Result.isValid) {
                    return PackageVerificationSummary(
                        verificationState = PackageVerificationState.REJECTED,
                        trustState = ExtensionTrustState.UNTRUSTED_REJECTED,
                        derivedId = crx2Result.extensionId ?: "",
                        publicKeyBytes = crx2Result.publicKeyBytes,
                        rawSignatureBytes = crx2Result.signatureBytes,
                        zipPayloadBytes = crx2Result.zipPayloadBytes,
                        manifestContent = null,
                        extensionVersion = null,
                        isSignatureVerified = false,
                        verifiedContentsSupported = false,
                        algorithm = "RSA (SHA256withRSA)",
                        error = ExtensionUpdateError.CrxSignatureInvalid(crx2Result.errorMessage ?: "CRX2 signature verification failed")
                    )
                }

                verificationState = PackageVerificationState.SIGNATURE_VALID
                trustState = ExtensionTrustState.TRUSTED_CRX2
                zipBytes = crx2Result.zipPayloadBytes
                publicKeyBytes = crx2Result.publicKeyBytes
                signatureBytes = crx2Result.signatureBytes
                isSignatureVerified = true
                algorithm = "RSA (SHA256withRSA)"
                derivedId = crx2Result.extensionId ?: ""

            } else {
                return PackageVerificationSummary(
                    verificationState = PackageVerificationState.REJECTED,
                    trustState = ExtensionTrustState.UNTRUSTED_REJECTED,
                    derivedId = "",
                    publicKeyBytes = null,
                    rawSignatureBytes = null,
                    zipPayloadBytes = ByteArray(0),
                    manifestContent = null,
                    extensionVersion = null,
                    isSignatureVerified = false,
                    verifiedContentsSupported = false,
                    algorithm = null,
                    error = ExtensionUpdateError.CrxHeaderInvalid("Unsupported CRX version: $version")
                )
            }
        } else {
            // Unsigned ZIP package
            if (isZipHeader(packageBytes)) {
                verificationState = PackageVerificationState.HEADER_VALID
                trustState = ExtensionTrustState.UNSIGNED_LOCAL_DEVELOPER
                zipBytes = packageBytes
            } else {
                return PackageVerificationSummary(
                    verificationState = PackageVerificationState.REJECTED,
                    trustState = ExtensionTrustState.UNTRUSTED_REJECTED,
                    derivedId = "",
                    publicKeyBytes = null,
                    rawSignatureBytes = null,
                    zipPayloadBytes = ByteArray(0),
                    manifestContent = null,
                    extensionVersion = null,
                    isSignatureVerified = false,
                    verifiedContentsSupported = false,
                    algorithm = null,
                    error = ExtensionUpdateError.CrxHeaderInvalid("Package is neither a valid CRX nor a valid ZIP archive")
                )
            }
        }

        // 2. Validate ZIP structure & security (Zip Bomb / Path Traversal)
        val zipValidation = validateZipArchive(zipBytes)
        if (!zipValidation.isValid) {
            return PackageVerificationSummary(
                verificationState = PackageVerificationState.REJECTED,
                trustState = ExtensionTrustState.UNTRUSTED_REJECTED,
                derivedId = derivedId,
                publicKeyBytes = publicKeyBytes,
                rawSignatureBytes = signatureBytes,
                zipPayloadBytes = zipBytes,
                manifestContent = null,
                extensionVersion = null,
                isSignatureVerified = isSignatureVerified,
                verifiedContentsSupported = verifiedContentsSupported,
                algorithm = algorithm,
                error = zipValidation.error
            )
        }

        // 3. Extract and parse manifest.json
        val manifestContent = zipValidation.manifestContent
        if (manifestContent == null) {
            return PackageVerificationSummary(
                verificationState = PackageVerificationState.REJECTED,
                trustState = ExtensionTrustState.UNTRUSTED_REJECTED,
                derivedId = derivedId,
                publicKeyBytes = publicKeyBytes,
                rawSignatureBytes = signatureBytes,
                zipPayloadBytes = zipBytes,
                manifestContent = null,
                extensionVersion = null,
                isSignatureVerified = isSignatureVerified,
                verifiedContentsSupported = verifiedContentsSupported,
                algorithm = algorithm,
                error = ExtensionUpdateError.ManifestInvalid("Archive is missing manifest.json")
            )
        }

        verificationState = PackageVerificationState.PARSED

        val manifestJson = try {
            JSONObject(manifestContent)
        } catch (e: Exception) {
            return PackageVerificationSummary(
                verificationState = PackageVerificationState.REJECTED,
                trustState = ExtensionTrustState.UNTRUSTED_REJECTED,
                derivedId = derivedId,
                publicKeyBytes = publicKeyBytes,
                rawSignatureBytes = signatureBytes,
                zipPayloadBytes = zipBytes,
                manifestContent = manifestContent,
                extensionVersion = null,
                isSignatureVerified = isSignatureVerified,
                verifiedContentsSupported = verifiedContentsSupported,
                algorithm = algorithm,
                error = ExtensionUpdateError.ManifestInvalid("manifest.json contains invalid JSON: ${e.message}", e)
            )
        }

        val manifestVersion = manifestJson.optString("version", "")
        if (!ExtensionVersionComparator.isValidVersion(manifestVersion)) {
            return PackageVerificationSummary(
                verificationState = PackageVerificationState.REJECTED,
                trustState = ExtensionTrustState.UNTRUSTED_REJECTED,
                derivedId = derivedId,
                publicKeyBytes = publicKeyBytes,
                rawSignatureBytes = signatureBytes,
                zipPayloadBytes = zipBytes,
                manifestContent = manifestContent,
                extensionVersion = manifestVersion,
                isSignatureVerified = isSignatureVerified,
                verifiedContentsSupported = verifiedContentsSupported,
                algorithm = algorithm,
                error = ExtensionUpdateError.ManifestInvalid("Invalid manifest version format: '$manifestVersion'")
            )
        }

        // 4. Derive/Verify Identity
        val manifestKey = manifestJson.optString("key", "").ifBlank { null }
        if (manifestKey != null) {
            val manifestIdentity = try {
                ExtensionIdGenerator.generateFromPublicKey(manifestKey)
            } catch (e: Exception) {
                null
            }

            if (manifestIdentity == null) {
                return PackageVerificationSummary(
                    verificationState = PackageVerificationState.REJECTED,
                    trustState = ExtensionTrustState.UNTRUSTED_REJECTED,
                    derivedId = derivedId,
                    publicKeyBytes = publicKeyBytes,
                    rawSignatureBytes = signatureBytes,
                    zipPayloadBytes = zipBytes,
                    manifestContent = manifestContent,
                    extensionVersion = manifestVersion,
                    isSignatureVerified = isSignatureVerified,
                    verifiedContentsSupported = verifiedContentsSupported,
                    algorithm = algorithm,
                    error = ExtensionUpdateError.PublicKeyInvalid("Manifest contains invalid 'key' value")
                )
            }

            if (isCrx && derivedId.isNotEmpty() && manifestIdentity.id != derivedId) {
                return PackageVerificationSummary(
                    verificationState = PackageVerificationState.REJECTED,
                    trustState = ExtensionTrustState.UNTRUSTED_REJECTED,
                    derivedId = derivedId,
                    publicKeyBytes = publicKeyBytes,
                    rawSignatureBytes = signatureBytes,
                    zipPayloadBytes = zipBytes,
                    manifestContent = manifestContent,
                    extensionVersion = manifestVersion,
                    isSignatureVerified = isSignatureVerified,
                    verifiedContentsSupported = verifiedContentsSupported,
                    algorithm = algorithm,
                    error = ExtensionUpdateError.IdentityMismatch(
                        expectedId = derivedId,
                        actualId = manifestIdentity.id
                    )
                )
            }

            if (derivedId.isEmpty()) {
                derivedId = manifestIdentity.id
                publicKeyBytes = manifestIdentity.publicKeyBytes
            }
        }

        if (derivedId.isEmpty()) {
            derivedId = ExtensionIdGenerator.generateOrionLocalIdentity(sourceName, manifestContent).id
        }

        verificationState = PackageVerificationState.IDENTITY_VALID

        // 5. Identity continuity and Key-rotation checks
        if (expectedExtensionId != null && !derivedId.equals(expectedExtensionId, ignoreCase = true)) {
            return PackageVerificationSummary(
                verificationState = PackageVerificationState.REJECTED,
                trustState = ExtensionTrustState.UNTRUSTED_REJECTED,
                derivedId = derivedId,
                publicKeyBytes = publicKeyBytes,
                rawSignatureBytes = signatureBytes,
                zipPayloadBytes = zipBytes,
                manifestContent = manifestContent,
                extensionVersion = manifestVersion,
                isSignatureVerified = isSignatureVerified,
                verifiedContentsSupported = verifiedContentsSupported,
                algorithm = algorithm,
                error = ExtensionUpdateError.IdentityMismatch(
                    expectedId = expectedExtensionId,
                    actualId = derivedId
                )
            )
        }

        if (expectedPublicKeyBytes != null && publicKeyBytes != null) {
            if (!publicKeyBytes.contentEquals(expectedPublicKeyBytes)) {
                return PackageVerificationSummary(
                    verificationState = PackageVerificationState.REJECTED,
                    trustState = ExtensionTrustState.UNTRUSTED_REJECTED,
                    derivedId = derivedId,
                    publicKeyBytes = publicKeyBytes,
                    rawSignatureBytes = signatureBytes,
                    zipPayloadBytes = zipBytes,
                    manifestContent = manifestContent,
                    extensionVersion = manifestVersion,
                    isSignatureVerified = isSignatureVerified,
                    verifiedContentsSupported = verifiedContentsSupported,
                    algorithm = algorithm,
                    error = ExtensionUpdateError.KeyRotationRejected(derivedId)
                )
            }
        }

        verificationState = PackageVerificationState.INTEGRITY_VALID
        verificationState = PackageVerificationState.VERIFIED

        return PackageVerificationSummary(
            verificationState = verificationState,
            trustState = trustState,
            derivedId = derivedId,
            publicKeyBytes = publicKeyBytes,
            rawSignatureBytes = signatureBytes,
            zipPayloadBytes = zipBytes,
            manifestContent = manifestContent,
            extensionVersion = manifestVersion,
            isSignatureVerified = isSignatureVerified,
            verifiedContentsSupported = verifiedContentsSupported,
            algorithm = algorithm,
            error = null
        )
    }

    private fun isZipHeader(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        return bytes[0] == 0x50.toByte() &&
                bytes[1] == 0x4B.toByte() &&
                bytes[2] == 0x03.toByte() &&
                bytes[3] == 0x04.toByte()
    }

    private data class ZipValidationResult(
        val isValid: Boolean,
        val manifestContent: String? = null,
        val error: Throwable? = null
    )

    private fun validateZipArchive(zipBytes: ByteArray): ZipValidationResult {
        var entryCount = 0
        var totalUncompressedSize = 0L
        var manifestText: String? = null

        try {
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    entryCount++
                    if (entryCount > MAX_ZIP_ENTRIES) {
                        return ZipValidationResult(
                            isValid = false,
                            error = ExtensionUpdateError.InstallFailed("Zip archive exceeds maximum entry count of $MAX_ZIP_ENTRIES")
                        )
                    }

                    val name = entry.name
                    if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                        return ZipValidationResult(
                            isValid = false,
                            error = ExtensionError.InstallerError.PathTraversalDetected(name)
                        )
                    }

                    val bos = ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    var read = zis.read(buffer)
                    var entryUncompressed = 0L

                    while (read != -1) {
                        entryUncompressed += read
                        totalUncompressedSize += read

                        if (totalUncompressedSize > MAX_UNCOMPRESSED_SIZE_BYTES) {
                            return ZipValidationResult(
                                isValid = false,
                                error = ExtensionUpdateError.PackageTooLarge(totalUncompressedSize, MAX_UNCOMPRESSED_SIZE_BYTES)
                            )
                        }

                        if (manifestText == null && (name == "manifest.json" || name.endsWith("/manifest.json"))) {
                            bos.write(buffer, 0, read)
                        }

                        read = zis.read(buffer)
                    }

                    if (manifestText == null && (name == "manifest.json" || name.endsWith("/manifest.json"))) {
                        manifestText = bos.toString(Charsets.UTF_8.name())
                    }

                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            if (zipBytes.isNotEmpty()) {
                val ratio = totalUncompressedSize.toDouble() / zipBytes.size.toDouble()
                if (ratio > MAX_COMPRESSION_RATIO && totalUncompressedSize > 10 * 1024 * 1024L) {
                    return ZipValidationResult(
                        isValid = false,
                        error = ExtensionUpdateError.InstallFailed("Zip archive exceeds safety compression ratio ($ratio > $MAX_COMPRESSION_RATIO)")
                    )
                }
            }

            return ZipValidationResult(isValid = true, manifestContent = manifestText)

        } catch (e: Exception) {
            return ZipValidationResult(
                isValid = false,
                error = ExtensionUpdateError.InstallFailed("Malformed ZIP archive: ${e.message}", e)
            )
        }
    }

    private data class Crx2Result(
        val isValid: Boolean,
        val extensionId: String?,
        val publicKeyBytes: ByteArray?,
        val signatureBytes: ByteArray?,
        val zipPayloadBytes: ByteArray,
        val errorMessage: String? = null
    )

    private fun verifyCrx2(packageBytes: ByteArray): Crx2Result {
        if (packageBytes.size < 16) {
            return Crx2Result(false, null, null, null, ByteArray(0), "Package too small for CRX2")
        }

        val pubKeyLen = readUint32LE(packageBytes, 8).toInt()
        val sigLen = readUint32LE(packageBytes, 12).toInt()
        val zipStart = 16 + pubKeyLen + sigLen

        if (pubKeyLen <= 0 || sigLen <= 0 || zipStart > packageBytes.size) {
            return Crx2Result(false, null, null, null, ByteArray(0), "Invalid CRX2 header lengths")
        }

        val pubKeyBytes = packageBytes.copyOfRange(16, 16 + pubKeyLen)
        val sigBytes = packageBytes.copyOfRange(16 + pubKeyLen, zipStart)
        val zipPayload = packageBytes.copyOfRange(zipStart, packageBytes.size)

        val digest = MessageDigest.getInstance("SHA-256").digest(pubKeyBytes)
        val extensionId = Crx3Verifier.encodeNibblesToAlphabet(digest, 16)

        val isSigValid = try {
            val keySpec = X509EncodedKeySpec(pubKeyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKey: PublicKey = keyFactory.generatePublic(keySpec)

            val signature = Signature.getInstance("SHA256withRSA")
            signature.initVerify(publicKey)
            signature.update(zipPayload)
            signature.verify(sigBytes)
        } catch (e: Exception) {
            false
        }

        return if (isSigValid) {
            Crx2Result(true, extensionId, pubKeyBytes, sigBytes, zipPayload, null)
        } else {
            Crx2Result(false, extensionId, pubKeyBytes, sigBytes, zipPayload, "CRX2 RSA signature verification failed")
        }
    }

    private fun readUint32LE(bytes: ByteArray, offset: Int): Long {
        return (bytes[offset].toLong() and 0xFF) or
                ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
                ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
                ((bytes[offset + 3].toLong() and 0xFF) shl 24)
    }
}
