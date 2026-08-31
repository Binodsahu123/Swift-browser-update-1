package com.swift.browser.adblockengine.network

import android.content.Context
import android.webkit.WebResourceResponse
import com.swift.browser.adblockengine.core.AdBlockRequestDecision
import com.swift.browser.adblockengine.core.AdBlockRuleEngine
import com.swift.browser.adblockengine.core.AdBlockStatsManager
import com.swift.browser.adblockengine.core.AdBlockDiagnostics
import java.io.ByteArrayInputStream

/**
 * Main WebView Interceptor interface that parses request metadata and decides block outcomes.
 */
object AdBlockRequestInterceptor {

    fun shouldInterceptRequest(
        context: Context,
        url: String?,
        documentUrl: String?
    ): WebResourceResponse? {
        if (url == null) return null

        if (!AdBlockNetworkPolicy.shouldProcessRequest(url)) {
            return null
        }
        
        // Quick check for obvious ads that should be instantly blocked
        val urlLower = url.lowercase()
        val docUrlLower = documentUrl?.lowercase() ?: ""
        
        // Skip fast-path ad blocking if on YouTube to prevent breaking the web app
        val isYouTube = docUrlLower.contains("youtube.com") || docUrlLower.contains("youtu.be")
        
        if (!isYouTube && (urlLower.contains("/api/stats/ads") ||
            urlLower.contains("/pagead/") ||
            urlLower.contains("googleads.g.doubleclick.net") ||
            urlLower.contains("ad.doubleclick.net") ||
            urlLower.contains("doubleclick.net") ||
            urlLower.contains("/ad_break") ||
            urlLower.contains("adformat=") ||
            urlLower.contains("ad_type=") ||
            urlLower.contains("advideo=") ||
            urlLower.contains("googleadservices.com") ||
            urlLower.contains("googlesyndication.com"))) {
            AdBlockStatsManager.recordAdBlocked()
            AdBlockDiagnostics.logEvent("Fast-Path Block", "Blocked explicit ad endpoint: $url")
            return AdBlockEmptyResponseBuilder.create("unknown", url)
        }

        val isThirdParty = AdBlockThirdPartyDetector.isThirdParty(url, documentUrl)
        val resourceType = AdBlockRequestClassifier.classify(url)

        // Protect ALL website logos, icons, favicons, avatars, and image assets across all websites
        if (isEssentialWebsiteVisualAsset(urlLower, resourceType)) {
            if (!isKnownAdServerHost(urlLower)) {
                return null // Allow essential website visual assets immediately
            }
        }

        val decision = AdBlockRuleEngine.evaluate(url, isThirdParty, resourceType, documentUrl)
        if (decision == AdBlockRequestDecision.BLOCK) {
            val isTracker = AdBlockTrackerDetector.isTracker(url)
            if (isTracker) {
                AdBlockStatsManager.recordTrackerBlocked()
                AdBlockDiagnostics.logEvent("Tracker Blocked", "Blocked Tracker URL: $url")
            } else {
                AdBlockStatsManager.recordAdBlocked()
                AdBlockDiagnostics.logEvent("Ad Blocked", "Blocked Ad URL: $url")
            }
            return AdBlockEmptyResponseBuilder.create(resourceType, url)
        }

        return null
    }

    private fun isEssentialWebsiteVisualAsset(urlLower: String, resourceType: String?): Boolean {
        if (resourceType == "image") return true
        if (urlLower.contains(".ico") ||
            urlLower.contains(".png") ||
            urlLower.contains(".jpg") ||
            urlLower.contains(".jpeg") ||
            urlLower.contains(".svg") ||
            urlLower.contains(".webp") ||
            urlLower.contains(".gif") ||
            urlLower.contains("favicon") ||
            urlLower.contains("logo") ||
            urlLower.contains("avatar") ||
            urlLower.contains("s2/favicons") ||
            urlLower.contains("google.com/s2/favicons") ||
            urlLower.contains("/assets/") ||
            urlLower.contains("/images/") ||
            urlLower.contains("/icons/")) {
            return true
        }
        return false
    }

    private fun isKnownAdServerHost(urlLower: String): Boolean {
        return urlLower.contains("doubleclick.net") ||
                urlLower.contains("googleads") ||
                urlLower.contains("pagead") ||
                urlLower.contains("adservice.google") ||
                urlLower.contains("adnxs.com") ||
                urlLower.contains("taboola.com") ||
                urlLower.contains("outbrain.com") ||
                urlLower.contains("pubmatic.com") ||
                urlLower.contains("rubiconproject.com") ||
                urlLower.contains("amazon-adsystem.com") ||
                urlLower.contains("casalemedia.com") ||
                urlLower.contains("popads.net")
    }
}
