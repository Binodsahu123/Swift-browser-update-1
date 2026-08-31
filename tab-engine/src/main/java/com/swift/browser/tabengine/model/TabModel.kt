package com.swift.browser.tabengine.model

import android.graphics.Bitmap

data class TabModel(
    val id: String,
    val readerModeAvailable: Boolean = false,
    val url: String = "swift://newtab",
    val title: String = "New Tab",
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val lastActiveTime: Long = System.currentTimeMillis(),
    val screenshot: Bitmap? = null,
    val faviconUrl: String? = null,
    val favicon: Bitmap? = null,
    val isWebViewDestroyed: Boolean = false,
    
    val isIncognito: Boolean = false,
    val isPrivate: Boolean = isIncognito,
    val privateSessionId: String? = null,
    val groupId: String? = null,
    val groupName: String? = null,
    val groupColor: Long? = null,
    val blockedAdsCount: Int = 0,
    val hasLoadedSuccessfully: Boolean = false,
    val parentTabId: String? = null,
    val showTranslateBar: Boolean = false,
    val isPageTranslated: Boolean = false,
    val translateTargetLang: String = "English",
    val translateTargetLangCode: String = "en",
    
    // TabEngine specific ones
    val freezeState: Int = 0, // 0 = active, 1 = frozen, 2 = suspended
    val scrollPosition: Int = 0,
    val previewId: String? = null
)
