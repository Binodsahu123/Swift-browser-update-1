package com.swift.browser.readerengine.api

import android.webkit.WebView
import com.swift.browser.readerengine.model.ReaderState
import kotlinx.coroutines.flow.StateFlow

interface ReaderEngineApi {
    val readerState: StateFlow<ReaderState>
    fun detectReaderModeAvailability(webView: WebView, tabId: String)
    fun triggerReaderMode(webView: WebView, tabId: String)
    fun closeReaderMode()
    fun updateReaderFontSize(size: Int)
    fun updateReaderTypeface(isSerif: Boolean)
    fun updateReaderTheme(theme: String)
}
