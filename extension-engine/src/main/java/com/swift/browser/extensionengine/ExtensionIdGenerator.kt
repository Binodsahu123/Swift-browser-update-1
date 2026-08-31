package com.swift.browser.extensionengine

import android.util.Base64
import java.security.MessageDigest

enum class IdentityType {
    CHROME_COMPATIBLE_IDENTITY,
    ORION_LOCAL_IDENTITY
}

data class ExtensionIdentity(
    val id: String,
    val identityType: IdentityType,
    val publicKeyBytes: ByteArray? = null,
    val rawSignatureBytes: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ExtensionIdentity
        if (id != other.id) return false
        if (identityType != other.identityType) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + identityType.hashCode()
        return result
    }
}

object ExtensionIdGenerator {

    private const val ALPHABET = "abcdefghijklmnop"

    /**
     * Generates a 32-character Chrome-compatible extension ID from a Base64-encoded public key (RSA / Ed25519)
     * as found in manifest `key` field or CRX3 headers.
     *
     * Algorithm:
     * 1. Decode base64 public key bytes.
     * 2. SHA-256 hash of public key DER bytes.
     * 3. Take first 16 bytes (128 bits = 32 nibbles).
     * 4. Map each nibble (0-15) to character 'a'..'p' (Chromium base16 mapping).
     */
    fun generateFromPublicKey(base64PublicKey: String, rawSignature: ByteArray? = null): ExtensionIdentity {
        val cleanKey = base64PublicKey
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\n", "")
            .replace("\r", "")
            .trim()

        val keyBytes = try {
            Base64.decode(cleanKey, Base64.DEFAULT)
        } catch (e: Exception) {
            throw ExtensionError.IdentityError.InvalidKey(base64PublicKey, e)
        }

        if (keyBytes.isEmpty()) {
            throw ExtensionError.IdentityError.InvalidKey("Public key bytes are empty")
        }

        val digest = MessageDigest.getInstance("SHA-256").digest(keyBytes)
        val id = encodeNibblesToAlphabet(digest, 16)
        return ExtensionIdentity(id, IdentityType.CHROME_COMPATIBLE_IDENTITY, keyBytes, rawSignature)
    }

    /**
     * Generates a deterministic Orion local extension identity for unsigned local ZIP archives.
     */
    fun generateOrionLocalIdentity(packageNameOrSeed: String, manifestContent: String): ExtensionIdentity {
        val seed = "$packageNameOrSeed:$manifestContent"
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
        val id = encodeNibblesToAlphabet(digest, 16)
        return ExtensionIdentity(id, IdentityType.ORION_LOCAL_IDENTITY)
    }

    /**
     * Maps raw digest bytes (up to byteCount) into a 32-character string using alphabet 'a'..'p'.
     */
    private fun encodeNibblesToAlphabet(digest: ByteArray, byteCount: Int): String {
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
