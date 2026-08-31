package com.swift.browser.adblockengine

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import android.webkit.WebView
import com.swift.browser.adblockengine.core.AdBlockEngine
import com.swift.browser.adblockengine.core.AdBlockExceptionManager
import com.swift.browser.adblockengine.core.AdBlockPolicy
import com.swift.browser.adblockengine.core.AdBlockStatsManager
import com.swift.browser.adblockengine.core.AdBlockWhitelistManager
import com.swift.browser.adblockengine.cosmetic.ElementHidingEngine
import com.swift.browser.adblockengine.network.AdBlockRequestInterceptor
import com.swift.browser.adblockengine.storage.AdBlockPreferenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class AdProtectionEngineApi private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _uiState = MutableStateFlow(AdProtectionUiState())
    val uiState: StateFlow<AdProtectionUiState> = _uiState.asStateFlow()

    private val tabBlockedCounts = ConcurrentHashMap<String, Int>()
    private val cosmeticInjectedPages = ConcurrentHashMap<String, String>() // tabId -> lastInjectedUrl

    init {
        initialize(context)
    }

    fun initialize(ctx: Context) {
        val appCtx = ctx.applicationContext
        AdBlockEngine.initialize(appCtx)
        NetworkSnifferEngine.initialize(appCtx)

        val gAdBlock = AdBlockPreferenceStore.getBoolean(appCtx, "easylist_enabled", true)
        val gTrackers = AdBlockPreferenceStore.getBoolean(appCtx, "easyprivacy_enabled", true)
        val whitelist = AdBlockWhitelistManager.getWhitelist()
        val blacklist = AdBlockExceptionManager.getBlacklist()

        AdBlockPolicy.isEasyListEnabled = gAdBlock
        AdBlockPolicy.isEasyPrivacyEnabled = gTrackers
        AdBlockPolicy.trackerBlockingEnabled = gTrackers

        _uiState.value = _uiState.value.copy(
            globalAdBlockEnabled = gAdBlock,
            globalTrackersEnabled = gTrackers,
            adblockWhitelist = whitelist,
            adblockBlacklist = blacklist,
            blockedAdsCount = AdBlockStatsManager.getAdsBlocked(),
            blockedTrackersCount = AdBlockStatsManager.getTrackersBlocked(),
            hiddenElementsCount = AdBlockStatsManager.getCosmeticHides(),
            blocklistRuleCount = AdBlocker.downloadBlocklistsSync(appCtx)
        )
    }

    fun setGlobalAdBlockEnabled(enabled: Boolean) {
        AdBlockEngine.setEnabled(context, enabled)
        _uiState.value = _uiState.value.copy(globalAdBlockEnabled = enabled)
    }

    fun setGlobalTrackersEnabled(enabled: Boolean) {
        AdBlockEngine.updatePolicy(
            context,
            easyList = AdBlockPolicy.isEasyListEnabled,
            easyPrivacy = enabled,
            autoUpdate = AdBlockPolicy.autoUpdateEnabled,
            wifiOnly = AdBlockPolicy.wifiOnlyUpdate
        )
        _uiState.value = _uiState.value.copy(globalTrackersEnabled = enabled)
    }

    fun toggleForSite(url: String) {
        val domain = getDomainName(url) ?: return
        if (AdBlockWhitelistManager.isWhitelisted(domain)) {
            removeWhitelistedSite(domain)
        } else {
            addWhitelistedSite(domain)
        }
    }

    fun addWhitelistedSite(domain: String) {
        val clean = cleanDomain(domain)
        if (clean.isBlank()) return
        AdBlockWhitelistManager.add(context, clean)
        val updated = AdBlockWhitelistManager.getWhitelist()
        _uiState.value = _uiState.value.copy(adblockWhitelist = updated)
        updateSiteStateForCurrentUrl(_uiState.value.currentSite)
    }

    fun removeWhitelistedSite(domain: String) {
        val clean = cleanDomain(domain)
        AdBlockWhitelistManager.remove(context, clean)
        val updated = AdBlockWhitelistManager.getWhitelist()
        _uiState.value = _uiState.value.copy(adblockWhitelist = updated)
        updateSiteStateForCurrentUrl(_uiState.value.currentSite)
    }

    fun addBlockedSite(domain: String) {
        val clean = cleanDomain(domain)
        if (clean.isBlank()) return
        AdBlockExceptionManager.add(context, clean)
        val updated = AdBlockExceptionManager.getBlacklist()
        _uiState.value = _uiState.value.copy(adblockBlacklist = updated)
        updateSiteStateForCurrentUrl(_uiState.value.currentSite)
    }

    fun removeBlockedSite(domain: String) {
        val clean = cleanDomain(domain)
        AdBlockExceptionManager.remove(context, clean)
        val updated = AdBlockExceptionManager.getBlacklist()
        _uiState.value = _uiState.value.copy(adblockBlacklist = updated)
        updateSiteStateForCurrentUrl(_uiState.value.currentSite)
    }

    fun updateSiteStateForCurrentUrl(url: String) {
        val domain = getDomainName(url) ?: ""
        val isWhitelisted = domain.isNotEmpty() && AdBlockWhitelistManager.isWhitelisted(domain)
        val isBlacklisted = domain.isNotEmpty() && AdBlockExceptionManager.isExplicitlyBlacklisted(domain)
        val isProtected = AdBlockPolicy.isEasyListEnabled && !isWhitelisted

        _uiState.value = _uiState.value.copy(
            currentSite = url,
            currentSiteProtected = isProtected,
            currentSiteWhitelisted = isWhitelisted,
            currentSiteBlacklisted = isBlacklisted
        )
    }

    fun updateBlocklists(onResult: ((BlocklistUpdateResult) -> Unit)? = null) {
        _uiState.value = _uiState.value.copy(isUpdatingBlocklists = true)
        scope.launch(Dispatchers.IO) {
            try {
                val size = AdBlocker.downloadBlocklistsSync(context)
                val result = BlocklistUpdateResult(
                    success = true,
                    rulesDownloaded = size,
                    listsUpdated = 1
                )
                scope.launch(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isUpdatingBlocklists = false,
                        blocklistRuleCount = size,
                        error = null
                    )
                    onResult?.invoke(result)
                }
            } catch (e: Exception) {
                val result = BlocklistUpdateResult(
                    success = false,
                    rulesDownloaded = 0,
                    listsUpdated = 0,
                    error = e.message
                )
                scope.launch(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isUpdatingBlocklists = false,
                        error = e.message
                    )
                    onResult?.invoke(result)
                }
            }
        }
    }

    fun onPageStarted(tabId: String, view: WebView?, url: String?) {
        if (url != null) {
            cosmeticInjectedPages.remove(tabId)
            updateSiteStateForCurrentUrl(url)
        }
    }

    fun onPageCommitVisible(tabId: String, view: WebView?, url: String?) {
        if (view != null && url != null && !url.startsWith("swift://") && !url.startsWith("about:") && !url.startsWith("file://")) {
            injectCosmeticFilters(tabId, view, url)
        }
    }

    fun onPageFinished(tabId: String, view: WebView?, url: String?) {
        if (view != null && url != null && !url.startsWith("swift://") && !url.startsWith("about:") && !url.startsWith("file://")) {
            if (cosmeticInjectedPages[tabId] != url) {
                injectCosmeticFilters(tabId, view, url)
            }
        }
    }

    private fun injectCosmeticFilters(tabId: String, view: WebView, url: String) {
        val cssHideJs = ElementHidingEngine.getHidingJavascript(url)
        if (cssHideJs.isNotEmpty()) {
            cosmeticInjectedPages[tabId] = url
            view.post {
                try {
                    view.evaluateJavascript(cssHideJs, null)
                } catch (e: Exception) {
                    // Ignore injection failure
                }
            }
        }
    }

    fun onAdBlocked(tabId: String) {
        val count = (tabBlockedCounts[tabId] ?: 0) + 1
        tabBlockedCounts[tabId] = count
        AdBlockStatsManager.recordAdBlocked()
        _uiState.value = _uiState.value.copy(
            blockedAdsCount = AdBlockStatsManager.getAdsBlocked()
        )
    }

    fun getTabBlockedAdsCount(tabId: String): Int {
        return tabBlockedCounts[tabId] ?: 0
    }

    fun shouldInterceptRequest(ctx: Context, urlStr: String?, documentUrl: String?): WebResourceResponse? {
        if (!AdBlockPolicy.isEasyListEnabled) return null
        return AdBlockRequestInterceptor.shouldInterceptRequest(ctx, urlStr, documentUrl)
    }

    private fun cleanDomain(domain: String): String {
        return domain.trim().lowercase().removePrefix("www.")
    }

    fun getDomainName(url: String?): String? {
        return AdBlocker.getDomainName(url)
    }

    companion object {
        @Volatile
        private var instance: AdProtectionEngineApi? = null

        fun getInstance(context: Context): AdProtectionEngineApi {
            return instance ?: synchronized(this) {
                instance ?: AdProtectionEngineApi(context.applicationContext).also { instance = it }
            }
        }
    }
}
