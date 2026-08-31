package com.swift.browser.tabengine.core

import com.swift.browser.tabengine.diagnostics.DiagnosticsManager

class TabDiagnosticsManager {
    fun recordLifecycleEvent(event: String) {
        DiagnosticsManager.logEvent(event)
    }
}
