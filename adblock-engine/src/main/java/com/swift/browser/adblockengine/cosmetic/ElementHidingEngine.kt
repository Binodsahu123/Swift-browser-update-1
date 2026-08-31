package com.swift.browser.adblockengine.cosmetic

import com.swift.browser.adblockengine.brave.BraveAdblockAdapter
import com.swift.browser.adblockengine.core.AdBlockPolicy
import com.swift.browser.adblockengine.core.AdBlockEngine

object ElementHidingEngine {
    fun getHidingJavascript(documentUrl: String?): String {
        if (documentUrl == null || !AdBlockEngine.isEnabled() || !AdBlockPolicy.isEasyListEnabled) return ""
        val docLower = documentUrl.lowercase()
        // Protect YouTube and Google Meet from cosmetic distortion
        if (docLower.contains("youtube.com") || docLower.contains("youtu.be") || docLower.contains("meet.google.com")) return ""
        
        val selectors = BraveAdblockAdapter.getCosmeticSelectors(documentUrl)
        if (selectors.isEmpty()) return ""

        val joinedSelectors = selectors.joinToString(", ")
        val escapedSelectors = joinedSelectors.replace("\\", "\\\\").replace("'", "\\'")

        return """
            (function() {
                try {
                    var styleId = 'swift-adblock-cosmetic-style';
                    var existing = document.getElementById(styleId);
                    var cssText = '$escapedSelectors { display: none !important; }';
                    if (existing) {
                        if (existing.innerHTML !== cssText) {
                            existing.innerHTML = cssText;
                        }
                        return;
                    }
                    var style = document.createElement('style');
                    style.id = styleId;
                    style.type = 'text/css';
                    style.innerHTML = cssText;
                    var parent = document.head || document.documentElement;
                    if (parent) {
                        parent.appendChild(style);
                    }
                } catch (e) {}
            })();
        """.trimIndent()
    }
}