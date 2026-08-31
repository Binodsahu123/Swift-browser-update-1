package com.swift.browser.desktopengine.state

import android.webkit.WebView
import com.swift.browser.desktopengine.navigation.DesktopModeTransition
import java.util.concurrent.ConcurrentHashMap

object DesktopNavigationState {
    private val scrollPositions = ConcurrentHashMap<String, Pair<Int, Int>>()

    fun captureState(tabId: String, webView: WebView) {
        val scrollX = webView.scrollX
        val scrollY = webView.scrollY
        scrollPositions[tabId] = Pair(scrollX, scrollY)
    }

    fun restoreState(tabId: String, webView: WebView, expectedGen: Long = -1L) {
        // Consume state immediately so it can never be applied more than once
        val pos = scrollPositions.remove(tabId) ?: return
        webView.postDelayed({
            if (expectedGen != -1L && !DesktopModeTransition.isGenerationCurrent(tabId, expectedGen)) {
                return@postDelayed
            }
            webView.scrollTo(pos.first, pos.second)
        }, 300)
    }

    fun consumeState(tabId: String): Pair<Int, Int>? {
        return scrollPositions.remove(tabId)
    }

    fun clearState(tabId: String) {
        scrollPositions.remove(tabId)
    }
}

