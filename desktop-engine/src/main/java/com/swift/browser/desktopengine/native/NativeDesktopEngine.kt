package com.swift.browser.desktopengine.native

import android.util.Log

object NativeDesktopEngine {
    private const val TAG = "NativeDesktopEngine"

    init {
        try {
            System.loadLibrary("native_media_bridge")
            Log.i(TAG, "Native Desktop Engine successfully loaded native library!")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load library: native_media_bridge", e)
        }
    }

    // High-speed URL rewrite engine
    external fun nativeRewriteUrl(url: String, toDesktop: Boolean): String

    // Speed-optimized calculation of screen scaling grid sizes
    external fun nativeCalculateScale(containerWidth: Int, containerHeight: Int): Float

    // High-performance site compatibility scorer
    external fun nativeEvaluateCompatibility(host: String): Int
}
