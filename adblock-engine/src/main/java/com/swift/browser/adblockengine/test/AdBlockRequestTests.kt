package com.swift.browser.adblockengine.test

import com.swift.browser.adblockengine.network.AdBlockRequestClassifier
import com.swift.browser.adblockengine.network.AdBlockThirdPartyDetector

/**
 * Validates deep network classifications and cross-origin detection logic.
 */
object AdBlockRequestTests {
    fun runSuite(): Boolean {
        // Test 1: Classifications
        if (AdBlockRequestClassifier.classify("https://example.com/main.js") != "script") return false
        if (AdBlockRequestClassifier.classify("https://example.com/image.png") != "image") return false

        // Test 2: Third party checks
        val tp1 = AdBlockThirdPartyDetector.isThirdParty("https://tracker.com/pixel", "https://news.com/home")
        if (!tp1) return false

        val tp2 = AdBlockThirdPartyDetector.isThirdParty("https://news.com/assets/logo.png", "https://news.com/home")
        if (tp2) return false

        return true
    }
}
