package com.swift.browser.tabengine.api

import com.swift.browser.tabengine.model.TabGroupModel
import com.swift.browser.tabengine.model.TabModel
import kotlinx.coroutines.flow.StateFlow

interface TabEngineApi {
    val groups: StateFlow<List<TabGroupModel>>
    val activeGroupId: StateFlow<String?>
    val activeTabId: StateFlow<String?>

    fun initialize()
    fun shutdown()
    fun handleLowMemory()
    
    fun createTab(url: String, title: String, isIncognito: Boolean = false, groupId: String? = null): TabModel
    fun createPrivateTab(sessionId: String, url: String = "swift://newtab", title: String = "Private Tab", groupId: String? = null): TabModel = createTab(url, title, isIncognito = true, groupId = groupId)
    fun createPrivateGroup(sessionId: String, name: String = "Private", color: Long? = null): TabGroupModel = createGroup(name, isIncognito = true, color = color)
    fun closePrivateTab(tabId: String) = closeTab(tabId)
    fun getPrivateTabs(sessionId: String): List<TabModel> = emptyList()
    fun getPrivateTabs(): List<TabModel> = emptyList()
    fun getNormalTabs(): List<TabModel> = emptyList()
    fun closeTab(tabId: String)
    fun switchTab(tabId: String)
    fun getWebView(tabId: String?): android.webkit.WebView?
    fun putWebView(tabId: String?, webView: android.webkit.WebView)
    fun getAllWebViews(): Map<String, android.webkit.WebView>
    fun removeWebView(tabId: String?): android.webkit.WebView?
    fun updateTab(tabId: String, updater: (TabModel) -> TabModel)
    fun updateGroup(groupId: String, updater: (TabGroupModel) -> TabGroupModel)
    
    fun createGroup(name: String, isIncognito: Boolean = false, color: Long? = null): TabGroupModel
    fun switchGroup(groupId: String)
    fun closeGroup(groupId: String)
    
    fun moveTabToGroup(tabId: String, targetGroupId: String)
    
    fun getActiveTab(): TabModel?
    fun getActiveGroup(): TabGroupModel?
    fun getTab(tabId: String): TabModel?

    fun closeAllIncognitoTabs()
    fun saveSession()
    fun restoreSession(): List<TabGroupModel>
}
