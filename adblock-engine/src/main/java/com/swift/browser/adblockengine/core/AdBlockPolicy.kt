package com.swift.browser.adblockengine.core

/**
 * Stores the runtime policy configuration settings for ad and tracker blocking behavior.
 */
object AdBlockPolicy {
    var isEasyListEnabled = true
    var isEasyPrivacyEnabled = true
    var isCustomFiltersEnabled = true
    var autoUpdateEnabled = true
    var wifiOnlyUpdate = false
    var aggressiveMode = false
    var cosmeticFilteringEnabled = true
    var trackerBlockingEnabled = true
}
