package com.swift.browser.aiengine

import android.content.Context
import android.util.Log
import android.webkit.WebView
import java.util.concurrent.ConcurrentHashMap

object PreloadAIEngine {
    private const val TAG = "PreloadAIEngine"
    
    // Track running preloads to avoid duplicates
    private val activePreloads = ConcurrentHashMap<String, Long>()

    fun preloadPage(
        context: Context,
        tabId: String,
        webView: WebView,
        url: String,
        settingsManager: AISettingsManager
    ) {
        if (!shouldPreload(url)) return
        
        Log.d(TAG, "Triggering automatic page preloading for: $url")
        activePreloads[url] = System.currentTimeMillis()
        
        BackgroundAnalyzer.analyzeActivePageInBackground(
            context = context,
            tabId = tabId,
            webView = webView,
            url = url,
            settingsManager = settingsManager
        )
    }

    fun preloadTranslatedPage(
        context: Context,
        tabId: String,
        webView: WebView,
        url: String,
        settingsManager: AISettingsManager,
        targetLangCode: String,
        targetLangName: String
    ) {
        // Allow re-preloading because translating implies the language state is modified
        Log.d(TAG, "Rebuilding AI summary cache on Translated Language Event: $targetLangName ($targetLangCode)")
        
        // Mark active pre-loads with a language key suffix to allow parallel or updated transitions
        val key = "${url}_translated_${targetLangCode}"
        activePreloads[key] = System.currentTimeMillis()
        
        BackgroundAnalyzer.analyzeActivePageInBackground(
            context = context,
            tabId = tabId,
            webView = webView,
            url = url,
            settingsManager = settingsManager,
            targetLangCode = targetLangCode,
            targetLangName = targetLangName
        )
    }

    private fun shouldPreload(url: String): Boolean {
        if (url.isBlank() || url.startsWith("swift://") || url.startsWith("about:") || url.startsWith("file://")) {
            return false
        }
        
        val lastPreloadTime = activePreloads[url]
        if (lastPreloadTime != null) {
            val elapsed = System.currentTimeMillis() - lastPreloadTime
            // Throttle duplicate preloads for the exact same URL within 20 seconds
            if (elapsed < 20000) {
                Log.d(TAG, "Throttle active preload for $url. Elapsed: ${elapsed}ms")
                return false
            }
        }
        return true
    }

    fun clearPreloadTracker() {
        activePreloads.clear()
        Log.i(TAG, "Preload tracking register cleared.")
    }
}
