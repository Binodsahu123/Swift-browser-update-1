package com.swift.browser.tabengine.core

import com.swift.browser.tabengine.model.TabGroupModel
import com.swift.browser.tabengine.model.TabModel
import com.swift.browser.tabengine.diagnostics.DiagnosticsManager

class TabResumeManager {
    fun resumeGroup(group: TabGroupModel): TabGroupModel {
        if (group.isSuspended) {
            DiagnosticsManager.logEvent("Resumed group ${group.id}")
        }
        return group.copy(
            isSuspended = false,
            lastActiveTime = System.currentTimeMillis(),
            tabs = group.tabs.map { tab ->
                if (tab.id == group.activeTabId) {
                    resumeTab(tab)
                } else tab
            }
        )
    }

    fun resumeTab(tab: TabModel): TabModel {
        if (tab.freezeState != 0) {
            DiagnosticsManager.logEvent("Resumed tab ${tab.id}")
        }
        return tab.copy(
            freezeState = 0,
            lastActiveTime = System.currentTimeMillis()
        )
    }
}
