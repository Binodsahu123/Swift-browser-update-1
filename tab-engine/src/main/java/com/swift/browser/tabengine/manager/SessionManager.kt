package com.swift.browser.tabengine.manager

import com.swift.browser.tabengine.model.TabGroupModel
import com.swift.browser.tabengine.core.SessionRestoreManager

class SessionManager(private val restoreManager: SessionRestoreManager) {
    fun saveSession(groups: List<TabGroupModel>) {
        restoreManager.saveSession(groups)
    }
    
    fun restoreSession(): List<TabGroupModel> {
        return restoreManager.restoreSession()
    }
}
