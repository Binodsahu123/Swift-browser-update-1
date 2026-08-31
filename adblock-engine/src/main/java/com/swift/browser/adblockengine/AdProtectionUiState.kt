package com.swift.browser.adblockengine

data class AdProtectionUiState(
    val globalAdBlockEnabled: Boolean = true,
    val globalTrackersEnabled: Boolean = true,
    val adblockWhitelist: Set<String> = emptySet(),
    val adblockBlacklist: Set<String> = emptySet(),
    val currentSite: String = "",
    val currentSiteProtected: Boolean = true,
    val currentSiteWhitelisted: Boolean = false,
    val currentSiteBlacklisted: Boolean = false,
    val blockedAdsCount: Int = 0,
    val blockedTrackersCount: Int = 0,
    val hiddenElementsCount: Int = 0,
    val isUpdatingBlocklists: Boolean = false,
    val blocklistRuleCount: Int = 0,
    val error: String? = null
)

data class BlocklistUpdateResult(
    val success: Boolean,
    val rulesDownloaded: Int,
    val listsUpdated: Int,
    val error: String? = null
)
