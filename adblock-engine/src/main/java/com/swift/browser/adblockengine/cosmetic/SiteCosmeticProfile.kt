package com.swift.browser.adblockengine.cosmetic

/**
 * Encapsulates custom element hiding rules specified on a per-site basis.
 */
data class SiteCosmeticProfile(
    val domain: String,
    val selectors: List<String>,
    val customCSS: String? = null
)
