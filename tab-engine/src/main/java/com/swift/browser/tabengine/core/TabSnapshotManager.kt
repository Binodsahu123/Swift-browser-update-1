package com.swift.browser.tabengine.core

import android.graphics.Bitmap
import com.swift.browser.tabengine.engine.TabSnapshotEngine
import com.swift.browser.tabengine.diagnostics.DiagnosticsManager

class TabSnapshotManager(private val snapshotEngine: TabSnapshotEngine) {
    fun updateSnapshot(tabId: String, bitmap: Bitmap) {
        snapshotEngine.saveSnapshot(tabId, bitmap)
        DiagnosticsManager.logEvent("Snapshot updated for $tabId")
    }

    fun clearSnapshot(tabId: String) {
        snapshotEngine.clearSnapshot(tabId)
        DiagnosticsManager.logEvent("Snapshot cleared for $tabId")
    }
}
