package com.swift.browser.browserengine

import android.util.Log

class BrowserBridgeEngine(private val stateEngine: BrowserStateEngine) {
    companion object {
        private const val TAG = "BrowserBridgeEngine"
    }

    fun onPageStarted(url: String) {
        Log.d(TAG, "Bridge: Page started -> $url")
        stateEngine.updateUrl(url)
        stateEngine.updateLoading(true, 15)
    }

    fun onPageFinished(url: String, title: String? = null) {
        Log.d(TAG, "Bridge: Page finished -> $url")
        stateEngine.updateUrl(url)
        if (!title.isNullOrBlank()) {
            stateEngine.updateTitle(title)
        }
        stateEngine.updateLoading(false, 100)
    }

    fun onProgressChanged(progress: Int) {
        stateEngine.updateLoading(progress < 100, progress)
    }

    fun onTitleReceived(title: String) {
        stateEngine.updateTitle(title)
    }

    fun onFaviconReceived(faviconUrl: String?) {
        stateEngine.updateFavicon(faviconUrl)
    }

    fun onErrorReceived(errorCode: Int, description: String, failingUrl: String) {
        stateEngine.setError(BrowserError(errorCode, description, failingUrl))
    }
}
