package com.swift.browser.tabengine.core

import com.swift.browser.tabengine.model.TabGroupModel
import com.swift.browser.tabengine.model.TabModel
import com.swift.browser.tabengine.diagnostics.DiagnosticsManager

class TabFreezeManager {
    fun freezeInactive(groups: List<TabGroupModel>, activeGroupId: String?, activeTabId: String?): List<TabGroupModel> {
        val now = System.currentTimeMillis()
        return groups.map { group ->
            if (group.id != activeGroupId && (now - group.lastActiveTime > 300000)) { // 5 minutes
                DiagnosticsManager.logEvent("Suspended group ${group.id}")
                group.copy(
                    isSuspended = true,
                    tabs = group.tabs.map { tab ->
                        tab.copy(freezeState = 2)
                    }
                )
            } else if (group.id == activeGroupId) {
                // Freeze inactive tabs in active group
                group.copy(
                    tabs = group.tabs.map { tab ->
                        if (tab.id != activeTabId && (now - tab.lastActiveTime > 120000)) {
                            tab.copy(freezeState = 1)
                        } else tab
                    }
                )
            } else group
        }
    }

    fun freezeAggressive(groups: List<TabGroupModel>, activeGroupId: String?, activeTabId: String?): List<TabGroupModel> {
        return groups.map { group ->
            if (group.id != activeGroupId) {
                DiagnosticsManager.logEvent("Aggressive freeze group ${group.id}")
                group.copy(
                    isSuspended = true,
                    tabs = group.tabs.map { it.copy(freezeState = 2) }
                )
            } else {
                group.copy(
                    tabs = group.tabs.map { tab ->
                        if (tab.id != activeTabId) tab.copy(freezeState = 1) else tab
                    }
                )
            }
        }
    }
}
