package com.swift.browser.desktopengine.useragent

import android.content.Context
import com.swift.browser.desktopengine.api.DesktopMode

object UserAgentManager {
    private var detectedWebViewVersion: String? = null

    fun initialize(context: Context) {
        val (version, _) = WebViewVersionDetector.detect(context)
        if (version.isNotEmpty()) {
            detectedWebViewVersion = version
        }
    }

    fun getWebViewVersion(context: Context? = null): String {
        if (context != null) {
            val (version, _) = WebViewVersionDetector.detect(context)
            if (version.isNotEmpty()) return version
        }
        return detectedWebViewVersion ?: "126.0.0.0"
    }

    fun getDesktopUserAgent(host: String = "", context: Context? = null): String {
        val version = getWebViewVersion(context)
        return WebCompatibilityMatrix.resolveUserAgent(host, DesktopMode.DESKTOP, version)
    }

    fun getMobileUserAgent(context: Context? = null): String {
        val version = getWebViewVersion(context)
        return WebCompatibilityMatrix.resolveUserAgent("", DesktopMode.MOBILE, version)
    }

    fun getUserAgent(host: String, isDesktop: Boolean, context: Context? = null): String {
        return if (isDesktop) {
            getDesktopUserAgent(host, context)
        } else {
            getMobileUserAgent(context)
        }
    }
}

