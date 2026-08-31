package com.swift.browser.desktopengine.useragent

import android.content.Context
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * Capture of the real WebView / Chromium package characteristics on the user's device.
 */
data class WebViewRuntimeProfile(
    val packageName: String,
    val versionName: String,
    val versionCode: String,
    val webViewImplementationPackage: String,
    val chromiumMajor: Int,
    val chromiumVersionRaw: String,
    val apiLevel: Int,
    val brand: String,
    val desktopMode: Boolean,
    val mobileMode: Boolean,
    val webRtcSupported: Boolean,
    val mediaCaptureSupported: Boolean,
    val screenCaptureSupported: Boolean,
    val mediaRecorderSupported: Boolean,
    val webAudioSupported: Boolean,
    val webglSupported: Boolean,
    val uaOverrideSupported: Boolean,
    val uaClientHintsControlSupported: Boolean,
    val uaControlStatus: UserAgentControlStatus = UserAgentControlStatus.UA_CONTROLLED,
    val uaChStatus: UserAgentControlStatus = UserAgentControlStatus.UA_CH_UNAVAILABLE
) {
    // Maintain old compatibility properties
    val webViewVersion: String get() = versionName
    val platform: String get() = "Android"
    val mobile: Boolean get() = mobileMode
    val jsSupport: Boolean get() = true
    val webrtcSupport: Boolean get() = webRtcSupported
    val mediaCaptureSupport: Boolean get() = mediaCaptureSupported
    val uaControlled: Boolean get() = uaOverrideSupported
    val uaClientHintsControlled: Boolean get() = uaClientHintsControlSupported

    companion object {
        fun create(context: Context): WebViewRuntimeProfile {
            return create(context, false)
        }

        fun create(context: Context, isDesktop: Boolean): WebViewRuntimeProfile {
            val details = WebViewVersionDetector.detectDetails(context)
            val hasValidWebViewPackage = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WebView.getCurrentWebViewPackage() != null
                } else {
                    true
                }
            } catch (_: Exception) {
                true
            }
            val isWebRtcCapable = hasValidWebViewPackage && details.versionName.isNotEmpty() && details.majorVersion >= 36
            
            return WebViewRuntimeProfile(
                packageName = details.packageName,
                versionName = details.versionName,
                versionCode = details.versionCode,
                webViewImplementationPackage = details.packageName,
                chromiumMajor = details.majorVersion,
                chromiumVersionRaw = details.versionName,
                apiLevel = Build.VERSION.SDK_INT,
                brand = Build.BRAND ?: "UNKNOWN",
                desktopMode = isDesktop,
                mobileMode = !isDesktop,
                webRtcSupported = isWebRtcCapable,
                mediaCaptureSupported = true,
                screenCaptureSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP,
                mediaRecorderSupported = true,
                webAudioSupported = true,
                webglSupported = true,
                uaOverrideSupported = true,
                uaClientHintsControlSupported = false,
                uaControlStatus = UserAgentControlStatus.UA_CONTROLLED,
                uaChStatus = UserAgentControlStatus.UA_CH_UNAVAILABLE
            )
        }
    }
}

/**
 * Utility to reliably detect system WebView / Chromium versions.
 */
object WebViewVersionDetector {
    data class DetectedDetails(
        val packageName: String,
        val versionName: String,
        val versionCode: String,
        val majorVersion: Int
    )

    fun detectDetails(context: Context): DetectedDetails {
        var pkg = "UNKNOWN"
        var version = ""
        var code = "UNKNOWN"

        // 1. Check native system WebView package manager info (API 26+)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val packageInfo = WebView.getCurrentWebViewPackage()
                if (packageInfo != null) {
                    pkg = packageInfo.packageName ?: "UNKNOWN"
                    version = packageInfo.versionName ?: ""
                    code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode.toString()
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.versionCode.toString()
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback Package queries if native package info is null or fails
        if (pkg == "UNKNOWN" || version.isEmpty()) {
            val knownPackages = listOf(
                "com.google.android.webview",
                "com.android.chrome",
                "com.android.webview",
                "com.google.android.webview.beta",
                "com.google.android.webview.dev",
                "com.google.android.webview.canary"
            )
            for (kp in knownPackages) {
                try {
                    val pi = context.packageManager.getPackageInfo(kp, 0)
                    pkg = kp
                    version = pi.versionName ?: ""
                    code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        pi.longVersionCode.toString()
                    } else {
                        @Suppress("DEPRECATION")
                        pi.versionCode.toString()
                    }
                    break
                } catch (_: Exception) {}
            }
        }

        // 2. Extract from standard WebView default User Agent
        if (version.isEmpty()) {
            try {
                val defaultUa = WebSettings.getDefaultUserAgent(context)
                val pattern = Regex("Chrome/([\\d\\.]+)")
                val match = pattern.find(defaultUa)
                match?.groupValues?.get(1)?.let {
                    version = it
                }
            } catch (_: Exception) {}
        }

        // Safe fallback if neither succeeded
        if (version.isEmpty()) {
            version = "126.0.0.0"
        }

        val major = try {
            version.split('.').firstOrNull()?.toInt() ?: 126
        } catch (_: Exception) {
            126
        }

        return DetectedDetails(pkg, version, code, major)
    }

    fun detect(context: Context): Pair<String, Int> {
        val details = detectDetails(context)
        return Pair(details.versionName, details.majorVersion)
    }
}
