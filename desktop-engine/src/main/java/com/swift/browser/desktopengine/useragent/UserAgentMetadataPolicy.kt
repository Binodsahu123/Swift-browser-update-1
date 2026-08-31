package com.swift.browser.desktopengine.useragent

import android.webkit.WebView
import com.swift.browser.desktopengine.api.DesktopMode

enum class UserAgentControlStatus {
    UA_CONTROLLED,
    UA_CH_CONTROLLED,
    UA_CH_UNAVAILABLE
}

data class BrandVersion(
    val brand: String,
    val version: String
)

data class UserAgentMetadataProfile(
    val platform: String,
    val platformVersion: String,
    val architecture: String,
    val model: String,
    val mobile: Boolean,
    val brandList: List<BrandVersion>,
    val uaControlStatus: UserAgentControlStatus = UserAgentControlStatus.UA_CONTROLLED,
    val uaChStatus: UserAgentControlStatus = UserAgentControlStatus.UA_CH_UNAVAILABLE
)

object UserAgentMetadataPolicy {

    fun getProfile(mode: DesktopMode, context: android.content.Context? = null): UserAgentMetadataProfile {
        val (version, major) = context?.let { WebViewVersionDetector.detect(it) } ?: Pair("126.0.0.0", 126)
        val majorStr = major.toString()

        return if (mode == DesktopMode.DESKTOP) {
            UserAgentMetadataProfile(
                platform = "Windows",
                platformVersion = "10.0.0",
                architecture = "x86",
                model = "",
                mobile = false,
                brandList = listOf(
                    BrandVersion("Chromium", majorStr),
                    BrandVersion("Google Chrome", majorStr),
                    BrandVersion("Not-A.Brand", "24")
                ),
                uaControlStatus = UserAgentControlStatus.UA_CONTROLLED,
                uaChStatus = UserAgentControlStatus.UA_CH_UNAVAILABLE
            )
        } else {
            UserAgentMetadataProfile(
                platform = "Android",
                platformVersion = "10.0.0",
                architecture = "",
                model = "Mobile",
                mobile = true,
                brandList = listOf(
                    BrandVersion("Chromium", majorStr),
                    BrandVersion("Google Chrome", majorStr),
                    BrandVersion("Not-A.Brand", "24")
                ),
                uaControlStatus = UserAgentControlStatus.UA_CONTROLLED,
                uaChStatus = UserAgentControlStatus.UA_CH_UNAVAILABLE
            )
        }
    }

    fun getProfile(mode: DesktopMode): UserAgentMetadataProfile {
        return getProfile(mode, null)
    }

    fun applyMetadata(webView: WebView, mode: DesktopMode) {
        // Native Android WebView does not expose Sec-CH-UA HTTP headers or NavigatorUAData override API natively.
        // We set the UserAgentString on WebSettings (UA_CONTROLLED), which is the official Android WebView mechanism.
        // We do not fabricate navigator.userAgentData or Sec-CH-UA values if Android WebView does not expose safe control (UA_CH_UNAVAILABLE).
    }
}

