package com.swift.browser.extensionengine

import android.util.Log

object NativeExtensionEngine {
    private const val TAG = "NativeExtensionEngine"
    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("extension_native_parser")
            isNativeLoaded = true
            try {
                Log.i(TAG, "Successfully bound high-performance C/C++ extension_native_parser pipeline.")
            } catch (_: Throwable) {}
        } catch (e: UnsatisfiedLinkError) {
            try {
                Log.w(TAG, "Native library 'extension_native_parser' not loaded. Falling back to robust JVM implementation.")
            } catch (_: Throwable) {}
            isNativeLoaded = false
        }
    }

    /**
     * Generates a 32-character alphabetic Chrome Extension ID.
     * Falls back to JVM implementation if JNI link is unavailable.
     */
    fun generateExtensionId(name: String): String {
        if (isNativeLoaded) {
            try {
                val nativeId = nativeGenerateExtensionId(name)
                if (nativeId.isNotBlank()) return nativeId
            } catch (e: Throwable) {
                Log.w(TAG, "Native extension ID generation failed, falling back to JVM", e)
            }
        }
        return jvmGenerateExtensionId(name)
    }

    /**
     * Verifies CRX / ZIP container format.
     * Returns:
     *   -1 if invalid header.
     *    0 if raw ZIP header.
     *    2 if Chrome CRX v2.
     *    3 if Chrome CRX v3.
     */
    fun verifyCrxHeader(bytes: ByteArray): Int {
        if (isNativeLoaded) {
            try {
                return nativeVerifyCrxHeader(bytes)
            } catch (e: Throwable) {
                Log.e(TAG, "Native CRX verification failed, falling back", e)
            }
        }
        return jvmVerifyCrxHeader(bytes)
    }

    /**
     * Safety check preventing directory traversal exploitation files layout.
     */
    fun isSafeRelativePath(relativePath: String): Boolean {
        return PathSanitizer.isSafePath(relativePath)
    }

    // JVM Fallback Implementations
    private fun jvmGenerateExtensionId(name: String): String {
        return ExtensionIdGenerator.generateOrionLocalIdentity(name, name).id
    }

    private fun jvmVerifyCrxHeader(bytes: ByteArray): Int {
        if (bytes.size < 12) return -1
        // Check magic "Cr24"
        if (bytes[0] == 0x43.toByte() && bytes[1] == 0x72.toByte() && 
            bytes[2] == 0x32.toByte() && bytes[3] == 0x34.toByte()) {
            val version = (bytes[4].toInt() and 0xFF) or
                          ((bytes[5].toInt() and 0xFF) shl 8) or
                          ((bytes[6].toInt() and 0xFF) shl 16) or
                          ((bytes[7].toInt() and 0xFF) shl 24)
            return version
        }
        
        // Check standard ZIP signature "PK\u0003\u0004"
        if (bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()) {
            return 0
        }
        return -1
    }

    // JNI Native Methods declarations
    private external fun nativeGenerateExtensionId(name: String): String
    private external fun nativeVerifyCrxHeader(bytes: ByteArray): Int
    private external fun nativeIsSafeRelativePath(path: String): Boolean
}
