package com.swift.browser.translateengine

import android.webkit.WebView

object DomRestoreEngine {

    /**
     * Executes the restore in the WebView in-place, keeping layout and scroll position intact.
     */
    fun restoreOriginal(webView: WebView, callback: (String?) -> Unit = {}) {
        restoreOriginal(webView, "active_tab", callback)
    }

    fun restoreOriginal(webView: WebView, tabId: String?, callback: (String?) -> Unit = {}) {
        OriginalPageSnapshotManager.restoreSnapshot(webView, tabId ?: "active_tab") { res ->
            callback(if (res) "restored" else "snapshot_failed")
        }
    }
}
