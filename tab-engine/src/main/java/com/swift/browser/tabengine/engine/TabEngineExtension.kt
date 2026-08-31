package com.swift.browser.tabengine.engine

import com.swift.browser.tabengine.model.TabModel
import com.swift.browser.tabengine.model.TabGroupModel

fun TabEngine.getAllTabs(): List<TabModel> {
    return this.groups.value.flatMap { it.tabs }
}

fun TabEngine.findGroupByName(name: String): TabGroupModel? {
    return this.groups.value.find { it.name.equals(name, ignoreCase = true) }
}

fun TabEngine.createTabInGroup(parentTabId: String, url: String, title: String = "New Tab"): TabModel? {
    val parentTab = getAllTabs().find { it.id == parentTabId } ?: return null
    var targetGroupId = parentTab.groupId
    if (targetGroupId == null) {
        val newGroup = createGroup("Group " + (groups.value.size + 1), parentTab.isIncognito)
        targetGroupId = newGroup.id
        moveTabToGroup(parentTab.id, targetGroupId)
    }
    val newTab = createTab(url, title, parentTab.isIncognito, targetGroupId)
    switchTab(newTab.id)
    return newTab
}

fun TabEngine.removeTabFromGroup(tabId: String) {
    val defaultGroup = findGroupByName("Default") ?: createGroup("Default")
    moveTabToGroup(tabId, defaultGroup.id)
}
