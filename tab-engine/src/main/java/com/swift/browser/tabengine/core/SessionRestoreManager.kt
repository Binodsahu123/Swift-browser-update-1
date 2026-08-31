package com.swift.browser.tabengine.core

import com.swift.browser.tabengine.model.TabGroupModel
import com.swift.browser.tabengine.diagnostics.DiagnosticsManager
import com.swift.browser.tabengine.repository.TabRepository

class SessionRestoreManager(private val repository: TabRepository) {
    fun restoreSession(): List<TabGroupModel> {
        return try {
            val groups = repository.loadGroups()
            if (groups.isNotEmpty()) {
                DiagnosticsManager.logEvent("Session restored with ${groups.size} groups")
            }
            groups
        } catch (e: Exception) {
            DiagnosticsManager.logEvent("Session restore failed: ${e.message}")
            emptyList()
        }
    }

    fun saveSession(groups: List<TabGroupModel>) {
        try {
            repository.saveGroups(groups)
            DiagnosticsManager.logEvent("Session saved")
        } catch (e: Exception) {
            DiagnosticsManager.logEvent("Session save failed: ${e.message}")
        }
    }
}
