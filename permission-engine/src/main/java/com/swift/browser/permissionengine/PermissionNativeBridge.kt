package com.swift.browser.permissionengine

import android.util.Log

enum class NativeBridgeLookupResult {
    NOT_FOUND,
    UNAVAILABLE,
    ERROR,
    ALLOW,
    BLOCK
}

object PermissionNativeBridge {
    private const val TAG = "PermissionNativeBridge"
    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("extension_native_parser")
            isNativeLoaded = true
            Log.i(TAG, "Native C++ Permission engine successfully loaded and linked.")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Native library 'extension_native_parser' missing, running JVM fallback mode.")
            isNativeLoaded = false
        }
    }

    fun matchOrigin(origin: String, pattern: String): Boolean {
        if (isNativeLoaded) {
            try {
                return nativeMatchOrigin(origin, pattern)
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "JNI matchOrigin failed, falling back to JVM", e)
            }
        }
        return jvmMatchOrigin(origin, pattern)
    }

    fun normalizeDomain(domain: String): String {
        if (isNativeLoaded) {
            try {
                return nativeNormalizeDomain(domain)
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "JNI normalizeDomain failed, falling back to JVM", e)
            }
        }
        return jvmNormalizeDomain(domain)
    }

    fun permissionLookup(origin: String, permissionType: String): NativeBridgeLookupResult {
        if (!isNativeLoaded) return NativeBridgeLookupResult.UNAVAILABLE
        return try {
            val result = nativePermissionLookup(origin, permissionType)
            when (result?.uppercase()) {
                "ALLOW", "ALLOW_ALWAYS", "ALLOW_ONCE" -> NativeBridgeLookupResult.ALLOW
                "BLOCK", "DENY" -> NativeBridgeLookupResult.BLOCK
                "NOT_FOUND", null -> NativeBridgeLookupResult.NOT_FOUND
                "UNAVAILABLE" -> NativeBridgeLookupResult.UNAVAILABLE
                else -> NativeBridgeLookupResult.ERROR
            }
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "JNI permissionLookup UnsatisfiedLinkError", e)
            NativeBridgeLookupResult.UNAVAILABLE
        } catch (e: Exception) {
            Log.e(TAG, "JNI permissionLookup Exception", e)
            NativeBridgeLookupResult.ERROR
        }
    }

    fun permissionCache(origin: String, permissionType: String, state: String) {
        if (isNativeLoaded) {
            try {
                nativePermissionCache(origin, permissionType, state)
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "JNI permissionCache failed, skipping", e)
            }
        }
    }

    // Native specifications
    private external fun nativeMatchOrigin(origin: String, pattern: String): Boolean
    private external fun nativeNormalizeDomain(domain: String): String
    private external fun nativePermissionLookup(origin: String, permissionType: String): String?
    private external fun nativePermissionCache(origin: String, permissionType: String, state: String)

    // JVM robust fallbacks
    private fun jvmMatchOrigin(origin: String, pattern: String): Boolean {
        if (pattern == "*") return true
        val cleanOrigin = origin.trim().lowercase().removePrefix("https://").removePrefix("http://").removeSuffix("/")
        val cleanPattern = pattern.trim().lowercase().removePrefix("https://").removePrefix("http://").removeSuffix("/")
        if (cleanPattern.startsWith("*.")) {
            val suffix = cleanPattern.substring(2)
            return cleanOrigin.endsWith(suffix)
        }
        return cleanOrigin == cleanPattern
    }

    private fun jvmNormalizeDomain(domain: String): String {
        var clean = domain.trim().lowercase()
        if (clean.contains("://")) {
            val idx = clean.indexOf("://")
            clean = clean.substring(idx + 3)
        }
        val slashIdx = clean.indexOf("/")
        if (slashIdx != -1) {
            clean = clean.substring(0, slashIdx)
        }
        if (clean.startsWith("www.")) {
            clean = clean.substring(4)
        }
        return clean
    }
}
