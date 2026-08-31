package com.swift.browser.adblockengine.core

/**
 * Enumerates the distinct high-level state profiles of the AdBlock matching engine.
 */
enum class AdBlockState {
    DISABLED,
    LOADING,
    ACTIVE,
    UPDATING,
    ERROR,
    OFFLINE_FALLBACK,
    WHITELISTED,
    PAUSED_FOR_SITE
}
