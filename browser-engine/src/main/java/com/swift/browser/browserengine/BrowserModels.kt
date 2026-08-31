package com.swift.browser.browserengine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.swift.browser.newsengine.NewsItemEntity

import com.swift.browser.desktopengine.api.DesktopEngineProvider

enum class BrowserState {
    IDLE,
    LOADING,
    READY,
    STOPPED,
    ERROR,
    RESTORING,
    ATTACHED,
    DETACHED
}

data class BrowserPageState(
    val title: String = "",
    val url: String = "",
    val favicon: String? = null,
    val isIncognito: Boolean = false,
    val userAgentMode: String = "Default",
    val loadingProgress: Int = 0,
    val isLoading: Boolean = false
)

data class BrowserNavigationState(
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val historyIndex: Int = 0,
    val historyCount: Int = 0
)

data class BrowserError(
    val errorCode: Int = 0,
    val description: String = "",
    val failingUrl: String = ""
)

data class BrowserSession(
    val sessionId: String = "session_${System.currentTimeMillis()}",
    val activeTabId: String = "",
    val tabUrls: List<String> = emptyList()
)

enum class MediaType {
    VIDEO,
    AUDIO,
    IMAGE,
    DOCUMENT
}

data class LocalMediaItem(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val path: String = "",
    val folder: String = "",
    val size: Long = 0L,
    val sizeFormatted: String = "",
    val mimeType: String = "",
    val dateAdded: Long = 0L,
    val duration: Long = 0L,
    val durationFormatted: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val thumbnailUri: String? = null,
    val type: MediaType = MediaType.DOCUMENT,
    val isFavorite: Boolean = false
)

data class ClearBrowsingDataResult(
    val historySuccess: Boolean = true,
    val cookiesSuccess: Boolean = true,
    val cacheSuccess: Boolean = true,
    val downloadsSuccess: Boolean = true,
    val errors: List<String> = emptyList()
) {
    val isAllSuccess: Boolean get() = historySuccess && cookiesSuccess && cacheSuccess && downloadsSuccess && errors.isEmpty()
}

data class BrowserUiState(
    val feedCategory: String = "For You",
    val isFeedLoading: Boolean = false,
    val articles: List<NewsItemEntity> = emptyList(),
    val isIncognito: Boolean = false,
    val activeTabId: String = "",
    val globalAdBlockEnabled: Boolean = true,
    val isJavaScriptEnabled: Boolean = true
)

open class BrowserViewModel(
    val app: android.app.Application? = null,
    val repo: com.swift.browser.data.BrowserRepository? = null,
    val preferenceManager: com.swift.browser.databasecore.PreferenceManager? = null
) : ViewModel() {
    val extensionApi: com.swift.browser.extensionengine.ExtensionEngineApi? by lazy {
        com.swift.browser.analyticscore.StartupTracker.recordStage(
            stage = "EXTENSION_ENGINE_CREATE",
            className = "ExtensionEngineApi",
            methodName = "getInstance"
        )
        app?.let { com.swift.browser.extensionengine.ExtensionEngineApi.getInstance(it) }
    }
    val adProtectionApi: com.swift.browser.adblockengine.AdProtectionEngineApi? by lazy {
        com.swift.browser.analyticscore.StartupTracker.recordStage(
            stage = "ADBLOCK_ENGINE_CREATE",
            className = "AdProtectionEngineApi",
            methodName = "getInstance"
        )
        app?.let { com.swift.browser.adblockengine.AdProtectionEngineApi.getInstance(it) }
    }
    val uiState = kotlinx.coroutines.flow.MutableStateFlow(BrowserUiState())
    val scrollChromeController = com.swift.browser.tabengine.ui.ScrollChromeController(viewModelScope)
    val contextMenuState = kotlinx.coroutines.flow.MutableStateFlow(ContextMenuState())

    data class RewriteRecord(
        val sourceUrl: String,
        val targetUrl: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val lastRewriteCache = java.util.concurrent.ConcurrentHashMap<String, RewriteRecord>()

    val downloadEngine: com.swift.browser.downloadengine.DownloadEngine by lazy {
        com.swift.browser.analyticscore.StartupTracker.recordStage(
            stage = "DOWNLOAD_ENGINE_CREATE",
            className = "DownloadEngineProvider",
            methodName = "getEngine"
        )
        if (app != null) {
            com.swift.browser.downloadengine.DownloadEngineProvider.getEngine(app)
        } else {
            object : com.swift.browser.downloadengine.DownloadEngine {
                private val _dl = kotlinx.coroutines.flow.MutableStateFlow<List<com.swift.browser.downloadengine.DownloadItem>>(emptyList())
                override fun getDownloadsFlow() = _dl.asStateFlow()
                override fun getDownloadsByCategory(category: String) = _dl.asStateFlow()
                override suspend fun startDownload(url: String, fileName: String, mimeType: String, threads: Int) = 0L
                override fun setConfig(config: com.swift.browser.downloadengine.DownloadConfig) {}
                override fun getConfig() = com.swift.browser.downloadengine.DownloadConfig()
                override suspend fun insertOrUpdateDownload(item: com.swift.browser.downloadengine.DownloadItem) {}
                override suspend fun pauseDownload(id: Long) {}
                override suspend fun resumeDownload(id: Long) {}
                override suspend fun cancelDownload(id: Long) {}
                override suspend fun deleteDownload(id: Long) {
                    _dl.value = _dl.value.filterNot { it.id == id }
                }
                override suspend fun renameDownload(id: Long, newName: String) {}
            }
        }
    }

    val downloads: kotlinx.coroutines.flow.StateFlow<List<com.swift.browser.downloadengine.DownloadItem>> by lazy { 
        downloadEngine.getDownloadsFlow().stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            emptyList()
        )
    }

    val bookmarkEngine: com.swift.browser.bookmarkengine.api.BookmarkEngineApi by lazy {
        com.swift.browser.analyticscore.StartupTracker.recordStage(
            stage = "BOOKMARK_ENGINE_CREATE",
            className = "BookmarkEngineProvider",
            methodName = "getEngine"
        )
        if (app != null) {
            com.swift.browser.bookmarkengine.api.BookmarkEngineProvider.getEngine(app, this.viewModelScope)
        } else {
            com.swift.browser.bookmarkengine.api.BookmarkEngineProvider.api
        }
    }

    val bookmarks: kotlinx.coroutines.flow.StateFlow<List<com.swift.browser.bookmarkengine.Bookmark>> by lazy { bookmarkEngine.bookmarks }

    val readerEngine: com.swift.browser.readerengine.engine.ReaderModeEngine by lazy { com.swift.browser.readerengine.engine.ReaderModeEngine() }

    val translateEngine: com.swift.browser.translateengine.TranslateEngineApi by lazy {
        com.swift.browser.analyticscore.StartupTracker.recordStage(
            stage = "TRANSLATE_ENGINE_CREATE",
            className = "TranslateEngineApi",
            methodName = "getInstance"
        )
        if (app != null) {
            com.swift.browser.translateengine.TranslateEngineApi.getInstance(app)
        } else {
            com.swift.browser.translateengine.TranslateEngineApi.getInstance(android.app.Application())
        }
    }

    val historyEngine: com.swift.browser.historyengine.HistoryEngine by lazy {
        com.swift.browser.analyticscore.StartupTracker.recordStage(
            stage = "HISTORY_ENGINE_CREATE",
            className = "HistoryEngineProvider",
            methodName = "getEngine"
        )
        repo?.historyEngine ?: com.swift.browser.historyengine.api.HistoryEngineProvider.api
    }

    val history: kotlinx.coroutines.flow.StateFlow<List<com.swift.browser.historyengine.HistoryItem>> by lazy {
        historyEngine.getHistoryFlow().stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            emptyList()
        )
    }

    val permissionEngine: com.swift.browser.permissionengine.PermissionEngine? by lazy {
        com.swift.browser.analyticscore.StartupTracker.recordStage(
            stage = "PERMISSION_ENGINE_CREATE",
            className = "PermissionEngineProvider",
            methodName = "get"
        )
        if (app != null) {
            com.swift.browser.permissionengine.PermissionEngineProvider.get(app)
        } else null
    }

    val privateModeEngine: com.swift.browser.privatemode.PrivateModeEngineApi by lazy {
        com.swift.browser.analyticscore.StartupTracker.recordStage(
            stage = "PRIVATE_MODE_ENGINE_CREATE",
            className = "PrivateModeEngineProvider",
            methodName = "getEngine"
        )
        if (app != null) {
            com.swift.browser.privatemode.PrivateModeEngineProvider.getEngine(app)
        } else {
            com.swift.browser.privatemode.PrivateModeEngineProvider.api
        }
    }

    val isBiometricUnlocked: kotlinx.coroutines.flow.StateFlow<Boolean> by lazy {
        privateModeEngine.isBiometricUnlocked
    }

    fun lockPrivateTabs() {
        privateModeEngine.lockPrivateTabs()
    }

    fun unlockPrivateTabs() {
        privateModeEngine.unlockPrivateTabs()
    }

    fun authenticatePrivateTabs(
        activity: androidx.fragment.app.FragmentActivity,
        onResult: (com.swift.browser.privatemode.BiometricAuthResult) -> Unit = {}
    ) {
        privateModeEngine.authenticateBiometric(
            activity = activity,
            config = com.swift.browser.privatemode.PrivateBiometricConfig(
                promptTitle = "Unlock Private Tabs",
                promptSubtitle = "Fingerprint or face unlock required",
                promptDescription = "Authenticate with biometrics to view active private tabs",
                allowDeviceCredentialFallback = true
            ),
            onResult = onResult
        )
    }

    val topSiteEngine: TopSiteEngine? by lazy {
        com.swift.browser.analyticscore.StartupTracker.recordStage(
            stage = "TOPSITE_ENGINE_CREATE",
            className = "TopSiteEngineImpl",
            methodName = "init"
        )
        val canonicalRepo = repo ?: app?.let { com.swift.browser.data.BrowserRepository(com.swift.browser.data.BrowserDatabase.getDatabase(it)) }
        canonicalRepo?.let { TopSiteEngineImpl(it) }
    }

    val topSites: kotlinx.coroutines.flow.StateFlow<List<com.swift.browser.data.TopSite>> by lazy {
        topSiteEngine?.getTopSites()?.stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            emptyList()
        ) ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())
    }

    val newsEngine: com.swift.browser.newsengine.api.NewsEngineApi by lazy {
        com.swift.browser.analyticscore.StartupTracker.recordStage(
            stage = "NEWS_ENGINE_CREATE",
            className = "NewsEngineProvider",
            methodName = "getEngine"
        )
        if (app != null) {
            com.swift.browser.newsengine.api.NewsEngineProvider.getEngine(app, this.viewModelScope)
        } else {
            object : com.swift.browser.newsengine.api.NewsEngineApi {
                private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(com.swift.browser.newsengine.state.NewsUiState())
                override val uiState = _uiState.asStateFlow()
                override fun selectCategory(category: String) {
                    _uiState.value = _uiState.value.copy(feedCategory = category)
                }
                override fun refresh() {}
                override fun getNewsFlow() = kotlinx.coroutines.flow.emptyFlow<List<com.swift.browser.newsengine.NewsItemEntity>>()
                override fun getFeedUrlForCategory(category: String) = ""
            }
        }
    }

    val tabEngine: com.swift.browser.tabengine.api.TabEngineApi by lazy {
        com.swift.browser.analyticscore.StartupTracker.recordStage(
            stage = "TAB_ENGINE_CREATE",
            className = "TabEngineProvider",
            methodName = "getEngine"
        )
        if (app != null) {
            com.swift.browser.tabengine.api.TabEngineProvider.getEngine(app, this.viewModelScope).apply {
                viewModelScope.launch(Dispatchers.IO) {
                    com.swift.browser.analyticscore.StartupTracker.recordStage(
                        stage = "TAB_ENGINE_INITIALIZE",
                        className = "TabEngine",
                        methodName = "initialize"
                    )
                    initialize()
                }
            }
        } else {
            object : com.swift.browser.tabengine.api.TabEngineApi {
                private val _groups = kotlinx.coroutines.flow.MutableStateFlow<List<com.swift.browser.tabengine.model.TabGroupModel>>(
                    listOf(
                        com.swift.browser.tabengine.model.TabGroupModel(
                            id = "group_1",
                            name = "Main Group",
                            tabs = listOf(com.swift.browser.tabengine.model.TabModel(id = "tab_1", url = "swift://newtab", title = "New Tab"))
                        )
                    )
                )
                override val groups = _groups.asStateFlow()
                override val activeGroupId = kotlinx.coroutines.flow.MutableStateFlow<String?>("group_1")
                override val activeTabId = kotlinx.coroutines.flow.MutableStateFlow<String?>("tab_1")

                override fun initialize() {}
                override fun shutdown() {}
                override fun handleLowMemory() {}
                override fun createTab(url: String, title: String, isIncognito: Boolean, groupId: String?): com.swift.browser.tabengine.model.TabModel {
                    val newTab = com.swift.browser.tabengine.model.TabModel(id = java.util.UUID.randomUUID().toString(), url = url, title = title, isIncognito = isIncognito)
                    val currentGroups = _groups.value
                    val targetGroup = currentGroups.firstOrNull() ?: com.swift.browser.tabengine.model.TabGroupModel(id = "group_1", name = "Main Group")
                    val updatedGroup = targetGroup.copy(tabs = targetGroup.tabs + newTab, activeTabId = newTab.id)
                    _groups.value = currentGroups.map { if (it.id == targetGroup.id) updatedGroup else it }
                    activeTabId.value = newTab.id
                    return newTab
                }
                override fun closeTab(tabId: String) {
                    _groups.value = _groups.value.map { g ->
                        g.copy(tabs = g.tabs.filterNot { it.id == tabId })
                    }.filter { it.tabs.isNotEmpty() }
                }
                override fun switchTab(tabId: String) {
                    activeTabId.value = tabId
                }
                override fun getWebView(tabId: String?) = null
                override fun putWebView(tabId: String?, webView: android.webkit.WebView) {}
                override fun getAllWebViews() = emptyMap<String, android.webkit.WebView>()
                override fun removeWebView(tabId: String?) = null
                override fun updateTab(tabId: String, updater: (com.swift.browser.tabengine.model.TabModel) -> com.swift.browser.tabengine.model.TabModel) {
                    _groups.value = _groups.value.map { g ->
                        g.copy(tabs = g.tabs.map { if (it.id == tabId) updater(it) else it })
                    }
                }
                override fun updateGroup(groupId: String, updater: (com.swift.browser.tabengine.model.TabGroupModel) -> com.swift.browser.tabengine.model.TabGroupModel) {
                    _groups.value = _groups.value.map { if (it.id == groupId) updater(it) else it }
                }
                override fun createGroup(name: String, isIncognito: Boolean, color: Long?): com.swift.browser.tabengine.model.TabGroupModel {
                    val g = com.swift.browser.tabengine.model.TabGroupModel(id = java.util.UUID.randomUUID().toString(), name = name, isIncognito = isIncognito, color = color ?: 0xFFCCCCCC)
                    _groups.value = _groups.value + g
                    return g
                }
                override fun switchGroup(groupId: String) { activeGroupId.value = groupId }
                override fun closeGroup(groupId: String) { _groups.value = _groups.value.filterNot { it.id == groupId } }
                override fun moveTabToGroup(tabId: String, targetGroupId: String) {}
                override fun getActiveTab() = _groups.value.flatMap { it.tabs }.find { it.id == activeTabId.value }
                override fun getActiveGroup() = _groups.value.find { it.id == activeGroupId.value }
                override fun getTab(tabId: String) = _groups.value.flatMap { it.tabs }.find { it.id == tabId }
                override fun closeAllIncognitoTabs() { _groups.value = _groups.value.map { g -> g.copy(tabs = g.tabs.filterNot { it.isIncognito }) } }
                override fun saveSession() {}
                override fun restoreSession() = _groups.value
            }
        }
    }

    val searchEngine: com.swift.browser.searchengine.SearchEngine by lazy {
        com.swift.browser.analyticscore.StartupTracker.recordStage(
            stage = "SEARCH_ENGINE_CREATE",
            className = "SearchEngineProvider",
            methodName = "getEngine"
        )
        com.swift.browser.searchengine.SearchEngineProvider.getEngine()
    }
    val searchSuggestionsProvider by lazy { com.swift.browser.searchengine.SearchSuggestionsProvider(searchEngine) }
    private var textToSpeech: android.speech.tts.TextToSpeech? = null

    init {
        // Wire core tab engine state asynchronously so constructor finishes in <1ms
        viewModelScope.launch {
            tabEngine.activeTabId.collect { tabId ->
                val activeTab = tabId?.let { tabEngine.getTab(it) }
                uiState.update { current ->
                    current.copy(
                        activeTabId = tabId ?: "",
                        isIncognito = activeTab?.isIncognito ?: false
                    )
                }
            }
        }
        viewModelScope.launch(Dispatchers.Default) {
            preferenceManager?.let { prefs ->
                launch {
                    prefs.isPurgePrivateOnTimeoutOrExit.collect { enabled ->
                        privateModeEngine.isAutoPurgeOnTimeoutOrExit = enabled
                    }
                }
                launch {
                    prefs.biometricTimeoutSeconds.collect { seconds ->
                        privateModeEngine.biometricTimeoutMillis = seconds * 1000L
                    }
                }
            }
            launch {
                newsEngine.uiState.collect { newsState ->
                    uiState.update { current ->
                        current.copy(
                            feedCategory = newsState.feedCategory,
                            isFeedLoading = newsState.isFeedLoading,
                            articles = newsState.articles
                        )
                    }
                }
            }
            adProtectionApi?.let { adApi ->
                launch {
                    adApi.uiState.collect { adState ->
                        uiState.update { current ->
                            current.copy(
                                globalAdBlockEnabled = adState.globalAdBlockEnabled
                            )
                        }
                    }
                }
            }
        }
    }

    fun normalizeUrl(url: String): String {
        return com.swift.browser.browserengine.BrowserNavigationEngine.normalizeUrl(url)
    }

    fun shouldPerformRewrite(tabId: String, currentUrl: String, targetUrl: String): Boolean {
        val normCurrent = normalizeUrl(currentUrl)
        val normTarget = normalizeUrl(targetUrl)
        if (normCurrent.isEmpty() || normCurrent == normTarget) {
            return false
        }
        val lastRecord = lastRewriteCache[tabId]
        val now = System.currentTimeMillis()
        if (lastRecord != null && (now - lastRecord.timestamp) < 3000L) {
            val normLastSource = normalizeUrl(lastRecord.sourceUrl)
            val normLastTarget = normalizeUrl(lastRecord.targetUrl)
            val isSameDirection = normLastSource == normCurrent && normLastTarget == normTarget
            val isReverseDirection = normLastSource == normTarget && normLastTarget == normCurrent
            if (isSameDirection || isReverseDirection) {
                android.util.Log.w("BrowserViewModel", "Rewrite loop blocked for tab $tabId ($normCurrent <-> $normTarget)")
                return false
            }
        }
        lastRewriteCache[tabId] = RewriteRecord(currentUrl, targetUrl, now)
        return true
    }

    fun clearRewriteCache(tabId: String? = null) {
        if (tabId != null) {
            lastRewriteCache.remove(tabId)
        } else {
            lastRewriteCache.clear()
        }
    }

    fun showContextMenu(
        url: String,
        isImage: Boolean,
        isImageLink: Boolean = false,
        imageUrl: String = "",
        tabId: String? = null
    ) {
        contextMenuState.value = ContextMenuState(
            show = true,
            url = url,
            isImage = isImage,
            isImageLink = isImageLink,
            imageUrl = imageUrl,
            tabId = tabId
        )
    }

    fun dismissContextMenu() {
        contextMenuState.value = ContextMenuState(show = false)
    }

    fun handleIncomingIntent(intent: android.content.Intent?) {
        val url = intent?.dataString
        if (!url.isNullOrEmpty()) {
            addNewTab(url)
        }
    }

    fun captureActiveVideoState() {}

    fun saveTabsState() {
        tabEngine.saveSession()
    }

    fun syncPrivateModeSettings() {
        preferenceManager?.let { prefs ->
            privateModeEngine.isAutoPurgeOnTimeoutOrExit = prefs.isPurgePrivateOnTimeoutOrExit.value
            privateModeEngine.biometricTimeoutMillis = prefs.biometricTimeoutSeconds.value * 1000L
        }
    }

    fun purgeAllPrivateCacheAndCookies() {
        viewModelScope.launch {
            privateModeEngine.purgeAllPrivateCacheAndCookies()
        }
    }

    fun onAppPause() {
        syncPrivateModeSettings()
        privateModeEngine.onAppBackgrounded()
        privateModeEngine.lockPrivateTabs()
    }

    fun onAppResume() {
        syncPrivateModeSettings()
        privateModeEngine.onAppForegrounded()
        tabEngine.restoreSession()
    }

    fun onAppDestroy() {
        syncPrivateModeSettings()
        if (privateModeEngine.isAutoPurgeOnTimeoutOrExit) {
            viewModelScope.launch {
                privateModeEngine.onAppExit()
            }
        }
    }

    fun openUrl(urlOrQuery: String, inNewTab: Boolean = false, isIncognito: Boolean = false) {
        val selectedEngine = preferenceManager?.getString("default_search_engine", "Google") ?: "Google"
        val targetUrl = searchEngine.processInput(urlOrQuery, selectedEngine)
        val currentActiveId = tabEngine.activeTabId.value
        val currentTab = if (!currentActiveId.isNullOrEmpty()) tabEngine.getTab(currentActiveId) else null

        if (inNewTab || currentTab == null || currentTab.url == "swift://newtab" || currentTab.url == "swift://newtab-incognito" || currentTab.url.isEmpty()) {
            if (currentTab != null && (currentTab.url == "swift://newtab" || currentTab.url == "swift://newtab-incognito" || currentTab.url.isEmpty())) {
                tabEngine.updateTab(currentTab.id) { it.copy(url = targetUrl, title = targetUrl) }
                val wv = tabEngine.getWebView(currentTab.id)
                BrowserNavigationApi.navigate(
                    NavigationRequest(
                        tabId = currentTab.id,
                        url = targetUrl,
                        source = NavigationSource.USER_INPUT,
                        applyDesktopPolicy = true,
                        webView = wv
                    )
                )
            } else {
                val newTab = tabEngine.createTab(targetUrl, targetUrl, isIncognito)
                tabEngine.switchTab(newTab.id)
            }
        } else {
            tabEngine.updateTab(currentTab.id) { it.copy(url = targetUrl, title = targetUrl) }
            val wv = tabEngine.getWebView(currentTab.id)
            BrowserNavigationApi.navigate(
                NavigationRequest(
                    tabId = currentTab.id,
                    url = targetUrl,
                    source = NavigationSource.USER_INPUT,
                    applyDesktopPolicy = true,
                    webView = wv
                )
            )
        }
    }

    fun downloadActiveFile(url: String, mimetype: String = "", contentDisposition: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            val fileName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype)
            downloadEngine.startDownload(url, fileName, mimetype, 3)
        }
    }

    fun addNewTab(url: String) {
        val targetUrl = if (url == "swift://newtab" || url.startsWith("http://") || url.startsWith("https://") || url.startsWith("about:") || url.startsWith("swift://")) url else searchEngine.processInput(url)
        val newTab = tabEngine.createTab(targetUrl, if (targetUrl == "swift://newtab") "New Tab" else targetUrl)
        tabEngine.switchTab(newTab.id)
        extensionApi?.notifyTabCreated(newTab.id, targetUrl)
        extensionApi?.notifyTabActivated(newTab.id)
    }

    fun openInNewTabInGroup(url: String, parentTabId: String? = null) {
        val activeId = parentTabId ?: tabEngine.activeTabId.value
        val targetUrl = if (url == "swift://newtab" || url.startsWith("http://") || url.startsWith("https://") || url.startsWith("about:") || url.startsWith("swift://")) url else searchEngine.processInput(url)
        if (activeId != null) {
            val parentTab = tabEngine.getTab(activeId)
            var targetGroupId = parentTab?.groupId
            if (targetGroupId == null) {
                val newGroup = tabEngine.createGroup("Group " + (tabEngine.groups.value.size + 1), parentTab?.isIncognito ?: false)
                targetGroupId = newGroup.id
                if (parentTab != null) {
                    tabEngine.moveTabToGroup(parentTab.id, targetGroupId)
                }
            }
            val newTab = tabEngine.createTab(targetUrl, if (targetUrl == "swift://newtab") "New Tab" else targetUrl, parentTab?.isIncognito ?: false, targetGroupId)
            tabEngine.switchTab(newTab.id)
        } else {
            addNewTab(targetUrl)
        }
    }

    fun closeTab(tabId: String) {
        tabEngine.closeTab(tabId)
        extensionApi?.notifyTabRemoved(tabId)
    }

    fun setDownloadsOpen(open: Boolean) {}
    fun setHistoryOpen(open: Boolean) {}
    fun setBookmarksOpen(open: Boolean) {}

    fun createNewTabGroup(name: String, isPrivate: Boolean) {
        tabEngine.createGroup(name, isPrivate)
    }

    fun setGlobalAdBlockEnabled(enabled: Boolean) {
        adProtectionApi?.setGlobalAdBlockEnabled(enabled)
        uiState.value = uiState.value.copy(globalAdBlockEnabled = enabled)
    }

    fun setGlobalTrackersEnabled(enabled: Boolean) {
        adProtectionApi?.setGlobalTrackersEnabled(enabled)
    }


    fun toggleAdBlockForSite(url: String) {
        adProtectionApi?.toggleForSite(url)
    }

    fun addWhitelistedSite(domain: String) {
        adProtectionApi?.addWhitelistedSite(domain)
    }

    fun removeWhitelistedSite(domain: String) {
        adProtectionApi?.removeWhitelistedSite(domain)
    }

    fun addBlockedSite(domain: String) {
        adProtectionApi?.addBlockedSite(domain)
    }

    fun removeBlockedSite(domain: String) {
        adProtectionApi?.removeBlockedSite(domain)
    }

    fun updateAdBlockerRulesList() {
        adProtectionApi?.updateBlocklists()
    }

    fun incrementBlockedAdsCount(tabId: String) {
        adProtectionApi?.onAdBlocked(tabId)
    }

    fun translateActivePage(lang: String) {}

    fun deleteDownloads(ids: Set<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { id ->
                downloadEngine.deleteDownload(id)
            }
        }
    }

    fun renameDownload(id: Long, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            downloadEngine.renameDownload(id, newName)
        }
    }

    fun deleteBookmark(bookmark: com.swift.browser.bookmarkengine.Bookmark) {
        bookmarkEngine.deleteBookmark(bookmark)
    }

    fun addBookmarkExternally(url: String, title: String) {
        bookmarkEngine.addBookmark(url, title)
    }

    fun toggleBookmarkActive(url: String, title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            bookmarkEngine.toggleBookmark(url, title)
        }
    }

    fun addHistory(url: String, title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            historyEngine.addHistoryItem(url, title)
        }
    }

    fun deleteHistoryItem(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            historyEngine.deleteHistoryItem(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            historyEngine.clearAllHistory()
        }
    }

    fun addCustomShortcut(url: String, title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            topSiteEngine?.addCustomShortcut(url, title)
        }
    }

    fun deleteCustomShortcut(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            topSiteEngine?.removeTopSite(com.swift.browser.data.TopSite(url = url, title = "", isCustom = true))
        }
    }

    fun removeTopSite(topSite: com.swift.browser.data.TopSite) {
        viewModelScope.launch(Dispatchers.IO) {
            topSiteEngine?.removeTopSite(topSite)
        }
    }

    fun clearBrowsingData(
        clearHistory: Boolean,
        clearCookies: Boolean,
        clearCache: Boolean,
        clearDownloads: Boolean,
        context: android.content.Context,
        onComplete: ((ClearBrowsingDataResult) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            var histSuccess = true
            var cookieSuccess = true
            var cacheSuccess = true
            var dlSuccess = true
            val errorList = mutableListOf<String>()

            supervisorScope {
                if (clearHistory) {
                    launch {
                        try {
                            historyEngine.clearAllHistory()
                        } catch (e: Exception) {
                            histSuccess = false
                            errorList.add("History: ${e.message}")
                        }
                    }
                }
                if (clearCookies) {
                    launch {
                        try {
                            com.swift.browser.cookieengine.CookieEngineApi.getInstance(context).removeAllCookies()
                        } catch (e: Exception) {
                            cookieSuccess = false
                            errorList.add("Cookies: ${e.message}")
                        }
                    }
                }
                if (clearCache) {
                    launch {
                        try {
                            android.webkit.WebStorage.getInstance().deleteAllData()
                            withContext(Dispatchers.Main) {
                                try {
                                    val webView = android.webkit.WebView(context)
                                    webView.clearCache(true)
                                    webView.destroy()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        } catch (e: Exception) {
                            cacheSuccess = false
                            errorList.add("Cache: ${e.message}")
                        }
                    }
                }
                if (clearDownloads) {
                    launch {
                        try {
                            downloads.value.forEach { item ->
                                downloadEngine.deleteDownload(item.id)
                            }
                        } catch (e: Exception) {
                            dlSuccess = false
                            errorList.add("Downloads: ${e.message}")
                        }
                    }
                }
            }

            val result = ClearBrowsingDataResult(
                historySuccess = histSuccess,
                cookiesSuccess = cookieSuccess,
                cacheSuccess = cacheSuccess,
                downloadsSuccess = dlSuccess,
                errors = errorList
            )
            onComplete?.invoke(result)
        }
    }

    fun startListeningToPageText(
        context: android.content.Context? = null,
        webView: android.webkit.WebView? = null
    ) {
        if (context == null || webView == null) return
        webView.evaluateJavascript(
            "(function() { return document.body ? (document.body.innerText || document.body.textContent || '') : ''; })()"
        ) { result ->
            val text = result?.removeSurrounding("\"")?.replace("\\n", "\n")?.replace("\\\"", "\"")?.trim() ?: ""
            if (text.isNotBlank()) {
                if (textToSpeech == null) {
                    textToSpeech = android.speech.tts.TextToSpeech(context.applicationContext) { status ->
                        if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                            textToSpeech?.language = java.util.Locale.getDefault()
                            textToSpeech?.speak(text.take(4000), android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "tts_page")
                        }
                    }
                } else {
                    textToSpeech?.speak(text.take(4000), android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "tts_page")
                }
                android.widget.Toast.makeText(context, "Reading page aloud...", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(context, "No text found on page to read", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun stopListeningToPageText() {
        textToSpeech?.stop()
    }

    override fun onCleared() {
        super.onCleared()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}

