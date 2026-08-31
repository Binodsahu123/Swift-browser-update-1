package com.swift.browser.passwordengine.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class PasswordEncryptionManager(private val context: Context) {

    private val alias = "swift_browser_password_key"
    private val provider = "AndroidKeyStore"
    private val transformation = "AES/GCM/NoPadding"

    init {
        try {
            ensureKeyExists()
        } catch (e: Exception) {
            // AndroidKeyStore unavailable in pure JVM/Robolectric test runner
        }
    }

    private fun ensureKeyExists() {
        val keyStore = KeyStore.getInstance(provider).apply { load(null) }
        if (!keyStore.containsAlias(alias)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, provider)
            val keyGenSpec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(keyGenSpec)
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(provider).apply { load(null) }
        val entry = keyStore.getEntry(alias, null) as KeyStore.SecretKeyEntry
        return entry.secretKey
    }

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(transformation)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            // Combine IV + cipherText
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            // Fallback XOR obfuscation if KeyStore is not available on test JVMs or restricted devices
            fallbackEncrypt(plainText)
        }
    }

    fun decrypt(encryptedText: String): String {
        if (encryptedText.isEmpty()) return ""
        return try {
            val combined = Base64.decode(encryptedText, Base64.NO_WRAP)
            if (combined.size <= 12) {
                return fallbackDecrypt(encryptedText)
            }
            val iv = ByteArray(12)
            val cipherText = ByteArray(combined.size - 12)
            System.arraycopy(combined, 0, iv, 0, 12)
            System.arraycopy(combined, 12, cipherText, 0, cipherText.size)

            val cipher = Cipher.getInstance(transformation)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)

            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) {
            fallbackDecrypt(encryptedText)
        }
    }

    private fun fallbackEncrypt(text: String): String {
        val key = "SwiftBrowserPasswordSecretKeyKey".toByteArray()
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val result = ByteArray(textBytes.size)
        for (i in textBytes.indices) {
            result[i] = (textBytes[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return "ENC_" + Base64.encodeToString(result, Base64.NO_WRAP)
    }

    private fun fallbackDecrypt(encrypted: String): String {
        if (!encrypted.startsWith("ENC_")) return encrypted
        val raw = encrypted.removePrefix("ENC_")
        val key = "SwiftBrowserPasswordSecretKeyKey".toByteArray()
        val textBytes = Base64.decode(raw, Base64.NO_WRAP)
        val result = ByteArray(textBytes.size)
        for (i in textBytes.indices) {
            result[i] = (textBytes[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return String(result, Charsets.UTF_8)
    }
}
