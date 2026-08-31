package com.swift.browser.webstudio.engine

import java.io.File
import com.swift.browser.webstudio.EditorTab

class TabStateManager(private val diagnosticsManager: DiagnosticsManager) {
    fun createTab(id: String, name: String, content: String, file: File?): EditorTab {
        return EditorTab(id, name, content, null, file)
    }

    fun closeTab(id: String, currentTabs: List<EditorTab>, activeTabId: String?): Pair<List<EditorTab>, String?> {
        val newTabs = currentTabs.filter { it.id != id }
        val newActive = if (activeTabId == id) {
            newTabs.lastOrNull()?.id
        } else {
            activeTabId
        }
        return Pair(newTabs, newActive)
    }

    fun updateTabContent(activeTabId: String?, newContent: String, currentTabs: List<EditorTab>): List<EditorTab> {
        return currentTabs.map {
            if (it.id == activeTabId) it.copy(content = newContent) else it
        }
    }
}
