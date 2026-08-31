package com.swift.browser.tabengine.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.swift.browser.tabengine.api.TabEngineApi
import com.swift.browser.tabengine.manager.MemoryManager
import com.swift.browser.tabengine.manager.SessionManager
import com.swift.browser.tabengine.model.TabGroupModel
import com.swift.browser.tabengine.model.TabModel
import com.swift.browser.tabengine.diagnostics.DiagnosticsManager
import com.swift.browser.tabengine.core.TabFreezeManager
import com.swift.browser.tabengine.core.TabResumeManager
import com.swift.browser.tabengine.core.SessionRestoreManager
import com.swift.browser.tabengine.core.CrashRecoveryManager
import com.swift.browser.tabengine.repository.TabRepository
import java.util.UUID

class TabEngine(val context: android.content.Context, val scope: kotlinx.coroutines.CoroutineScope) : TabEngineApi {
    val motionEngine = MotionEngine(scope)
    val snapshotEngine = TabSnapshotEngine(scope)
    
    private val tabRepository = TabRepository(context)
    private val freezeManager = TabFreezeManager()
    private val resumeManager = TabResumeManager()
    private val sessionRestoreManager = SessionRestoreManager(tabRepository)
    private val crashRecoveryManager = CrashRecoveryManager()
    
    val memoryManager = MemoryManager(freezeManager)
    val sessionManager = SessionManager(sessionRestoreManager)
    
    private val webViewMap = mutableMapOf<String, android.webkit.WebView>()
    private val tabGroupEngine = TabGroupEngine()

    private val _groups = MutableStateFlow<List<TabGroupModel>>(emptyList())
    override val groups: StateFlow<List<TabGroupModel>> = _groups.asStateFlow()

    private val _activeGroupId = MutableStateFlow<String?>(null)
    override val activeGroupId: StateFlow<String?> = _activeGroupId.asStateFlow()
    
    private val _activeTabId = MutableStateFlow<String?>(null)
    override val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()

    override fun initialize() {
        try {
            val restored = sessionManager.restoreSession()
            val validGroups = mutableListOf<TabGroupModel>()
            for (group in restored) {
                val validTabs = group.tabs.filter { it.id.isNotBlank() }
                if (validTabs.isNotEmpty()) {
                    val activeId = if (validTabs.any { it.id == group.activeTabId }) {
                        group.activeTabId
                    } else {
                        validTabs.first().id
                    }
                    validGroups.add(group.copy(tabs = validTabs, activeTabId = activeId))
                }
            }

            if (validGroups.isEmpty()) {
                val group = createGroup("Default", false)
                createTab("swift://newtab", "New Tab", false, group.id)
            } else {
                _groups.value = validGroups
                val firstGroup = validGroups.first()
                _activeGroupId.value = firstGroup.id
                _activeTabId.value = firstGroup.activeTabId ?: firstGroup.tabs.firstOrNull()?.id
            }
        } catch (e: Throwable) {
            val group = createGroup("Default", false)
            createTab("swift://newtab", "New Tab", false, group.id)
        }
        DiagnosticsManager.logEvent("TabEngine initialized")
    }
    
    override fun shutdown() {
        sessionManager.saveSession(_groups.value)
        DiagnosticsManager.logEvent("TabEngine shutdown")
    }
    
    override fun handleLowMemory() {
        _groups.value = memoryManager.onLowMemory(_groups.value, _activeGroupId.value, _activeTabId.value)
        snapshotEngine.evictAll()
    }

    override fun createTab(url: String, title: String, isIncognito: Boolean, groupId: String?): TabModel {
        val targetGroup = if (isIncognito) {
            if (groupId != null) {
                _groups.value.find { it.id == groupId && (it.isIncognito || it.isPrivate) }
                    ?: _groups.value.find { it.isIncognito || it.isPrivate }
                    ?: createGroup("Incognito", isIncognito = true)
            } else {
                val activeGrp = _groups.value.find { it.id == _activeGroupId.value }
                if (activeGrp != null && (activeGrp.isIncognito || activeGrp.isPrivate)) {
                    activeGrp
                } else {
                    _groups.value.find { it.isIncognito || it.isPrivate }
                        ?: createGroup("Incognito", isIncognito = true)
                }
            }
        } else {
            if (groupId != null) {
                _groups.value.find { it.id == groupId && !it.isIncognito && !it.isPrivate }
                    ?: _groups.value.find { !it.isIncognito && !it.isPrivate }
                    ?: createGroup("Default", isIncognito = false)
            } else {
                val activeGrp = _groups.value.find { it.id == _activeGroupId.value }
                if (activeGrp != null && !activeGrp.isIncognito && !activeGrp.isPrivate) {
                    activeGrp
                } else {
                    _groups.value.find { !it.isIncognito && !it.isPrivate }
                        ?: createGroup("Default", isIncognito = false)
                }
            }
        }

        val targetGroupId = targetGroup.id
        val newTab = TabModel(
            id = UUID.randomUUID().toString(),
            url = url,
            title = title,
            isIncognito = isIncognito,
            isPrivate = isIncognito,
            privateSessionId = if (isIncognito) (targetGroup.privateSessionId ?: UUID.randomUUID().toString()) else null,
            groupId = targetGroupId
        )
        
        _groups.value = _groups.value.map { group ->
            if (group.id == targetGroupId) {
                val updatedGroup = resumeManager.resumeGroup(group)
                tabGroupEngine.addTabToGroup(updatedGroup, newTab)
            } else group
        }
        
        _activeGroupId.value = targetGroupId
        _activeTabId.value = newTab.id
        
        DiagnosticsManager.logEvent("Created tab ${newTab.id} (private=$isIncognito) in group $targetGroupId")
        return newTab
    }

    override fun createPrivateTab(sessionId: String, url: String, title: String, groupId: String?): TabModel {
        val targetGroup = if (groupId != null) {
            _groups.value.find { it.id == groupId && (it.isPrivate || it.isIncognito) && (it.privateSessionId == sessionId || it.privateSessionId == null) }
                ?: _groups.value.find { (it.isPrivate || it.isIncognito) && it.privateSessionId == sessionId }
                ?: createPrivateGroup(sessionId = sessionId)
        } else {
            val activeGrp = _groups.value.find { it.id == _activeGroupId.value }
            if (activeGrp != null && (activeGrp.isPrivate || activeGrp.isIncognito) && (activeGrp.privateSessionId == sessionId || activeGrp.privateSessionId == null)) {
                activeGrp
            } else {
                _groups.value.find { (it.isPrivate || it.isIncognito) && it.privateSessionId == sessionId }
                    ?: createPrivateGroup(sessionId = sessionId)
            }
        }

        val targetGroupId = targetGroup.id
        val newTab = TabModel(
            id = UUID.randomUUID().toString(),
            url = url,
            title = title,
            isIncognito = true,
            isPrivate = true,
            privateSessionId = sessionId,
            groupId = targetGroupId
        )

        _groups.value = _groups.value.map { group ->
            if (group.id == targetGroupId) {
                val updatedGroup = resumeManager.resumeGroup(group)
                tabGroupEngine.addTabToGroup(updatedGroup, newTab)
            } else group
        }

        _activeGroupId.value = targetGroupId
        _activeTabId.value = newTab.id

        DiagnosticsManager.logEvent("Created private tab ${newTab.id} for session $sessionId in group $targetGroupId")
        return newTab
    }

    override fun createPrivateGroup(sessionId: String, name: String, color: Long?): TabGroupModel {
        val newGroup = TabGroupModel(
            id = UUID.randomUUID().toString(),
            name = name,
            isIncognito = true,
            isPrivate = true,
            privateSessionId = sessionId,
            color = color ?: 0xFF475569
        )
        _groups.value = _groups.value + newGroup
        DiagnosticsManager.logEvent("Created private group ${newGroup.id} for session $sessionId")
        return newGroup
    }

    override fun closePrivateTab(tabId: String) {
        closeTab(tabId)
    }

    override fun getPrivateTabs(sessionId: String): List<TabModel> {
        return _groups.value.flatMap { it.tabs }.filter { 
            (it.isPrivate || it.isIncognito) && (it.privateSessionId == sessionId || it.privateSessionId == null)
        }
    }

    override fun getPrivateTabs(): List<TabModel> {
        return _groups.value.flatMap { it.tabs }.filter { it.isPrivate || it.isIncognito }
    }

    override fun getNormalTabs(): List<TabModel> {
        return _groups.value.flatMap { it.tabs }.filter { !it.isPrivate && !it.isIncognito && it.privateSessionId == null }
    }

    
    override fun getWebView(tabId: String?): android.webkit.WebView? {
        if (tabId == null) return null
        return webViewMap[tabId]
    }
    
    override fun putWebView(tabId: String?, webView: android.webkit.WebView) {
        if (tabId != null) webViewMap[tabId] = webView
    }
    
    override fun getAllWebViews(): Map<String, android.webkit.WebView> = webViewMap

    override fun removeWebView(tabId: String?): android.webkit.WebView? {
        return webViewMap.remove(tabId)
    }

    override fun closeTab(tabId: String) {
        var groupToRemoveFrom: TabGroupModel? = null
        _groups.value = _groups.value.mapNotNull { group ->
            if (group.tabs.any { it.id == tabId }) {
                groupToRemoveFrom = group
                val updatedGroup = tabGroupEngine.removeTabFromGroup(group, tabId)
                if (updatedGroup.tabs.isEmpty()) null else updatedGroup
            } else {
                group
            }
        }
        
        if (groupToRemoveFrom != null) {
            snapshotEngine.clearSnapshot(tabId)
            webViewMap.remove(tabId)?.destroy()
            DiagnosticsManager.logEvent("Closed tab $tabId")
        }
        
        syncActiveState()
    }

    override fun switchTab(tabId: String) {
        val start = System.currentTimeMillis()
        
        // Capture snapshot of the current active tab before switching
        _activeTabId.value?.let { currentTabId ->
            val webView = webViewMap[currentTabId]
            if (webView != null && webView.width > 0 && webView.height > 0) {
                try {
                    val bitmap = android.graphics.Bitmap.createBitmap(
                        webView.width, 
                        webView.height, 
                        android.graphics.Bitmap.Config.ARGB_8888
                    )
                    val canvas = android.graphics.Canvas(bitmap)
                    webView.draw(canvas)
                    snapshotEngine.saveSnapshot(currentTabId, bitmap)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        _groups.value = _groups.value.map { group ->
            if (group.tabs.any { it.id == tabId }) {
                _activeGroupId.value = group.id
                _activeTabId.value = tabId
                resumeManager.resumeGroup(group.copy(activeTabId = tabId))
            } else {
                group
            }
        }
        DiagnosticsManager.recordFrameTime(System.currentTimeMillis() - start)
        DiagnosticsManager.logEvent("Switched to tab $tabId")
    }

    override fun updateTab(tabId: String, updater: (TabModel) -> TabModel) {
        _groups.value = _groups.value.map { group ->
            if (group.tabs.any { it.id == tabId }) {
                tabGroupEngine.updateTabInGroup(group, tabId, updater)
            } else {
                group
            }
        }
    }

    override fun updateGroup(groupId: String, updater: (TabGroupModel) -> TabGroupModel) {
        _groups.value = _groups.value.map { group ->
            if (group.id == groupId) {
                updater(group)
            } else {
                group
            }
        }
        sessionManager.saveSession(_groups.value)
    }

    override fun createGroup(name: String, isIncognito: Boolean, color: Long?): TabGroupModel {
        val newGroup = TabGroupModel(
            id = UUID.randomUUID().toString(),
            name = name,
            isIncognito = isIncognito,
            isPrivate = isIncognito,
            privateSessionId = if (isIncognito) UUID.randomUUID().toString() else null,
            color = color ?: (if (isIncognito) 0xFF475569 else 0xFFCCCCCC)
        )
        _groups.value = _groups.value + newGroup
        DiagnosticsManager.logEvent("Created group ${newGroup.id} (private=$isIncognito)")
        return newGroup
    }

    override fun switchGroup(groupId: String) {
        val start = System.currentTimeMillis()
        
        // Capture snapshot of the current active tab before switching
        _activeTabId.value?.let { currentTabId ->
            val webView = webViewMap[currentTabId]
            if (webView != null && webView.width > 0 && webView.height > 0) {
                try {
                    val bitmap = android.graphics.Bitmap.createBitmap(
                        webView.width, 
                        webView.height, 
                        android.graphics.Bitmap.Config.ARGB_8888
                    )
                    val canvas = android.graphics.Canvas(bitmap)
                    webView.draw(canvas)
                    snapshotEngine.saveSnapshot(currentTabId, bitmap)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        val targetGroup = _groups.value.find { it.id == groupId }
        if (targetGroup != null) {
            val updatedTarget = resumeManager.resumeGroup(targetGroup)
            _activeGroupId.value = targetGroup.id
            _activeTabId.value = updatedTarget.activeTabId
            
            _groups.value = _groups.value.map { group ->
                if (group.id == groupId) {
                    updatedTarget
                } else {
                    group
                }
            }
            DiagnosticsManager.recordFrameTime(System.currentTimeMillis() - start)
            DiagnosticsManager.logEvent("Switched to group $groupId")
        }
    }

    override fun closeGroup(groupId: String) {
        val targetGroup = _groups.value.find { it.id == groupId }
        targetGroup?.tabs?.forEach { snapshotEngine.clearSnapshot(it.id) }
        
        _groups.value = _groups.value.filter { it.id != groupId }
        DiagnosticsManager.logEvent("Closed group $groupId")
        syncActiveState()
    }

    override fun moveTabToGroup(tabId: String, targetGroupId: String) {
        val tabToMove = _groups.value.flatMap { it.tabs }.find { it.id == tabId }
        val targetGroup = _groups.value.find { it.id == targetGroupId }
        if (tabToMove == null || targetGroup == null) return

        val tabIsPrivate = tabToMove.isPrivate || tabToMove.isIncognito
        val groupIsPrivate = targetGroup.isPrivate || targetGroup.isIncognito
        
        // Strictly reject mixing private and normal tabs/groups
        if (tabIsPrivate != groupIsPrivate) {
            DiagnosticsManager.logEvent("Rejected moving tab $tabId to group $targetGroupId: cannot mix private and normal tabs/groups")
            return
        }

        if (tabIsPrivate && groupIsPrivate) {
            if (tabToMove.privateSessionId != null && targetGroup.privateSessionId != null && tabToMove.privateSessionId != targetGroup.privateSessionId) {
                DiagnosticsManager.logEvent("Rejected moving private tab $tabId to group $targetGroupId: private session mismatch")
                return
            }
        }

        _groups.value = _groups.value.mapNotNull { group ->
            if (group.tabs.any { it.id == tabId }) {
                val updatedGroup = tabGroupEngine.removeTabFromGroup(group, tabId)
                if (updatedGroup.tabs.isEmpty()) null else updatedGroup
            } else {
                group
            }
        }
        
        _groups.value = _groups.value.map { group ->
            if (group.id == targetGroupId) {
                tabGroupEngine.addTabToGroup(group, tabToMove)
            } else group
        }
        DiagnosticsManager.logEvent("Moved tab $tabId to group $targetGroupId")
        syncActiveState()
    }

    override fun getActiveTab(): TabModel? {
        val groupId = _activeGroupId.value ?: return null
        val tabId = _activeTabId.value ?: return null
        return _groups.value.find { it.id == groupId }?.tabs?.find { it.id == tabId }
    }

    
    override fun getTab(tabId: String): TabModel? {
        return _groups.value.flatMap { it.tabs }.find { it.id == tabId }
    }

    override fun getActiveGroup(): TabGroupModel? {
        val groupId = _activeGroupId.value ?: return null
        return _groups.value.find { it.id == groupId }
    }

    private fun syncActiveState() {
        val currentGroups = _groups.value
        if (currentGroups.isEmpty()) {
            _activeGroupId.value = null
            _activeTabId.value = null
            return
        }
        
        val activeGroup = currentGroups.find { it.id == _activeGroupId.value } ?: currentGroups.first()
        _activeGroupId.value = activeGroup.id
        
        if (activeGroup.tabs.none { it.id == _activeTabId.value }) {
            _activeTabId.value = activeGroup.activeTabId ?: activeGroup.tabs.firstOrNull()?.id
        }
        
        // Save state on structural changes
        sessionManager.saveSession(_groups.value)
    }

    override fun closeAllIncognitoTabs() {
        val incognitoTabs = _groups.value.flatMap { it.tabs }.filter { it.isIncognito }
        incognitoTabs.forEach { closeTab(it.id) }
        DiagnosticsManager.logEvent("Closed all incognito tabs")
    }

    override fun saveSession() {
        sessionManager.saveSession(_groups.value)
        DiagnosticsManager.logEvent("Session saved manually")
    }

    override fun restoreSession(): List<TabGroupModel> {
        val restored = sessionManager.restoreSession()
        if (restored.isNotEmpty()) {
            _groups.value = restored
            _activeGroupId.value = restored.firstOrNull()?.id
            _activeTabId.value = restored.firstOrNull()?.activeTabId
        }
        DiagnosticsManager.logEvent("Session restored manually")
        return restored
    }
    
    fun recoverFromCrash() {
        _groups.value = crashRecoveryManager.handleEngineCrash(_groups.value)
        syncActiveState()
    }
}
