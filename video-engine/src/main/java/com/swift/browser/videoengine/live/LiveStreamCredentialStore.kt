package com.swift.browser.videoengine.live

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class StreamProtocol {
    RTMP, RTMPS
}

data class LiveStreamEndpoint(
    val serverUrl: String,
    val port: Int = 1935,
    val protocol: StreamProtocol = StreamProtocol.RTMP,
    val tlsRequired: Boolean = false
) {
    fun buildFullUrl(streamKey: String): String {
        val isRtmps = protocol == StreamProtocol.RTMPS || tlsRequired
        val streamingProtocol = if (isRtmps) StreamingProtocol.RTMPS else StreamingProtocol.RTMP
        return RtmpUrlParser.buildUrl(
            serverUrl = serverUrl,
            port = port,
            protocol = streamingProtocol,
            tlsRequired = tlsRequired,
            streamKey = streamKey
        )
    }
}

data class LiveStreamDestination(
    val name: String,
    val endpoint: LiveStreamEndpoint
)

object LiveStreamCredentialStore {
    private const val PREFS_NAME = "live_stream_secure_prefs"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_PORT = "port"
    private const val KEY_PROTOCOL = "protocol"
    private const val KEY_TLS_REQUIRED = "tls_required"
    private const val KEY_ENCRYPTED_STREAM_KEY = "encrypted_stream_key"
    private const val KEY_IV = "encryption_iv"

    private const val KEY_ALIAS = "LiveStreamSecretKeyAlias"
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private var decryptedKeyCache: CharArray? = null

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val entry = keyStore.getEntry(KEY_ALIAS, null)
        if (entry is KeyStore.SecretKeyEntry) {
            return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    @Synchronized
    fun saveCredentials(
        context: Context,
        endpoint: LiveStreamEndpoint,
        streamKey: CharArray
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        editor.putString(KEY_SERVER_URL, endpoint.serverUrl)
        editor.putInt(KEY_PORT, endpoint.port)
        editor.putString(KEY_PROTOCOL, endpoint.protocol.name)
        editor.putBoolean(KEY_TLS_REQUIRED, endpoint.tlsRequired)

        try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val iv = cipher.iv
            val plainBytes = streamKey.map { it.code.toByte() }.toByteArray()
            val encryptedBytes = cipher.doFinal(plainBytes)

            // Securely clear temporary plainBytes in memory
            plainBytes.fill(0)

            val encryptedStr = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            val ivStr = Base64.encodeToString(iv, Base64.NO_WRAP)

            editor.putString(KEY_ENCRYPTED_STREAM_KEY, encryptedStr)
            editor.putString(KEY_IV, ivStr)
        } catch (e: Exception) {
            // Fail closed securely: do NOT store stream key in plaintext/Base64
            throw RuntimeException("Secure Keystore encryption failed", e)
        }

        editor.apply()

        // Cache decrypted key for active sessions
        decryptedKeyCache = streamKey.clone()
    }

    @Synchronized
    fun getCredentials(context: Context): Pair<LiveStreamEndpoint, CharArray?>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val serverUrl = prefs.getString(KEY_SERVER_URL, null) ?: return null
        val port = prefs.getInt(KEY_PORT, 1935)
        val protocolStr = prefs.getString(KEY_PROTOCOL, StreamProtocol.RTMP.name)
        val protocol = try { StreamProtocol.valueOf(protocolStr!!) } catch (_: Exception) { StreamProtocol.RTMP }
        val tlsRequired = prefs.getBoolean(KEY_TLS_REQUIRED, false)

        val endpoint = LiveStreamEndpoint(serverUrl, port, protocol, tlsRequired)

        // Try getting cached key first
        val cached = decryptedKeyCache
        if (cached != null) {
            return Pair(endpoint, cached.clone())
        }

        val encryptedStr = prefs.getString(KEY_ENCRYPTED_STREAM_KEY, null) ?: return Pair(endpoint, null)
        val ivStr = prefs.getString(KEY_IV, null) ?: return Pair(endpoint, null)

        try {
            val encryptedBytes = Base64.decode(encryptedStr, Base64.NO_WRAP)
            val iv = Base64.decode(ivStr, Base64.NO_WRAP)

            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val plainBytes = cipher.doFinal(encryptedBytes)
            val charArray = CharArray(plainBytes.size) { i -> plainBytes[i].toInt().toChar() }
            
            plainBytes.fill(0)

            decryptedKeyCache = charArray.clone()
            return Pair(endpoint, charArray)
        } catch (e: Exception) {
            return Pair(endpoint, null)
        }
    }

    @Synchronized
    fun clearCredentials(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        wipeMemory()
    }

    @Synchronized
    fun wipeMemory() {
        decryptedKeyCache?.let {
            it.fill('\u0000')
        }
        decryptedKeyCache = null
    }
}
