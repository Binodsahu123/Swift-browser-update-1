package com.swift.browser.tabengine.core

import com.swift.browser.tabengine.diagnostics.DiagnosticsManager
import com.swift.browser.tabengine.model.TabGroupModel
import com.swift.browser.tabengine.model.TabModel
import java.util.UUID

class CrashRecoveryManager {
    fun recoverTab(tab: TabModel): TabModel {
        DiagnosticsManager.logEvent("Recovering crashed tab ${tab.id}")
        return tab.copy(isWebViewDestroyed = false, hasLoadedSuccessfully = false)
    }

    fun recoverGroup(group: TabGroupModel): TabGroupModel {
        DiagnosticsManager.logEvent("Recovering crashed group ${group.id}")
        return group.copy(isSuspended = false, tabs = group.tabs.map { recoverTab(it) })
    }

    fun handleEngineCrash(groups: List<TabGroupModel>): List<TabGroupModel> {
        DiagnosticsManager.logEvent("Handling full engine crash")
        if (groups.isEmpty()) {
            val fallbackTab = TabModel(id = UUID.randomUUID().toString(), url = "swift://newtab", title = "New Tab")
            val fallbackGroup = TabGroupModel(id = UUID.randomUUID().toString(), name = "Default", tabs = listOf(fallbackTab), activeTabId = fallbackTab.id)
            return listOf(fallbackGroup)
        }
        return groups.map { recoverGroup(it) }
    }
}
