package com.swift.browser.tabengine.engine

import com.swift.browser.tabengine.model.TabGroupModel
import com.swift.browser.tabengine.model.TabModel

class TabGroupEngine {
    fun addTabToGroup(group: TabGroupModel, tab: TabModel): TabGroupModel {
        val updatedTabs = group.tabs.filter { it.id != tab.id } + tab.copy(groupId = group.id)
        return group.copy(
            tabs = updatedTabs,
            activeTabId = tab.id,
            lastActiveTime = System.currentTimeMillis()
        )
    }

    fun removeTabFromGroup(group: TabGroupModel, tabId: String): TabGroupModel {
        val updatedTabs = group.tabs.filter { it.id != tabId }
        val newActiveTabId = if (group.activeTabId == tabId) {
            updatedTabs.lastOrNull()?.id
        } else {
            group.activeTabId
        }
        return group.copy(
            tabs = updatedTabs,
            activeTabId = newActiveTabId,
            lastActiveTime = System.currentTimeMillis()
        )
    }
    
    fun updateTabInGroup(group: TabGroupModel, tabId: String, updater: (TabModel) -> TabModel): TabGroupModel {
        val updatedTabs = group.tabs.map { if (it.id == tabId) updater(it) else it }
        return group.copy(tabs = updatedTabs)
    }
}
