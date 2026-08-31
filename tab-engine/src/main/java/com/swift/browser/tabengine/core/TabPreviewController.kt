package com.swift.browser.tabengine.core

import android.graphics.Bitmap
import com.swift.browser.tabengine.engine.TabSnapshotEngine

class TabPreviewController(private val snapshotEngine: TabSnapshotEngine) {
    fun getPreview(tabId: String): Bitmap? {
        return snapshotEngine.getThumbnail(tabId)
    }

    fun getHighResPreview(tabId: String): Bitmap? {
        return snapshotEngine.getHighResSnapshot(tabId)
    }
}
