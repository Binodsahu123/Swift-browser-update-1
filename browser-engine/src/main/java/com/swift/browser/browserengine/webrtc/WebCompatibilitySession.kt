package com.swift.browser.browserengine.webrtc

/**
 * Tracks and caches the web platform capability profile for an individual browser tab.
 * This ensures different tabs do not share security/capability caches and respects 
 * origin transitions, desktop mode toggles, and secure context rules.
 */
data class WebCompatibilitySession(
    val tabId: String,
    val sessionId: String,
    val origin: String,
    val webViewVersion: String,
    val desktopMode: Boolean,
    val featureMatrix: WebMediaCapabilityMatrix,
    val secureContext: Boolean,
    val mediaCapability: String, // "FULL", "PARTIAL", "LIMITED", "UNSUPPORTED"
    val lastNavigation: String,
    val lastCompatibilityProbeTime: Long = System.currentTimeMillis()
)
