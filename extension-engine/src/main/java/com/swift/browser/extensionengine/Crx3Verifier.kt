package com.swift.browser.extensionengine

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Result of CRX3 verification.
 */
data class Crx3VerificationResult(
    val isValid: Boolean,
    val extensionId: String?,
    val publicKeyBytes: ByteArray?,
    val signatureBytes: ByteArray?,
    val zipPayloadBytes: ByteArray,
    val algorithm: String?,
    val signedHeaderData: ByteArray?,
    val crxIdFromSignedHeader: ByteArray?,
    val verifiedContentsSupported: Boolean,
    val errorMessage: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Crx3VerificationResult
        return isValid == other.isValid &&
                extensionId == other.extensionId &&
                publicKeyBytes.contentEquals(other.publicKeyBytes) &&
                errorMessage == other.errorMessage
    }

    override fun hashCode(): Int {
        var result = isValid.hashCode()
        result = 31 * result + (extensionId?.hashCode() ?: 0)
        result = 31 * result + (publicKeyBytes?.contentHashCode() ?: 0)
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        return result
    }
}

/**
 * Key proof extracted from CRX3 header protobuf.
 */
data class AsymmetricKeyProof(
    val publicKey: ByteArray,
    val signature: ByteArray,
    val isEcdsa: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AsymmetricKeyProof
        return publicKey.contentEquals(other.publicKey) &&
                signature.contentEquals(other.signature) &&
                isEcdsa == other.isEcdsa
    }

    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + signature.contentHashCode()
        result = 31 * result + isEcdsa.hashCode()
        return result
    }
}

/**
 * Parsed CRX3 Header contents.
 */
data class Crx3Header(
    val proofs: List<AsymmetricKeyProof>,
    val signedHeaderData: ByteArray?,
    val crxId: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Crx3Header
        return proofs == other.proofs &&
                signedHeaderData.contentEquals(other.signedHeaderData) &&
                crxId.contentEquals(other.crxId)
    }

    override fun hashCode(): Int {
        var result = proofs.hashCode()
        result = 31 * result + (signedHeaderData?.contentHashCode() ?: 0)
        result = 31 * result + (crxId?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Production-grade Chromium CRX3 package parser and cryptographic signature verifier.
 *
 * Adheres strictly to the Chromium CRX3 SignedData byte construction:
 * - "CRX3 SignedData\0" (16 bytes)
 * - 4-byte little-endian length of signed_header_data
 * - signed_header_data bytes
 * - zip_payload bytes
 */
object Crx3Verifier {

    private val CRX_MAGIC = byteArrayOf(0x43, 0x72, 0x32, 0x34) // "Cr24"
    private val SIGNED_DATA_PREFIX = byteArrayOf(
        0x43, 0x52, 0x58, 0x33, 0x20, 0x53, 0x69, 0x67,
        0x6e, 0x65, 0x64, 0x44, 0x61, 0x74, 0x61, 0x00
    ) // "CRX3 SignedData\0"
    private const val ALPHABET = "abcdefghijklmnop"

    /**
     * Checks if the package bytes begin with the CRX magic header ("Cr24").
     */
    fun isCrxPackage(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        return bytes[0] == CRX_MAGIC[0] &&
                bytes[1] == CRX_MAGIC[1] &&
                bytes[2] == CRX_MAGIC[2] &&
                bytes[3] == CRX_MAGIC[3]
    }

    /**
     * Verifies a CRX3 package completely according to Chromium specifications.
     */
    fun verifyCrx3(packageBytes: ByteArray): Crx3VerificationResult {
        if (packageBytes.size < 12) {
            return Crx3VerificationResult(
                isValid = false,
                extensionId = null,
                publicKeyBytes = null,
                signatureBytes = null,
                zipPayloadBytes = ByteArray(0),
                algorithm = null,
                signedHeaderData = null,
                crxIdFromSignedHeader = null,
                verifiedContentsSupported = false,
                errorMessage = "Package too small to be a valid CRX file (${packageBytes.size} bytes)"
            )
        }

        // Verify magic "Cr24"
        if (!isCrxPackage(packageBytes)) {
            return Crx3VerificationResult(
                isValid = false,
                extensionId = null,
                publicKeyBytes = null,
                signatureBytes = null,
                zipPayloadBytes = ByteArray(0),
                algorithm = null,
                signedHeaderData = null,
                crxIdFromSignedHeader = null,
                verifiedContentsSupported = false,
                errorMessage = "Invalid CRX magic bytes"
            )
        }

        // Read version (uint32 LE at offset 4)
        val version = readUint32LE(packageBytes, 4)
        if (version != 3L) {
            return Crx3VerificationResult(
                isValid = false,
                extensionId = null,
                publicKeyBytes = null,
                signatureBytes = null,
                zipPayloadBytes = ByteArray(0),
                algorithm = null,
                signedHeaderData = null,
                crxIdFromSignedHeader = null,
                verifiedContentsSupported = false,
                errorMessage = "CRX version is $version, expected CRX3 (version 3)"
            )
        }

        // Read header_size (uint32 LE at offset 8)
        val headerSize = readUint32LE(packageBytes, 8).toInt()
        val headerStart = 12
        val headerEnd = headerStart + headerSize

        if (headerSize <= 0 || headerEnd > packageBytes.size) {
            return Crx3VerificationResult(
                isValid = false,
                extensionId = null,
                publicKeyBytes = null,
                signatureBytes = null,
                zipPayloadBytes = ByteArray(0),
                algorithm = null,
                signedHeaderData = null,
                crxIdFromSignedHeader = null,
                verifiedContentsSupported = false,
                errorMessage = "CRX3 header size ($headerSize) exceeds package boundary"
            )
        }

        val headerBytes = packageBytes.copyOfRange(headerStart, headerEnd)
        val zipPayloadBytes = packageBytes.copyOfRange(headerEnd, packageBytes.size)

        // Validate ZIP payload is non-empty and starts with PK header
        if (zipPayloadBytes.size < 4 ||
            zipPayloadBytes[0] != 0x50.toByte() ||
            zipPayloadBytes[1] != 0x4B.toByte()
        ) {
            return Crx3VerificationResult(
                isValid = false,
                extensionId = null,
                publicKeyBytes = null,
                signatureBytes = null,
                zipPayloadBytes = zipPayloadBytes,
                algorithm = null,
                signedHeaderData = null,
                crxIdFromSignedHeader = null,
                verifiedContentsSupported = false,
                errorMessage = "CRX3 zip payload does not contain a valid ZIP header"
            )
        }

        // Parse Protobuf CrxFileHeader
        val crxHeader = try {
            parseCrxFileHeaderProtobuf(headerBytes)
        } catch (e: Exception) {
            return Crx3VerificationResult(
                isValid = false,
                extensionId = null,
                publicKeyBytes = null,
                signatureBytes = null,
                zipPayloadBytes = zipPayloadBytes,
                algorithm = null,
                signedHeaderData = null,
                crxIdFromSignedHeader = null,
                verifiedContentsSupported = false,
                errorMessage = "Failed to parse CRX3 protobuf header: ${e.message}"
            )
        }

        if (crxHeader.proofs.isEmpty()) {
            return Crx3VerificationResult(
                isValid = false,
                extensionId = null,
                publicKeyBytes = null,
                signatureBytes = null,
                zipPayloadBytes = zipPayloadBytes,
                algorithm = null,
                signedHeaderData = crxHeader.signedHeaderData,
                crxIdFromSignedHeader = crxHeader.crxId,
                verifiedContentsSupported = false,
                errorMessage = "CRX3 header does not contain any asymmetric key proofs"
            )
        }

        // Construct SignedData byte stream:
        // [16 bytes "CRX3 SignedData\0"] + [4 bytes signed_header_size LE] + [signed_header_data] + [zip_payload]
        val signedHeaderData = crxHeader.signedHeaderData ?: ByteArray(0)
        val signedDataStream = constructSignedDataBytes(signedHeaderData, zipPayloadBytes)

        // Verify each proof until one succeeds
        for (proof in crxHeader.proofs) {
            val pubKeyBytes = proof.publicKey
            val sigBytes = proof.signature
            val isEcdsa = proof.isEcdsa
            val algorithmName = if (isEcdsa) "ECDSA (SHA256withECDSA)" else "RSA (SHA256withRSA)"

            // Derive 16-byte ID from SHA-256(publicKey DER)
            val digest = MessageDigest.getInstance("SHA-256").digest(pubKeyBytes)
            val derivedRawId = digest.copyOfRange(0, 16)
            val derivedExtensionId = encodeNibblesToAlphabet(derivedRawId)

            // If crx_id is present in signedHeaderData, verify match
            if (crxHeader.crxId != null && crxHeader.crxId.isNotEmpty()) {
                if (!crxHeader.crxId.contentEquals(derivedRawId)) {
                    // Try comparing against full 32-char string if raw ID representation
                    val headerIdStr = encodeNibblesToAlphabet(crxHeader.crxId)
                    if (headerIdStr != derivedExtensionId) {
                        return Crx3VerificationResult(
                            isValid = false,
                            extensionId = derivedExtensionId,
                            publicKeyBytes = pubKeyBytes,
                            signatureBytes = sigBytes,
                            zipPayloadBytes = zipPayloadBytes,
                            algorithm = algorithmName,
                            signedHeaderData = signedHeaderData,
                            crxIdFromSignedHeader = crxHeader.crxId,
                            verifiedContentsSupported = false,
                            errorMessage = "CRX3 signed header crx_id does not match derived public key ID ($derivedExtensionId)"
                        )
                    }
                }
            }

            // Cryptographic verification
            val isSigValid = verifyCryptographicProof(pubKeyBytes, sigBytes, signedDataStream, isEcdsa)
            if (isSigValid) {
                return Crx3VerificationResult(
                    isValid = true,
                    extensionId = derivedExtensionId,
                    publicKeyBytes = pubKeyBytes,
                    signatureBytes = sigBytes,
                    zipPayloadBytes = zipPayloadBytes,
                    algorithm = algorithmName,
                    signedHeaderData = signedHeaderData,
                    crxIdFromSignedHeader = crxHeader.crxId,
                    verifiedContentsSupported = crxHeader.signedHeaderData != null && crxHeader.signedHeaderData.isNotEmpty(),
                    errorMessage = null
                )
            }
        }

        return Crx3VerificationResult(
            isValid = false,
            extensionId = null,
            publicKeyBytes = null,
            signatureBytes = null,
            zipPayloadBytes = zipPayloadBytes,
            algorithm = null,
            signedHeaderData = crxHeader.signedHeaderData,
            crxIdFromSignedHeader = crxHeader.crxId,
            verifiedContentsSupported = false,
            errorMessage = "Cryptographic signature verification failed for all key proofs"
        )
    }

    /**
     * Constructs the canonical CRX3 SignedData verification bytes.
     */
    fun constructSignedDataBytes(signedHeaderData: ByteArray, zipPayload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(SIGNED_DATA_PREFIX.size + 4 + signedHeaderData.size + zipPayload.size)
        out.write(SIGNED_DATA_PREFIX)
        val headerLen = signedHeaderData.size
        out.write(headerLen and 0xFF)
        out.write((headerLen ushr 8) and 0xFF)
        out.write((headerLen ushr 16) and 0xFF)
        out.write((headerLen ushr 24) and 0xFF)
        if (signedHeaderData.isNotEmpty()) {
            out.write(signedHeaderData)
        }
        out.write(zipPayload)
        return out.toByteArray()
    }

    /**
     * Cryptographically verifies the signature over the signedDataBytes.
     */
    private fun verifyCryptographicProof(
        publicKeyBytes: ByteArray,
        rawSignatureBytes: ByteArray,
        signedDataBytes: ByteArray,
        isEcdsa: Boolean
    ): Boolean {
        return try {
            if (isEcdsa) {
                val keySpec = X509EncodedKeySpec(publicKeyBytes)
                val keyFactory = KeyFactory.getInstance("EC")
                val publicKey: PublicKey = keyFactory.generatePublic(keySpec)

                val signature = Signature.getInstance("SHA256withECDSA")
                signature.initVerify(publicKey)
                signature.update(signedDataBytes)

                val derSig = normalizeEcdsaSignatureToDer(rawSignatureBytes)
                signature.verify(derSig)
            } else {
                val keySpec = X509EncodedKeySpec(publicKeyBytes)
                val keyFactory = KeyFactory.getInstance("RSA")
                val publicKey: PublicKey = keyFactory.generatePublic(keySpec)

                val signature = Signature.getInstance("SHA256withRSA")
                signature.initVerify(publicKey)
                signature.update(signedDataBytes)
                signature.verify(rawSignatureBytes)
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Normalizes IEEE P1363 (r || s 64-byte) ECDSA signatures to ASN.1 DER if necessary.
     */
    private fun normalizeEcdsaSignatureToDer(sigBytes: ByteArray): ByteArray {
        // If already ASN.1 sequence (starts with 0x30), return as is
        if (sigBytes.isNotEmpty() && sigBytes[0] == 0x30.toByte()) {
            return sigBytes
        }

        // If 64 bytes (P-256 r: 32 bytes, s: 32 bytes)
        if (sigBytes.size == 64) {
            val r = sigBytes.copyOfRange(0, 32)
            val s = sigBytes.copyOfRange(32, 64)
            return encodeDerEcdsa(r, s)
        }

        return sigBytes
    }

    private fun encodeDerEcdsa(r: ByteArray, s: ByteArray): ByteArray {
        val rEncoded = encodeDerInteger(r)
        val sEncoded = encodeDerInteger(s)
        val totalLength = rEncoded.size + sEncoded.size

        val out = ByteArrayOutputStream()
        out.write(0x30) // SEQUENCE
        if (totalLength < 128) {
            out.write(totalLength)
        } else {
            out.write(0x81)
            out.write(totalLength)
        }
        out.write(rEncoded)
        out.write(sEncoded)
        return out.toByteArray()
    }

    private fun encodeDerInteger(bytes: ByteArray): ByteArray {
        var start = 0
        while (start < bytes.size - 1 && bytes[start] == 0.toByte()) {
            start++
        }
        val needsLeadingZero = (bytes[start].toInt() and 0x80) != 0
        val len = (bytes.size - start) + (if (needsLeadingZero) 1 else 0)

        val out = ByteArrayOutputStream()
        out.write(0x02) // INTEGER
        out.write(len)
        if (needsLeadingZero) {
            out.write(0x00)
        }
        out.write(bytes, start, bytes.size - start)
        return out.toByteArray()
    }

    /**
     * Parses the protobuf CrxFileHeader bytes.
     * Field 2: sha256_with_rsa (repeated AsymmetricKeyProof)
     * Field 3: sha256_with_ecdsa (repeated AsymmetricKeyProof)
     * Field 10000: signed_header_data (bytes)
     */
    fun parseCrxFileHeaderProtobuf(bytes: ByteArray): Crx3Header {
        val proofs = mutableListOf<AsymmetricKeyProof>()
        var signedHeaderData: ByteArray? = null
        var crxId: ByteArray? = null

        var offset = 0
        while (offset < bytes.size) {
            val tagAndWire = readVarint(bytes, offset)
            val tag = (tagAndWire.value ushr 3).toInt()
            val wireType = (tagAndWire.value and 0x07).toInt()
            offset = tagAndWire.nextOffset

            when (wireType) {
                0 -> { // Varint
                    val v = readVarint(bytes, offset)
                    offset = v.nextOffset
                }
                1 -> { // 64-bit
                    offset += 8
                }
                2 -> { // Length-delimited
                    val lenVarint = readVarint(bytes, offset)
                    val len = lenVarint.value.toInt()
                    offset = lenVarint.nextOffset
                    val fieldData = bytes.copyOfRange(offset, offset + len)
                    offset += len

                    when (tag) {
                        2 -> { // sha256_with_rsa
                            parseAsymmetricKeyProof(fieldData, isEcdsa = false)?.let { proofs.add(it) }
                        }
                        3 -> { // sha256_with_ecdsa
                            parseAsymmetricKeyProof(fieldData, isEcdsa = true)?.let { proofs.add(it) }
                        }
                        10000 -> { // signed_header_data
                            signedHeaderData = fieldData
                            // Parse SignedData to extract crx_id (Field 1)
                            crxId = parseSignedDataCrxId(fieldData)
                        }
                    }
                }
                5 -> { // 32-bit
                    offset += 4
                }
                else -> {
                    // Unknown wire type, abort parsing
                    break
                }
            }
        }

        return Crx3Header(proofs, signedHeaderData, crxId)
    }

    /**
     * Parses an AsymmetricKeyProof protobuf message.
     * Field 1: public_key (bytes)
     * Field 2: signature (bytes)
     */
    private fun parseAsymmetricKeyProof(bytes: ByteArray, isEcdsa: Boolean): AsymmetricKeyProof? {
        var publicKey: ByteArray? = null
        var signature: ByteArray? = null

        var offset = 0
        while (offset < bytes.size) {
            val tagAndWire = readVarint(bytes, offset)
            val tag = (tagAndWire.value ushr 3).toInt()
            val wireType = (tagAndWire.value and 0x07).toInt()
            offset = tagAndWire.nextOffset

            when (wireType) {
                0 -> {
                    val v = readVarint(bytes, offset)
                    offset = v.nextOffset
                }
                1 -> offset += 8
                2 -> {
                    val lenVarint = readVarint(bytes, offset)
                    val len = lenVarint.value.toInt()
                    offset = lenVarint.nextOffset
                    val fieldData = bytes.copyOfRange(offset, offset + len)
                    offset += len

                    when (tag) {
                        1 -> publicKey = fieldData
                        2 -> signature = fieldData
                    }
                }
                5 -> offset += 4
                else -> break
            }
        }

        return if (publicKey != null && signature != null) {
            AsymmetricKeyProof(publicKey, signature, isEcdsa)
        } else null
    }

    /**
     * Parses the SignedData protobuf message from signed_header_data.
     * Field 1: crx_id (bytes, 16 bytes raw)
     */
    private fun parseSignedDataCrxId(bytes: ByteArray): ByteArray? {
        var offset = 0
        while (offset < bytes.size) {
            val tagAndWire = readVarint(bytes, offset)
            val tag = (tagAndWire.value ushr 3).toInt()
            val wireType = (tagAndWire.value and 0x07).toInt()
            offset = tagAndWire.nextOffset

            when (wireType) {
                0 -> {
                    val v = readVarint(bytes, offset)
                    offset = v.nextOffset
                }
                1 -> offset += 8
                2 -> {
                    val lenVarint = readVarint(bytes, offset)
                    val len = lenVarint.value.toInt()
                    offset = lenVarint.nextOffset
                    val fieldData = bytes.copyOfRange(offset, offset + len)
                    offset += len

                    if (tag == 1) {
                        return fieldData
                    }
                }
                5 -> offset += 4
                else -> break
            }
        }
        return null
    }

    data class VarintResult(val value: Long, val nextOffset: Int)

    private fun readVarint(bytes: ByteArray, startOffset: Int): VarintResult {
        var value = 0L
        var shift = 0
        var offset = startOffset
        while (offset < bytes.size) {
            val b = bytes[offset].toInt()
            offset++
            value = value or ((b.toLong() and 0x7F) shl shift)
            if ((b and 0x80) == 0) {
                return VarintResult(value, offset)
            }
            shift += 7
            if (shift >= 64) break
        }
        return VarintResult(value, offset)
    }

    private fun readUint32LE(bytes: ByteArray, offset: Int): Long {
        return (bytes[offset].toLong() and 0xFF) or
                ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
                ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
                ((bytes[offset + 3].toLong() and 0xFF) shl 24)
    }

    fun encodeNibblesToAlphabet(digest: ByteArray, byteCount: Int = 16): String {
        val sb = StringBuilder()
        val limit = minOf(byteCount, digest.size)
        for (i in 0 until limit) {
            val b = digest[i].toInt() and 0xFF
            val highNibble = (b ushr 4) and 0x0F
            val lowNibble = b and 0x0F
            sb.append(ALPHABET[highNibble])
            sb.append(ALPHABET[lowNibble])
        }
        return sb.toString()
    }
}
