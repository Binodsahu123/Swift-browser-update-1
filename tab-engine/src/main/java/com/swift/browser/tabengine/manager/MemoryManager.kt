package com.swift.browser.tabengine.manager

import android.util.Log
import com.swift.browser.tabengine.diagnostics.DiagnosticsManager
import com.swift.browser.tabengine.core.TabFreezeManager
import com.swift.browser.tabengine.model.TabGroupModel

class MemoryManager(private val freezeManager: TabFreezeManager) {
    fun optimizeMemory(groups: List<TabGroupModel>, activeGroupId: String?, activeTabId: String?): List<TabGroupModel> {
        Log.d("MemoryManager", "Optimizing memory: trimming inactive groups and tabs.")
        DiagnosticsManager.logEvent("Memory optimized")
        return freezeManager.freezeInactive(groups, activeGroupId, activeTabId)
    }
    
    fun onLowMemory(groups: List<TabGroupModel>, activeGroupId: String?, activeTabId: String?): List<TabGroupModel> {
        Log.w("MemoryManager", "Low memory detected! Dropping non-essential caches.")
        DiagnosticsManager.logEvent("Low memory triggered")
        return freezeManager.freezeAggressive(groups, activeGroupId, activeTabId)
    }
}
