package com.swift.browser.extensionengine

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

enum class HeaderValidationResult {
    HEADER_VALID,
    HEADER_INVALID,
    PLAIN_ZIP
}

enum class SignatureVerificationResult {
    SIGNATURE_VERIFIED,
    SIGNATURE_INVALID,
    UNSIGNED
}

enum class PackageSourceType {
    DIRECTORY, ZIP, CRX2, CRX3
}

enum class PackageInstallationState {
    DISCOVERED, VALIDATING, EXTRACTING, VERIFYING, INSTALLING, INSTALLED, FAILED, ROLLED_BACK, DISABLED, UNINSTALLED
}

enum class PackageVerificationState {
    UNVERIFIED,
    HEADER_VALID,
    PARSED,
    SIGNATURE_VALID,
    SIGNATURE_VERIFIED,
    IDENTITY_VALID,
    INTEGRITY_VALID,
    VERIFIED,
    REJECTED
}

data class ExtensionPackage(
    val rawBytes: ByteArray,
    val sourceName: String,
    val headerValidation: HeaderValidationResult,
    val signatureVerification: SignatureVerificationResult,
    val zipPayloadBytes: ByteArray,
    val extractedPublicKey: ByteArray? = null,
    val rawSignature: ByteArray? = null,
    val crxVersion: Int = 0,
    val derivedExtensionId: String? = null,
    val archivePath: String = "",
    val rootPath: String = "",
    val manifest: ValidatedExtensionManifest? = null,
    val sizeBytes: Long = rawBytes.size.toLong(),
    val installationState: PackageInstallationState = PackageInstallationState.DISCOVERED
) {
    val packageId: String get() = derivedExtensionId ?: sourceName
    val sourceType: PackageSourceType get() = when {
        crxVersion == 2 -> PackageSourceType.CRX2
        crxVersion == 3 -> PackageSourceType.CRX3
        headerValidation == HeaderValidationResult.PLAIN_ZIP -> PackageSourceType.ZIP
        else -> PackageSourceType.DIRECTORY
    }
    val identity: ExtensionIdentity? get() = derivedExtensionId?.let {
        ExtensionIdentity(it, IdentityType.CHROME_COMPATIBLE_IDENTITY, extractedPublicKey, rawSignature)
    }
    val verificationState: PackageVerificationState get() = when (signatureVerification) {
        SignatureVerificationResult.SIGNATURE_VERIFIED -> PackageVerificationState.SIGNATURE_VERIFIED
        SignatureVerificationResult.SIGNATURE_INVALID -> PackageVerificationState.REJECTED
        SignatureVerificationResult.UNSIGNED -> if (headerValidation == HeaderValidationResult.HEADER_VALID) PackageVerificationState.HEADER_VALID else PackageVerificationState.UNVERIFIED
    }

    val isCrx: Boolean get() = headerValidation == HeaderValidationResult.HEADER_VALID
    val isSignatureVerified: Boolean get() = signatureVerification == SignatureVerificationResult.SIGNATURE_VERIFIED

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ExtensionPackage

        if (!rawBytes.contentEquals(other.rawBytes)) return false
        if (sourceName != other.sourceName) return false
        if (headerValidation != other.headerValidation) return false
        if (signatureVerification != other.signatureVerification) return false

        return true
    }

    override fun hashCode(): Int {
        var result = rawBytes.contentHashCode()
        result = 31 * result + sourceName.hashCode()
        result = 31 * result + headerValidation.hashCode()
        result = 31 * result + signatureVerification.hashCode()
        return result
    }

    companion object {
        private val CRX3_SIGNED_DATA_PREFIX = byteArrayOf(
            0x43, 0x52, 0x58, 0x33, 0x20, 0x53, 0x69, 0x67,
            0x6e, 0x65, 0x64, 0x44, 0x61, 0x74, 0x61, 0x00
        ) // "CRX3 SignedData\x00"

        fun parseAndValidate(rawBytes: ByteArray, sourceName: String = "local_archive"): ExtensionPackage {
            if (rawBytes.size < 4) {
                throw IllegalArgumentException("Invalid package: payload is too small")
            }

            // Check "Cr24" magic header (0x43 0x72 0x32 0x34)
            if (rawBytes[0] == 0x43.toByte() && rawBytes[1] == 0x72.toByte() &&
                rawBytes[2] == 0x32.toByte() && rawBytes[3] == 0x34.toByte()) {

                if (rawBytes.size < 12) {
                    return ExtensionPackage(
                        rawBytes = rawBytes,
                        sourceName = sourceName,
                        headerValidation = HeaderValidationResult.HEADER_INVALID,
                        signatureVerification = SignatureVerificationResult.SIGNATURE_INVALID,
                        zipPayloadBytes = ByteArray(0)
                    )
                }

                val version = (rawBytes[4].toInt() and 0xFF) or
                        ((rawBytes[5].toInt() and 0xFF) shl 8) or
                        ((rawBytes[6].toInt() and 0xFF) shl 16) or
                        ((rawBytes[7].toInt() and 0xFF) shl 24)

                if (version == 2) {
                    val pubKeyLen = (rawBytes[8].toInt() and 0xFF) or
                            ((rawBytes[9].toInt() and 0xFF) shl 8) or
                            ((rawBytes[10].toInt() and 0xFF) shl 16) or
                            ((rawBytes[11].toInt() and 0xFF) shl 24)

                    val sigLen = (rawBytes[12].toInt() and 0xFF) or
                            ((rawBytes[13].toInt() and 0xFF) shl 8) or
                            ((rawBytes[14].toInt() and 0xFF) shl 16) or
                            ((rawBytes[15].toInt() and 0xFF) shl 24)

                    val zipStart = 16 + pubKeyLen + sigLen
                    if (zipStart <= rawBytes.size && pubKeyLen > 0 && sigLen > 0) {
                        val pubKeyBytes = rawBytes.copyOfRange(16, 16 + pubKeyLen)
                        val sigBytes = rawBytes.copyOfRange(16 + pubKeyLen, zipStart)
                        val zipBytes = rawBytes.copyOfRange(zipStart, rawBytes.size)

                        val verified = verifyRsaSha256Signature(zipBytes, pubKeyBytes, sigBytes)
                        val sigState = if (verified) SignatureVerificationResult.SIGNATURE_VERIFIED else SignatureVerificationResult.SIGNATURE_INVALID
                        val derivedId = if (verified) deriveExtensionIdFromPublicKey(pubKeyBytes) else null

                        return ExtensionPackage(
                            rawBytes = rawBytes,
                            sourceName = sourceName,
                            headerValidation = HeaderValidationResult.HEADER_VALID,
                            signatureVerification = sigState,
                            zipPayloadBytes = zipBytes,
                            extractedPublicKey = pubKeyBytes,
                            rawSignature = sigBytes,
                            crxVersion = 2,
                            derivedExtensionId = derivedId
                        )
                    }
                } else if (version == 3) {
                    val headerLen = (rawBytes[8].toInt() and 0xFF) or
                            ((rawBytes[9].toInt() and 0xFF) shl 8) or
                            ((rawBytes[10].toInt() and 0xFF) shl 16) or
                            ((rawBytes[11].toInt() and 0xFF) shl 24)

                    val zipStart = 12 + headerLen
                    if (zipStart <= rawBytes.size && headerLen > 0) {
                        val headerBytes = rawBytes.copyOfRange(12, zipStart)
                        val zipBytes = rawBytes.copyOfRange(zipStart, rawBytes.size)

                        // Check ZIP magic
                        val isZip = zipBytes.size >= 4 && zipBytes[0] == 0x50.toByte() && zipBytes[1] == 0x4B.toByte()
                        if (!isZip) {
                            return ExtensionPackage(
                                rawBytes = rawBytes,
                                sourceName = sourceName,
                                headerValidation = HeaderValidationResult.HEADER_INVALID,
                                signatureVerification = SignatureVerificationResult.SIGNATURE_INVALID,
                                zipPayloadBytes = ByteArray(0)
                            )
                        }

                        // Parse and verify CRX3 using production Crx3Verifier
                        val crx3Result = Crx3Verifier.verifyCrx3(rawBytes)
                        if (crx3Result.isValid) {
                            return ExtensionPackage(
                                rawBytes = rawBytes,
                                sourceName = sourceName,
                                headerValidation = HeaderValidationResult.HEADER_VALID,
                                signatureVerification = SignatureVerificationResult.SIGNATURE_VERIFIED,
                                zipPayloadBytes = crx3Result.zipPayloadBytes,
                                extractedPublicKey = crx3Result.publicKeyBytes,
                                rawSignature = crx3Result.signatureBytes,
                                crxVersion = 3,
                                derivedExtensionId = crx3Result.extensionId
                            )
                        } else {
                            return ExtensionPackage(
                                rawBytes = rawBytes,
                                sourceName = sourceName,
                                headerValidation = HeaderValidationResult.HEADER_VALID,
                                signatureVerification = SignatureVerificationResult.SIGNATURE_INVALID,
                                zipPayloadBytes = crx3Result.zipPayloadBytes,
                                crxVersion = 3
                            )
                        }
                    }
                }

                return ExtensionPackage(
                    rawBytes = rawBytes,
                    sourceName = sourceName,
                    headerValidation = HeaderValidationResult.HEADER_INVALID,
                    signatureVerification = SignatureVerificationResult.SIGNATURE_INVALID,
                    zipPayloadBytes = ByteArray(0)
                )
            }

            // Standard PKZIP header (0x50 0x4B 0x03 0x04)
            if (rawBytes[0] == 0x50.toByte() && rawBytes[1] == 0x4B.toByte() &&
                rawBytes[2] == 0x03.toByte() && rawBytes[3] == 0x04.toByte()) {
                return ExtensionPackage(
                    rawBytes = rawBytes,
                    sourceName = sourceName,
                    headerValidation = HeaderValidationResult.PLAIN_ZIP,
                    signatureVerification = SignatureVerificationResult.UNSIGNED,
                    zipPayloadBytes = rawBytes
                )
            }

            return ExtensionPackage(
                rawBytes = rawBytes,
                sourceName = sourceName,
                headerValidation = HeaderValidationResult.HEADER_INVALID,
                signatureVerification = SignatureVerificationResult.SIGNATURE_INVALID,
                zipPayloadBytes = rawBytes
            )
        }

        private fun verifyRsaSha256Signature(data: ByteArray, pubKeyDER: ByteArray, sigBytes: ByteArray): Boolean {
            return try {
                val keySpec = X509EncodedKeySpec(pubKeyDER)
                val keyFactory = KeyFactory.getInstance("RSA")
                val pubKey = keyFactory.generatePublic(keySpec)
                val signer = Signature.getInstance("SHA256withRSA")
                signer.initVerify(pubKey)
                signer.update(data)
                signer.verify(sigBytes)
            } catch (e: Exception) {
                false
            }
        }

        fun deriveExtensionIdFromPublicKey(pubKeyDER: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(pubKeyDER)
            val alphabet = "abcdefghijklmnop"
            val sb = StringBuilder(32)
            for (i in 0 until 16) {
                val b = digest[i].toInt() and 0xFF
                val high = (b ushr 4) and 0x0F
                val low = b and 0x0F
                sb.append(alphabet[high])
                sb.append(alphabet[low])
            }
            return sb.toString()
        }
    }
}
