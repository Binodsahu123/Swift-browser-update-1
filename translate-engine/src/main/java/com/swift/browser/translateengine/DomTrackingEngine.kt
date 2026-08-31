package com.swift.browser.translateengine

import java.util.concurrent.ConcurrentHashMap

class DomTrackingEngine {
    private val tabTranslatedNodesCount = ConcurrentHashMap<String, Int>()
    private val tabOriginalLanguages = ConcurrentHashMap<String, String>()

    fun recordTranslation(tabId: String, nodeCount: Int, srcLanguage: String) {
        tabTranslatedNodesCount[tabId] = (tabTranslatedNodesCount[tabId] ?: 0) + nodeCount
        tabOriginalLanguages[tabId] = srcLanguage
    }

    fun getTranslatedCount(tabId: String): Int {
        return tabTranslatedNodesCount[tabId] ?: 0
    }

    fun getOriginalLanguage(tabId: String): String {
        return tabOriginalLanguages[tabId] ?: "en"
    }

    fun clearTab(tabId: String) {
        tabTranslatedNodesCount.remove(tabId)
        tabOriginalLanguages.remove(tabId)
    }

    fun clearAll() {
        tabTranslatedNodesCount.clear()
        tabOriginalLanguages.clear()
    }
}
