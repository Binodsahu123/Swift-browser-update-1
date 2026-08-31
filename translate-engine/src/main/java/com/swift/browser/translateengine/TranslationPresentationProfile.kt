package com.swift.browser.translateengine

enum class TranslationPresentationProfile {
    COMPACT_MOBILE,
    COMPACT_DESKTOP,
    TABLET_COMPACT
}

data class PageRenderContext(
    val innerWidth: Int = 0,
    val innerHeight: Int = 0,
    val clientWidth: Int = 0,
    val clientHeight: Int = 0,
    val devicePixelRatio: Float = 1.0f,
    val isDesktopModeRequested: Boolean = false,
    val profile: TranslationPresentationProfile = TranslationPresentationProfile.COMPACT_MOBILE
)
