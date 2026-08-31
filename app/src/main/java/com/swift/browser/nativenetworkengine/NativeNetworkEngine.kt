package com.swift.browser.nativenetworkengine

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.StringReader

object NativeNetworkEngine {
    private const val TAG = "NativeNetworkEngine"
    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("extension_native_parser")
            isNativeLoaded = true
            Log.i(TAG, "Native network optimization layer loaded successfully.")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Native library 'extension_native_parser' not loaded. Falling back to robust JVM implementation.")
            isNativeLoaded = false
        }
    }

    /**
     * Parses HTTP raw headers natively to prevent heap allocation overhead.
     */
    fun parseRequestHeaders(rawHeaders: String): String {
        if (isNativeLoaded) {
            try {
                return nativeParseRequestHeaders(rawHeaders)
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Native parse headers failed; executing fallback", e)
            }
        }
        return fallbackParseRequestHeaders(rawHeaders)
    }

    /**
     * Analyzes response bodies natively for potential security concerns or tracker strings.
     */
    fun analyzeResponseBody(body: String, mimeType: String): String {
        if (isNativeLoaded) {
            try {
                return nativeAnalyzeResponseBody(body, mimeType)
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Native analyze body failed; executing fallback", e)
            }
        }
        return fallbackAnalyzeResponseBody(body, mimeType)
    }

    /**
     * Aggregates live traffic transfers using lightweight, zero-allocation native metrics trackers.
     */
    fun trackTraffic(host: String, bytesTransferred: Long): String {
        if (isNativeLoaded) {
            try {
                return nativeTrackTraffic(host, bytesTransferred)
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Native traffic tracker failed; executing fallback", e)
            }
        }
        return fallbackTrackTraffic(host, bytesTransferred)
    }

    private external fun nativeParseRequestHeaders(rawHeaders: String): String
    private external fun nativeAnalyzeResponseBody(body: String, mimeType: String): String
    private external fun nativeTrackTraffic(host: String, bytes: Long): String

    private fun fallbackParseRequestHeaders(rawHeaders: String): String {
        val result = JSONObject()
        try {
            val headersMap = JSONObject()
            val reader = BufferedReader(StringReader(rawHeaders))
            var line = reader.readLine()
            var count = 0
            while (line != null) {
                if (line.isNotBlank()) {
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2) {
                        headersMap.put(parts[0].trim(), parts[1].trim())
                        count++
                    }
                }
                line = reader.readLine()
            }
            result.put("status", "success")
            result.put("parsedCount", count)
            result.put("headers", headersMap)
            result.put("parserMode", "fallback_jvm")
        } catch (e: Exception) {
            Log.e(TAG, "Fallback headers parser encountered error", e)
            result.put("status", "error")
            result.put("message", e.message)
        }
        return result.toString()
    }

    private fun fallbackAnalyzeResponseBody(body: String, mimeType: String): String {
        val analysis = JSONObject()
        try {
            var trackerFound = false
            val suspicionFactors = mutableListOf<String>()

            if (mimeType.contains("javascript", ignoreCase = true) || mimeType.contains("html", ignoreCase = true)) {
                // Look for suspect tracking scripts or analytics snippets
                val trackingSignatures = listOf("google-analytics.com", "fbq('track'", "eval(atob(", "coinhive.min.js")
                for (sig in trackingSignatures) {
                    if (body.contains(sig)) {
                        trackerFound = true
                        suspicionFactors.add(sig)
                    }
                }
            }

            analysis.put("trackerDetected", trackerFound)
            analysis.put("matches", org.json.JSONArray(suspicionFactors))
            analysis.put("bodySize", body.length)
            analysis.put("safetyRating", if (suspicionFactors.isEmpty()) "SAFE" else "SUSPICIOUS")
            analysis.put("engine", "fallback_jvm")
        } catch (e: Exception) {
            analysis.put("status", "error")
            analysis.put("error", e.message)
        }
        return analysis.toString()
    }

    private fun fallbackTrackTraffic(host: String, bytesTransferred: Long): String {
        val statsObj = JSONObject().apply {
            put("host", host)
            put("bytesTransferred", bytesTransferred)
            put("timestamp", System.currentTimeMillis())
            put("overheadSavedBytes", (bytesTransferred * 0.05).toLong()) // Mock overhead metric
            put("status", "RECORDED")
            put("engine", "fallback_jvm")
        }
        return statsObj.toString()
    }
}
