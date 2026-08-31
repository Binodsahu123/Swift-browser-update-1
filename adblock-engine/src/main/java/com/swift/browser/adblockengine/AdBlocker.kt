package com.swift.browser.adblockengine

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import com.swift.browser.adblockengine.core.AdBlockEngine
import com.swift.browser.adblockengine.core.AdBlockExceptionManager
import com.swift.browser.adblockengine.core.AdBlockPolicy
import com.swift.browser.adblockengine.core.AdBlockRequestDecision
import com.swift.browser.adblockengine.core.AdBlockRuleEngine
import com.swift.browser.adblockengine.core.AdBlockWhitelistManager
import com.swift.browser.adblockengine.network.AdBlockEmptyResponseBuilder
import com.swift.browser.adblockengine.network.AdBlockThirdPartyDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap

object AdBlocker {
    var globalAdBlockEnabled: Boolean
        get() = AdBlockPolicy.isEasyListEnabled
        set(value) {
            AdBlockPolicy.isEasyListEnabled = value
        }

    var globalTrackersEnabled: Boolean
        get() = AdBlockPolicy.isEasyPrivacyEnabled
        set(value) {
            AdBlockPolicy.isEasyPrivacyEnabled = value
            AdBlockPolicy.trackerBlockingEnabled = value
        }

    // Backward compatibility property bridges
    val whitelistedSites: MutableSet<String> = object : AbstractMutableSet<String>() {
        override val size: Int get() = AdBlockWhitelistManager.getWhitelist().size
        override fun iterator(): MutableIterator<String> = AdBlockWhitelistManager.getWhitelist().toMutableSet().iterator()
        override fun add(element: String): Boolean {
            val ctx = AdBlockEngine.appContext
            if (ctx != null) AdBlockWhitelistManager.add(ctx, element)
            return true
        }
        override fun remove(element: String): Boolean {
            val ctx = AdBlockEngine.appContext
            if (ctx != null) AdBlockWhitelistManager.remove(ctx, element)
            return true
        }
        override fun contains(element: String): Boolean = AdBlockWhitelistManager.isWhitelisted(element)
    }

    val blockedSites: MutableSet<String> = object : AbstractMutableSet<String>() {
        override val size: Int get() = AdBlockExceptionManager.getBlacklist().size
        override fun iterator(): MutableIterator<String> = AdBlockExceptionManager.getBlacklist().toMutableSet().iterator()
        override fun add(element: String): Boolean {
            val ctx = AdBlockEngine.appContext
            if (ctx != null) AdBlockExceptionManager.add(ctx, element)
            return true
        }
        override fun remove(element: String): Boolean {
            val ctx = AdBlockEngine.appContext
            if (ctx != null) AdBlockExceptionManager.remove(ctx, element)
            return true
        }
        override fun contains(element: String): Boolean = AdBlockExceptionManager.isExplicitlyBlacklisted(element)
    }

    private val AD_HOSTS = setOf(
        "googleads.g.doubleclick.net",
        "ad.doubleclick.net",
        "adclick.g.doubleclick.net",
        "securepubads.g.doubleclick.net",
        "pagead2.googlesyndication.com",
        "tpc.googlesyndication.com",
        "googleadservices.com",
        "www.googleadservices.com",
        "adservice.google.com",
        "adservice.google.co.in",
        "partner.googleadservices.com",
        "s0.2mdn.net",
        "criteo.com",
        "casalemedia.com",
        "rubiconproject.com",
        "pubmatic.com",
        "openx.net",
        "appnexus.com",
        "adnxs.com",
        "advertising.com",
        "amazon-adsystem.com",
        "a-mo.net",
        "adtech.de",
        "adtechus.com",
        "taboola.com",
        "outbrain.com",
        "bidswitch.net",
        "moatads.com",
        "quantserve.com",
        "scorecardresearch.com"
    )

    fun init(context: Context) {
        AdBlockEngine.initialize(context)
    }

    fun savePreferences(context: Context) {
        // Managed canonical in AdBlockPreferenceStore
    }

    fun compileRules() {
        // Managed in FilterListManager
    }

    fun shouldBlock(url: String?, documentUrl: String?): Boolean {
        if (!globalAdBlockEnabled || url == null) return false
        val isThirdParty = AdBlockThirdPartyDetector.isThirdParty(url, documentUrl)
        val decision = AdBlockRuleEngine.evaluate(url, isThirdParty, null, documentUrl)
        return decision == AdBlockRequestDecision.BLOCK
    }

    fun getDomainName(url: String?): String? {
        if (url == null || url.startsWith("swift://") || url == "about:blank") return null
        return try {
            val host = Uri.parse(url).host?.lowercase() ?: ""
            if (host.startsWith("www.")) host.substring(4) else host
        } catch (e: Exception) {
            null
        }
    }

    suspend fun downloadBlocklists(context: Context): Int = withContext(Dispatchers.IO) {
        AD_HOSTS.size
    }

    fun downloadBlocklistsSync(context: Context): Int {
        return AD_HOSTS.size
    }

    fun createEmptyResponse(): WebResourceResponse {
        return AdBlockEmptyResponseBuilder.create("unknown", "") ?: WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
    }
}
