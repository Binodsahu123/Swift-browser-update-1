package com.swift.browser.browserengine.ui

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.swift.browser.browserengine.BrowserUiState
import com.swift.browser.browserengine.BrowserViewModel
import com.swift.browser.browserengine.BrowserWebView
import com.swift.browser.browserengine.BrowserWebViewController
import com.swift.browser.desktopengine.api.DesktopEngineProvider
import com.swift.browser.browserengine.ErrorPageEngine
import com.swift.browser.browserengine.FindInPageEngine
import com.swift.browser.browserengine.PrintEngine
import com.swift.browser.browserengine.util.ShortcutUtils
import com.swift.browser.permissionengine.ui.PermissionPromptDialog
import com.swift.browser.data.TopSite
import com.swift.browser.developertoolsengine.DeveloperPanelComponent
import com.swift.browser.tabengine.model.TabGroupModel
import com.swift.browser.tabengine.model.TabModel
import com.swift.browser.vpnengine.api.VpnEngineProvider
import com.swift.browser.vpnengine.domain.VpnConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val uiState by viewModel.uiState.collectAsState()
    val downloadsList by viewModel.downloads.collectAsState()
    val bookmarksList by viewModel.bookmarks.collectAsState()
    val historyList by viewModel.history.collectAsState()
    val groupsList by viewModel.tabEngine.groups.collectAsState()
    val newsState by viewModel.newsEngine.uiState.collectAsState()
    val translateUiState by viewModel.translateEngine.uiState.collectAsState()

    val activeTabId by viewModel.tabEngine.activeTabId.collectAsState()

    // Screen and Overlay states
    var activeQuickTool by remember { mutableStateOf<String?>(null) }
    var pendingToolLaunch by remember { mutableStateOf<String?>(null) }
    var isTabSwitcherOpen by remember { mutableStateOf(false) }
    var isBookmarksOpen by remember { mutableStateOf(false) }
    var isHistoryOpen by remember { mutableStateOf(false) }
    var isDownloadsOpen by remember { mutableStateOf(false) }
    var isPasswordManagerOpen by remember { mutableStateOf(false) }
    var isDevToolsOpen by remember { mutableStateOf(false) }
    var isAiPanelOpen by remember { mutableStateOf(false) }
    var isVoiceOverlayOpen by remember { mutableStateOf(false) }
    var isReaderModeOpen by remember { mutableStateOf(false) }
    var isTranslateBarOpen by remember { mutableStateOf(false) }
    var isAboutDialogOpen by remember { mutableStateOf(false) }
    var isFindInPageOpen by remember { mutableStateOf(false) }
    var findInPageQuery by remember { mutableStateOf("") }
    var showBrowserMenu by remember { mutableStateOf(false) }
    var isUrlEditing by remember { mutableStateOf(false) }
    var urlInputText by remember { mutableStateOf("") }

    LaunchedEffect(isUrlEditing) {
        if (isUrlEditing) {
            viewModel.scrollChromeController.reset()
        }
    }

    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == "com.swift.browser.ACTION_CLEAR_HISTORY") {
                    coroutineScope.launch {
                        viewModel.clearAllHistory()
                    }
                }
            }
        }
        val filter = android.content.IntentFilter("com.swift.browser.ACTION_CLEAR_HISTORY")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
    val contextMenuState by viewModel.contextMenuState.collectAsState()

    // Dialog states
    var showSslDialog by remember { mutableStateOf(false) }
    var showAddShortcutDialog by remember { mutableStateOf(false) }
    var showRecentTabsDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showHelpFeedbackDialog by remember { mutableStateOf(false) }
    var showExtensionsDialog by remember { mutableStateOf(false) }
    var showWebNotificationsDialog by remember { mutableStateOf(false) }
    var showMediaDetectedDialog by remember { mutableStateOf(false) }
    var showOrionAiAssistantBottomSheet by remember { mutableStateOf(false) }
    var showPrintPreviewDialog by remember { mutableStateOf(false) }

    // Extension Engine Integration
    val extensionApi = remember(context) { com.swift.browser.extensionengine.ExtensionEngineApi.getInstance(context) }
    val extensionUiState by extensionApi.uiState.collectAsState()

    val findInPageEngine = remember { FindInPageEngine() }
    val findInPageState by findInPageEngine.state.collectAsState()

    // Speech Recognizer Launcher for Voice Search
    val speechRecognizerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrEmpty()) {
                val formatted = if (spokenText.startsWith("http://") || spokenText.startsWith("https://")) spokenText
                else "https://www.google.com/search?q=${java.net.URLEncoder.encode(spokenText, "UTF-8")}"
                viewModel.addNewTab(formatted)
            }
        }
    }

    val webViewController = remember(context) { BrowserWebViewController(context) }

    val currentTabId = activeTabId
    val activeTab = remember(groupsList, currentTabId) {
        val found = if (currentTabId != null) viewModel.tabEngine.getTab(currentTabId) else null
        found ?: groupsList.flatMap { it.tabs }.find { it.id == currentTabId }
            ?: groupsList.flatMap { it.tabs }.firstOrNull()
            ?: TabModel(id = "default", url = "swift://newtab", title = "New Tab")
    }

    val tabNavigationHistory = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    androidx.compose.runtime.LaunchedEffect(activeTab.id) {
        if (activeTab.id != "default") {
            tabNavigationHistory.remove(activeTab.id)
            tabNavigationHistory.add(activeTab.id)
        }
    }
    androidx.compose.runtime.LaunchedEffect(groupsList) {
        val existingIds = groupsList.flatMap { it.tabs }.map { it.id }.toSet()
        tabNavigationHistory.retainAll(existingIds)
    }

    val activeTabGroup = remember(groupsList, activeTab) {
        groupsList.find { group -> group.tabs.any { it.id == activeTab.id } } ?: groupsList.firstOrNull()
    }

    val currentWebUrl = activeTab.url
    val currentWebTitle = activeTab.title
    val webProgress = activeTab.progress
    val currentFavicon = activeTab.favicon
    val desktopState by DesktopEngineProvider.api.getTabState(activeTab.id).collectAsState()
    val isDesktopMode = desktopState.isDesktopModeEnabled
    val isBiometricUnlocked by viewModel.isBiometricUnlocked.collectAsStateWithLifecycle()
    var activeWebView by remember { mutableStateOf<BrowserWebView?>(null) }

    val defaultTopSites = remember {
        listOf(
            TopSite(id = 1, url = "https://www.google.com", title = "Google"),
            TopSite(id = 2, url = "https://www.wikipedia.org", title = "Wikipedia"),
            TopSite(id = 3, url = "https://github.com", title = "GitHub"),
            TopSite(id = 4, url = "https://reddit.com", title = "Reddit"),
            TopSite(id = 5, url = "https://news.ycombinator.com", title = "Hacker News"),
            TopSite(id = 6, url = "swift://diagnostics", title = "Diagnostics")
        )
    }
    val realTopSites by viewModel.topSites.collectAsState()
    val topSitesList = if (realTopSites.isNotEmpty()) realTopSites else defaultTopSites

    val isPageBookmarked = remember(bookmarksList, currentWebUrl) {
        bookmarksList.any { it.url == currentWebUrl }
    }

    val totalTabsCount = remember(groupsList) {
        groupsList.sumOf { it.tabs.size }.coerceAtLeast(1)
    }

    val isNewTab = activeTab.url.startsWith("swift://newtab") || activeTab.url.isEmpty() || activeTab.url == "about:blank"
    val isDiagnosticsPage = activeTab.url.startsWith("swift://diagnostics")

    // File Chooser Launcher
    var fileUploadCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        fileUploadCallback?.onReceiveValue(uris)
        fileUploadCallback = null
    }

    val canGoBackInWebView = activeWebView?.canGoBack() == true
    val hasActiveOverlays = com.swift.browser.videoengine.api.VideoEngineApi.isCustomViewShowing() ||
            extensionUiState.showPopupBottomSheet ||
            extensionUiState.showActiveHubDialog ||
            extensionUiState.showManagerOverlay ||
            extensionUiState.showStoreScreen ||
            showSslDialog ||
            showAddShortcutDialog ||
            showRecentTabsDialog ||
            showClearDataDialog ||
            showHelpFeedbackDialog ||
            showExtensionsDialog ||
            showWebNotificationsDialog ||
            isAboutDialogOpen ||
            contextMenuState.show ||
            activeQuickTool != null ||
            pendingToolLaunch != null ||
            isReaderModeOpen ||
            isFindInPageOpen ||
            isTranslateBarOpen ||
            translateUiState.isVisible ||
            isVoiceOverlayOpen ||
            showBrowserMenu ||
            isDevToolsOpen ||
            isAiPanelOpen ||
            isBookmarksOpen ||
            isHistoryOpen ||
            isDownloadsOpen ||
            isPasswordManagerOpen ||
            isTabSwitcherOpen ||
            isUrlEditing

    val canGoBackInHistory = tabNavigationHistory.size > 1

    // BackHandler: handles overlays, webview back navigation, and returning to home/newtab
    BackHandler(
        enabled = hasActiveOverlays || canGoBackInWebView || !isNewTab || canGoBackInHistory
    ) {
        when {
            com.swift.browser.videoengine.api.VideoEngineApi.isCustomViewShowing() -> {
                val activity = context as? android.app.Activity
                if (activity != null) {
                    com.swift.browser.videoengine.api.VideoEngineApi.hideCustomView(activity)
                }
            }
            extensionUiState.showPopupBottomSheet -> extensionApi.closePopup()
            extensionUiState.showActiveHubDialog -> extensionApi.closeActiveHub()
            extensionUiState.showManagerOverlay -> extensionApi.closeManagerUi()
            extensionUiState.showStoreScreen -> extensionApi.closeStoreScreen()
            showSslDialog -> showSslDialog = false
            showAddShortcutDialog -> showAddShortcutDialog = false
            showRecentTabsDialog -> showRecentTabsDialog = false
            showClearDataDialog -> showClearDataDialog = false
            showHelpFeedbackDialog -> showHelpFeedbackDialog = false
            showExtensionsDialog -> showExtensionsDialog = false
            showWebNotificationsDialog -> showWebNotificationsDialog = false
            contextMenuState.show -> viewModel.dismissContextMenu()
            isAboutDialogOpen -> isAboutDialogOpen = false
            activeQuickTool != null -> activeQuickTool = null
            pendingToolLaunch != null -> pendingToolLaunch = null
            isReaderModeOpen -> isReaderModeOpen = false
            isFindInPageOpen -> {
                isFindInPageOpen = false
                findInPageEngine.toggleFindInPage(false, activeWebView)
            }
            isTranslateBarOpen -> {
                isTranslateBarOpen = false
                viewModel.translateEngine.dismissTranslateBar(activeWebView, activeTab.id, currentWebUrl)
            }
            translateUiState.isVisible -> {
                viewModel.translateEngine.dismissTranslateBar(activeWebView, activeTab.id, currentWebUrl)
            }
            isVoiceOverlayOpen -> isVoiceOverlayOpen = false
            showBrowserMenu -> showBrowserMenu = false
            isDevToolsOpen -> isDevToolsOpen = false
            isAiPanelOpen -> isAiPanelOpen = false
            isBookmarksOpen -> isBookmarksOpen = false
            isHistoryOpen -> isHistoryOpen = false
            isDownloadsOpen -> isDownloadsOpen = false
            isPasswordManagerOpen -> isPasswordManagerOpen = false
            isTabSwitcherOpen -> isTabSwitcherOpen = false
            isUrlEditing -> {
                isUrlEditing = false
                focusManager.clearFocus()
            }
            activeWebView?.canGoBack() == true -> {
                val wv = activeWebView
                if (wv != null && wv.canGoBack()) {
                    val historyList = wv.copyBackForwardList()
                    val currentIndex = historyList.currentIndex
                    if (currentIndex > 0) {
                        val currentItemUrl = historyList.getItemAtIndex(currentIndex)?.url.orEmpty()
                        var stepsBack = -1
                        while (currentIndex + stepsBack >= 0) {
                            val prevUrl = historyList.getItemAtIndex(currentIndex + stepsBack)?.url.orEmpty()
                            if (prevUrl.isNotEmpty() && prevUrl != currentItemUrl && prevUrl != "about:blank" && !prevUrl.startsWith("swift://newtab")) {
                                break
                            }
                            stepsBack--
                        }
                        if (currentIndex + stepsBack >= 0) {
                            wv.goBackOrForward(stepsBack)
                        } else {
                            wv.stopLoading()
                            viewModel.tabEngine.updateTab(activeTab.id) { it.copy(url = "swift://newtab", title = "New Tab") }
                        }
                    } else {
                        wv.stopLoading()
                        viewModel.tabEngine.updateTab(activeTab.id) { it.copy(url = "swift://newtab", title = "New Tab") }
                    }
                } else {
                    activeWebView?.stopLoading()
                    viewModel.tabEngine.updateTab(activeTab.id) { it.copy(url = "swift://newtab", title = "New Tab") }
                }
            }
            !isNewTab -> {
                activeWebView?.stopLoading()
                viewModel.tabEngine.updateTab(activeTab.id) { it.copy(url = "swift://newtab", title = "New Tab") }
            }
            canGoBackInHistory -> {
                tabNavigationHistory.remove(activeTab.id)
                viewModel.tabEngine.closeTab(activeTab.id)
                if (tabNavigationHistory.isNotEmpty()) {
                    viewModel.tabEngine.switchTab(tabNavigationHistory.last())
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0F172A))) {
        // Main Navigation Layers & Overlays
        when {
            activeQuickTool != null -> {
                QuickToolsOverlay(
                    toolName = activeQuickTool!!,
                    viewModel = viewModel,
                    onClose = { activeQuickTool = null }
                )
            }

            pendingToolLaunch == "video" -> {
                com.swift.browser.videoengine.ui.VideoCenterHomeScreen(
                    onBack = { pendingToolLaunch = null }
                )
            }

            pendingToolLaunch == "music" -> {
                com.swift.browser.audioengine.AudioCenterHomeScreen(
                    onBack = { pendingToolLaunch = null }
                )
            }

            isReaderModeOpen -> {
                val readerState by viewModel.readerEngine.readerState.collectAsState()
                LaunchedEffect(activeTab.id, currentWebUrl) {
                    activeWebView?.let { webView ->
                        viewModel.readerEngine.triggerReaderMode(webView, activeTab.id)
                    }
                }
                com.swift.browser.readerengine.ui.ReaderModeScreen(
                    state = readerState,
                    onClose = {
                        viewModel.readerEngine.closeReaderMode()
                        isReaderModeOpen = false
                    },
                    onUpdateFontSize = { size -> viewModel.readerEngine.updateReaderFontSize(size) },
                    onUpdateTheme = { theme -> viewModel.readerEngine.updateReaderTheme(theme) },
                    onUpdateTypeface = { isSerif -> viewModel.readerEngine.updateReaderTypeface(isSerif) }
                )
            }


            isBookmarksOpen -> {
                com.swift.browser.bookmarkengine.ui.BookmarksOverlay(
                    bookmarks = bookmarksList,
                    onDismiss = { isBookmarksOpen = false },
                    onNavigate = { url ->
                        viewModel.openUrl(url)
                        isBookmarksOpen = false
                    },
                    onDelete = { bookmark ->
                        viewModel.deleteBookmark(bookmark)
                    }
                )
            }

            isHistoryOpen -> {
                com.swift.browser.historyengine.ui.HistoryOverlay(
                    history = historyList,
                    onDismiss = { isHistoryOpen = false },
                    onNavigate = { url ->
                        viewModel.openUrl(url)
                        isHistoryOpen = false
                    },
                    onDelete = { id ->
                        viewModel.deleteHistoryItem(id)
                    },
                    onClearAll = {
                        viewModel.clearAllHistory()
                    },
                    onClearBrowsingData = { clearHistory, clearCookies, clearCache, _ ->
                        viewModel.clearBrowsingData(clearHistory, clearCookies, clearCache, false, context)
                    }
                )
            }

            isDownloadsOpen -> {
                com.swift.browser.downloadengine.ui.DownloadsOverlay(
                    downloads = downloadsList,
                    onDismiss = { isDownloadsOpen = false },
                    onOpenFile = { filePath, mimeType, _ ->
                        try {
                            val file = java.io.File(filePath)
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, mimeType)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open file", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDeleteDownloads = { ids -> viewModel.deleteDownloads(ids) },
                    onDeleteDownload = { id -> viewModel.deleteDownloads(setOf(id)) },
                    onRenameDownloadFile = { id, _, newName ->
                        viewModel.renameDownload(id, newName)
                        true
                    }
                )
            }

            isPasswordManagerOpen -> {
                Dialog(
                    onDismissRequest = { isPasswordManagerOpen = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        com.swift.browser.passwordengine.ui.PasswordManagerScreen(
                            viewModel = viewModel(),
                            onNavigateBack = { isPasswordManagerOpen = false }
                        )
                    }
                }
            }

            isAiPanelOpen -> {
                AIChatPanel(
                    tabId = activeTab.id,
                    url = if (isNewTab) "swift://newtab" else currentWebUrl,
                    pageText = currentWebTitle,
                    onDismiss = { isAiPanelOpen = false },
                    viewModel = viewModel
                )
            }

            isDevToolsOpen -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { isDevToolsOpen = false },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(modifier = Modifier.clickable(enabled = false) {}) {
                        DeveloperPanelComponent(
                            htmlContent = currentWebTitle,
                            onClose = { isDevToolsOpen = false }
                        )
                    }
                }
            }

            else -> {
                val mainScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isTabSwitcherOpen) 0.92f else 1f,
                    animationSpec = com.swift.browser.tabengine.animation.TabAnimationTransitions.FluidSpring,
                    label = "main_content_scale"
                )
                val mainAlpha by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isTabSwitcherOpen) 0.4f else 1f,
                    animationSpec = com.swift.browser.tabengine.animation.TabAnimationTransitions.SmoothSpring,
                    label = "main_content_alpha"
                )

                // Main Browser Shell: New Tab Page or Real WebView with Browser Shell Controls
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = mainScale
                            scaleY = mainScale
                            alpha = mainAlpha
                        }
                ) {
                    if (isDiagnosticsPage) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            WebRTCCompatibilityDiagnosticsScreen(
                                onClose = {
                                    viewModel.tabEngine.updateTab(activeTab.id) { it.copy(url = "swift://newtab", title = "New Tab") }
                                },
                                onNavigateToUrl = { url ->
                                    viewModel.openUrl(url)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else if (isNewTab) {
                        // NEW TAB SCREEN WITH INTEGRATED QUICK ACCESS TOOLS BAR
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            NewTabScreen(
                                state = uiState,
                                newsState = newsState,
                                activeTab = activeTab,
                                tabCount = totalTabsCount,
                                topSites = topSitesList,
                                recentHistory = historyList,
                                onSearch = { query ->
                                    viewModel.openUrl(query)
                                },
                                onRequestSearchFocus = {
                                    urlInputText = ""
                                    isUrlEditing = true
                                },
                                onArticleClick = { url -> viewModel.openUrl(url) },
                                onCategorySelected = { category -> viewModel.newsEngine.selectCategory(category) },
                                onAddShortcut = { title, url -> viewModel.addCustomShortcut(url, title) },
                                onRemoveTopSite = { topSite -> viewModel.removeTopSite(topSite) },
                                onTabSwitcherClick = { isTabSwitcherOpen = true },
                                onDownloadsClick = { isDownloadsOpen = true },
                                onHistoryClick = { isHistoryOpen = true },
                                onBookmarksClick = { isBookmarksOpen = true },
                                onAIChatClick = { isAiPanelOpen = true },
                                onQuickToolSelected = { tool ->
                                    if (tool == "video" || tool == "music") {
                                        pendingToolLaunch = tool
                                    } else {
                                        activeQuickTool = tool
                                    }
                                },
                                optionsMenuContent = { expanded, onDismiss ->
                                    DropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = onDismiss
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("New Tab") },
                                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                            onClick = {
                                                onDismiss()
                                                viewModel.addNewTab("swift://newtab")
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("New Private Tab") },
                                            leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null) },
                                            onClick = {
                                                onDismiss()
                                                viewModel.tabEngine.createTab("swift://newtab", "Private Tab", isIncognito = true)
                                            }
                                        )
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text("History") },
                                            leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                                            onClick = {
                                                onDismiss()
                                                isHistoryOpen = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Bookmarks") },
                                            leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null) },
                                            onClick = {
                                                onDismiss()
                                                isBookmarksOpen = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Downloads") },
                                            leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                                            onClick = {
                                                onDismiss()
                                                isDownloadsOpen = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Web Notifications") },
                                            leadingIcon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                                            onClick = {
                                                onDismiss()
                                                showWebNotificationsDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Recent Tabs") },
                                            leadingIcon = { Icon(Icons.Default.Tab, contentDescription = null) },
                                            onClick = {
                                                onDismiss()
                                                showRecentTabsDialog = true
                                            }
                                        )
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text("Swift AI Assistant") },
                                            leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                                            onClick = {
                                                onDismiss()
                                                isAiPanelOpen = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Extensions") },
                                            leadingIcon = { Icon(Icons.Default.Extension, contentDescription = null) },
                                            onClick = {
                                                onDismiss()
                                                extensionApi.openManagerUi()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Clear Browsing Data") },
                                            leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                                            onClick = {
                                                onDismiss()
                                                showClearDataDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Help & Feedback") },
                                            leadingIcon = { Icon(Icons.Default.HelpOutline, contentDescription = null) },
                                            onClick = {
                                                onDismiss()
                                                showHelpFeedbackDialog = true
                                            }
                                        )
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text("Settings") },
                                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                            onClick = {
                                                onDismiss()
                                                context.startActivity(Intent(context, com.swift.browser.settingsengine.AdvancedSettingsActivity::class.java))
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("About Swift Browser") },
                                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                            onClick = {
                                                onDismiss()
                                                isAboutDialogOpen = true
                                            }
                                        )
                                    }
                                },
                                onVoiceClick = {
                                    try {
                                        val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
                                            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak to search...")
                                        }
                                        speechRecognizerLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Voice recognition not available", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Bottom Quick Access Tools Bar (Video, Music, Swift AI, Editing, Learn & Earn)
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isNewTab && !isTabSwitcherOpen,
                            enter = androidx.compose.animation.expandVertically(
                                animationSpec = androidx.compose.animation.core.tween(
                                    durationMillis = 200,
                                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                                )
                            ) + androidx.compose.animation.fadeIn(
                                animationSpec = androidx.compose.animation.core.tween(200)
                            ),
                            exit = androidx.compose.animation.shrinkVertically(
                                animationSpec = androidx.compose.animation.core.tween(
                                    durationMillis = 200,
                                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                                )
                            ) + androidx.compose.animation.fadeOut(
                                animationSpec = androidx.compose.animation.core.tween(200)
                            )
                        ) {
                            QuickAccessToolsBar(
                                onToolSelect = { tool ->
                                    if (tool == "video" || tool == "music") {
                                        pendingToolLaunch = tool
                                    } else {
                                        activeQuickTool = tool
                                    }
                                },
                                isIncognito = activeTab.isIncognito,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        // ACTIVE WEBPAGE HOST WITH REAL WEBVIEW, EXACT OLD TOP ADDRESS BAR & CHROME
                        val hideProgress by viewModel.scrollChromeController.hideProgress.collectAsState()
                        // 1. Top URL / Navigation Bar (Exact Old Design with Scroll Hide)
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            tonalElevation = 4.dp,
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .layout { measurable, constraints ->
                                    val placeable = measurable.measure(constraints)
                                    val offset = (placeable.height * hideProgress).toInt()
                                    layout(placeable.width, (placeable.height - offset).coerceAtLeast(0)) {
                                        placeable.placeRelative(0, -offset)
                                    }
                                }
                                .graphicsLayer {
                                    alpha = 1f - (hideProgress * 0.5f)
                                    translationY = -size.height * hideProgress
                                }
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Go Home Button
                                    IconButton(
                                        onClick = {
                                            focusManager.clearFocus()
                                            viewModel.addNewTab("swift://newtab")
                                        },
                                        modifier = Modifier.size(36.dp).testTag("omnibox_home")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Home,
                                            contentDescription = "Go Home",
                                            tint = LocalContentColor.current,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    // URL Input Bar
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                            .padding(horizontal = 4.dp),
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Security Lock Indicator
                                            val isHttps = currentWebUrl.startsWith("https://")
                                            IconButton(
                                                onClick = { showSslDialog = true },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isHttps) Icons.Default.Lock else Icons.Default.LockOpen,
                                                    contentDescription = "SSL Status",
                                                    tint = if (isHttps) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }

                                            if (isDesktopMode) {
                                                Text("🖥", modifier = Modifier.padding(start = 2.dp), fontSize = 12.sp)
                                            }

                                            Spacer(modifier = Modifier.width(6.dp))

                                            if (isUrlEditing) {
                                                androidx.compose.foundation.text.BasicTextField(
                                                    value = urlInputText,
                                                    onValueChange = { urlInputText = it },
                                                    singleLine = true,
                                                    textStyle = LocalTextStyle.current.copy(
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontSize = 12.sp
                                                    ),
                                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                                    keyboardActions = KeyboardActions(
                                                        onGo = {
                                                            isUrlEditing = false
                                                            focusManager.clearFocus()
                                                            val formatted = viewModel.searchEngine.processInput(urlInputText)
                                                            viewModel.tabEngine.updateTab(activeTab.id) { it.copy(url = formatted, title = formatted) }
                                                            com.swift.browser.browserengine.BrowserNavigationApi.navigate(
                                                                com.swift.browser.browserengine.NavigationRequest(
                                                                    tabId = activeTab.id,
                                                                    url = formatted,
                                                                    source = com.swift.browser.browserengine.NavigationSource.USER_INPUT,
                                                                    applyDesktopPolicy = true,
                                                                    webView = activeWebView
                                                                )
                                                            )
                                                        }
                                                    ),
                                                    modifier = Modifier.weight(1f).testTag("url_input_bar")
                                                )

                                                IconButton(
                                                    onClick = {
                                                        isUrlEditing = false
                                                        focusManager.clearFocus()
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Clear",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            } else {
                                                Text(
                                                    text = currentWebUrl.replace("https://", "").replace("http://", ""),
                                                    style = LocalTextStyle.current.copy(
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontSize = 12.sp
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable {
                                                            urlInputText = currentWebUrl
                                                            isUrlEditing = true
                                                        }
                                                )

                                                // Reader Mode trigger
                                                IconButton(
                                                    onClick = { isReaderModeOpen = true },
                                                    modifier = Modifier.size(24.dp).testTag("reader_mode_trigger")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.MenuBook,
                                                        contentDescription = "Reader Mode",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                // Bookmark Star toggle
                                                IconButton(
                                                    onClick = {
                                                        viewModel.toggleBookmarkActive(currentWebUrl, currentWebTitle)
                                                        Toast.makeText(context, if (isPageBookmarked) "Bookmark removed" else "Bookmark saved", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(24.dp).testTag("bookmark_star_toggle")
                                                ) {
                                                    Icon(
                                                        imageVector = if (isPageBookmarked) Icons.Default.Star else Icons.Default.StarBorder,
                                                        contentDescription = "Bookmark Page",
                                                        tint = if (isPageBookmarked) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // AI Button placed beside tab counter and menu
                                    IconButton(
                                        onClick = { showOrionAiAssistantBottomSheet = true },
                                        modifier = Modifier.size(36.dp).testTag("ai_star_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = "Active Page Summary Assistant",
                                            tint = LocalContentColor.current,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    // Tab Switcher with tab count badge (Exact Old Circle Badge Design)
                                    Box(
                                        contentAlignment = Alignment.BottomEnd,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clickable { isTabSwitcherOpen = true }
                                    ) {
                                        IconButton(
                                            onClick = { isTabSwitcherOpen = true },
                                            modifier = Modifier.size(36.dp).testTag("tab_switcher_btn")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Tab,
                                                contentDescription = "Tabs List",
                                                tint = LocalContentColor.current,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Surface(
                                            modifier = Modifier
                                                .padding(bottom = 2.dp, end = 2.dp)
                                                .size(14.dp),
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = totalTabsCount.toString(),
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    // PDF Print Icon
                                    IconButton(
                                        onClick = { showPrintPreviewDialog = true },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PictureAsPdf,
                                            contentDescription = "Save Page to PDF",
                                            tint = Color(0xFFF43F5E),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    // 3-Dot Options Menu
                                    Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                        IconButton(
                                            onClick = { showBrowserMenu = true },
                                            modifier = Modifier.size(36.dp).testTag("menu_nav_btn")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "Options Menu",
                                                tint = LocalContentColor.current,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = showBrowserMenu,
                                            onDismissRequest = { showBrowserMenu = false }
                                        ) {
                                            // Top quick row: Back, Forward, Refresh/Stop, Bookmark, Site Info
                                            Row(
                                                modifier = Modifier
                                                    .width(240.dp)
                                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        showBrowserMenu = false
                                                        val wv = activeWebView
                                                        if (wv != null && wv.canGoBack()) {
                                                            val historyList = wv.copyBackForwardList()
                                                            val currentIndex = historyList.currentIndex
                                                            if (currentIndex > 0) {
                                                                val currentItemUrl = historyList.getItemAtIndex(currentIndex)?.url.orEmpty()
                                                                var stepsBack = -1
                                                                while (currentIndex + stepsBack >= 0) {
                                                                    val prevUrl = historyList.getItemAtIndex(currentIndex + stepsBack)?.url.orEmpty()
                                                                    if (prevUrl.isNotEmpty() && prevUrl != currentItemUrl && prevUrl != "about:blank" && !prevUrl.startsWith("swift://newtab")) {
                                                                        break
                                                                    }
                                                                    stepsBack--
                                                                }
                                                                if (currentIndex + stepsBack >= 0) {
                                                                    wv.goBackOrForward(stepsBack)
                                                                } else {
                                                                    wv.stopLoading()
                                                                    viewModel.tabEngine.updateTab(activeTab.id) { it.copy(url = "swift://newtab", title = "New Tab") }
                                                                }
                                                            } else {
                                                                wv.stopLoading()
                                                                viewModel.tabEngine.updateTab(activeTab.id) { it.copy(url = "swift://newtab", title = "New Tab") }
                                                            }
                                                        } else {
                                                            activeWebView?.stopLoading()
                                                            viewModel.tabEngine.updateTab(activeTab.id) { it.copy(url = "swift://newtab", title = "New Tab") }
                                                        }
                                                    },
                                                    enabled = activeWebView?.canGoBack() == true
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                        contentDescription = "Back",
                                                        tint = if (activeWebView?.canGoBack() == true) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.3f)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        showBrowserMenu = false
                                                        activeWebView?.goForward()
                                                    },
                                                    enabled = activeWebView?.canGoForward() == true
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                        contentDescription = "Forward",
                                                        tint = if (activeWebView?.canGoForward() == true) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.3f)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        showBrowserMenu = false
                                                        if (webProgress in 1..99) {
                                                            activeWebView?.stopLoading()
                                                        } else {
                                                            activeWebView?.reload()
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = if (webProgress in 1..99) Icons.Default.Close else Icons.Default.Refresh,
                                                        contentDescription = if (webProgress in 1..99) "Stop" else "Refresh"
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        showBrowserMenu = false
                                                        viewModel.toggleBookmarkActive(currentWebUrl, currentWebTitle)
                                                        Toast.makeText(context, if (isPageBookmarked) "Bookmark removed" else "Bookmark saved", Toast.LENGTH_SHORT).show()
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = if (isPageBookmarked) Icons.Default.Star else Icons.Default.StarBorder,
                                                        contentDescription = "Bookmark",
                                                        tint = if (isPageBookmarked) Color(0xFFFFD700) else LocalContentColor.current
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        showBrowserMenu = false
                                                        showSslDialog = true
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Info,
                                                        contentDescription = "Info"
                                                    )
                                                }
                                            }

                                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                            DropdownMenuItem(
                                                text = { Text("New tab") },
                                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                                onClick = {
                                                    showBrowserMenu = false
                                                    viewModel.addNewTab("swift://newtab")
                                                }
                                            )

                                            DropdownMenuItem(
                                                text = { Text("New private tab") },
                                                leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null) },
                                                onClick = {
                                                    showBrowserMenu = false
                                                    val privateEngine = com.swift.browser.privatemode.PrivateModeEngineProvider.getEngine(context)
                                                 val session = privateEngine.startPrivateSession()
                                                 val tab = viewModel.tabEngine.createTab("swift://newtab", "Private Tab", isIncognito = true)
                                                 privateEngine.registerPrivateTab(tab.id, session.id)
                                                }
                                            )

                                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                            DropdownMenuItem(
                                                text = { Text("History") },
                                                leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                                                onClick = {
                                                    showBrowserMenu = false
                                                    isHistoryOpen = true
                                                }
                                            )

                                            DropdownMenuItem(
                                                text = { Text("Bookmarks") },
                                                leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                                                onClick = {
                                                    showBrowserMenu = false
                                                    isBookmarksOpen = true
                                                }
                                            )

                                            DropdownMenuItem(
                                                text = { Text("Downloads") },
                                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                                onClick = {
                                                    showBrowserMenu = false
                                                    isDownloadsOpen = true
                                                }
                                            )

                                            DropdownMenuItem(
                                                text = { Text("Web Notifications") },
                                                leadingIcon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                                                onClick = {
                                                    showBrowserMenu = false
                                                    showWebNotificationsDialog = true
                                                }
                                            )

                                            DropdownMenuItem(
                                                text = { Text("Recent tabs") },
                                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                                onClick = {
                                                    showBrowserMenu = false
                                                    showRecentTabsDialog = true
                                                }
                                            )

                                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                            DropdownMenuItem(
                                                text = { Text("Find in page") },
                                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                                onClick = {
                                                    showBrowserMenu = false
                                                    isFindInPageOpen = true
                                                    findInPageEngine.toggleFindInPage(true, activeWebView)
                                                }
                                            )

                                            DropdownMenuItem(
                                                text = { Text("Add to Home screen") },
                                                leadingIcon = { Icon(Icons.Default.Launch, contentDescription = null) },
                                                onClick = {
                                                    showBrowserMenu = false
                                                    showAddShortcutDialog = true
                                                }
                                            )

                                            DropdownMenuItem(
                                                text = { Text("Translate...") },
                                                leadingIcon = { Icon(Icons.Default.Translate, contentDescription = null) },
                                                onClick = {
                                                    showBrowserMenu = false
                                                    viewModel.translateEngine.triggerTranslationSelection(activeWebView, activeTab.id, currentWebUrl)
                                                }
                                            )

                                            DropdownMenuItem(
                                                text = { Text("AI Page Assistant") },
                                                leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF818CF8)) },
                                                onClick = {
                                                    showBrowserMenu = false
                                                    showOrionAiAssistantBottomSheet = true
                                                }
                                            )

                                            DropdownMenuItem(
                                                text = { Text("Print") },
                                                leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
                                                onClick = {
                                                    showBrowserMenu = false
                                                    showPrintPreviewDialog = true
                                                }
                                            )

                                            DropdownMenuItem(
                                                text = { Text("Orion VPN") },
                                                leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null, tint = Color(0xFF10B981)) },
                                                onClick = {
                                                    showBrowserMenu = false
                                                    VpnEngineProvider.api.launchVpnUi(context)
                                                }
                                            )

                                            DropdownMenuItem(
                                                text = { Text("Developer Tools") },
                                                leadingIcon = { Icon(Icons.Default.Code, contentDescription = null, tint = Color(0xFF6366F1)) },
                                                onClick = {
                                                    showBrowserMenu = false
                                                    isDevToolsOpen = true
                                                }
                                            )

                                            DropdownMenuItem(
                                                text = { Text("Extensions Management") },
                                                leadingIcon = { Icon(Icons.Default.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                                onClick = {
                                                    showBrowserMenu = false
                                                    extensionApi.openManagerUi()
                                                }
                                            )

                                            extensionUiState.enabledExtensions.forEach { enabledExt ->
                                                DropdownMenuItem(
                                                    text = { Text(enabledExt.name) },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = com.swift.browser.extensionengine.ui.ExtensionIconMapper.getIconForExtension(enabledExt.id, enabledExt.name),
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    },
                                                    onClick = {
                                                        showBrowserMenu = false
                                                        extensionApi.openPopup(enabledExt.id)
                                                    }
                                                )
                                            }

                                            DropdownMenuItem(
                                                text = { Text("Listen to this page") },
                                                leadingIcon = { Icon(Icons.Default.VolumeUp, contentDescription = null) },
                                                onClick = {
                                                    showBrowserMenu = false
                                                    viewModel.startListeningToPageText(context, activeWebView)
                                                }
                                            )

                                            DropdownMenuItem(
                                                text = { Text("Show Reading mode") },
                                                leadingIcon = { Icon(Icons.Default.ChromeReaderMode, contentDescription = null) },
                                                onClick = {
                                                    showBrowserMenu = false
                                                    isReaderModeOpen = true
                                                }
                                            )

                                            DropdownMenuItem(
                                                text = { Text("Desktop site") },
                                                leadingIcon = { Icon(Icons.Default.Devices, contentDescription = null) },
                                                trailingIcon = {
                                                    Checkbox(
                                                        checked = isDesktopMode,
                                                        onCheckedChange = { _ ->
                                                            showBrowserMenu = false
                                                            DesktopEngineProvider.api.toggleForSite(activeTab.id, currentWebUrl, context, activeWebView)
                                                        }
                                                    )
                                                },
                                                onClick = {
                                                    showBrowserMenu = false
                                                    DesktopEngineProvider.api.toggleForSite(activeTab.id, currentWebUrl, context, activeWebView)
                                                }
                                            )

                                            DropdownMenuItem(
                                                text = { Text("Share page") },
                                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                                onClick = {
                                                    showBrowserMenu = false
                                                    val sendIntent = Intent().apply {
                                                        action = Intent.ACTION_SEND
                                                        putExtra(Intent.EXTRA_TEXT, currentWebUrl)
                                                        type = "text/plain"
                                                    }
                                                    context.startActivity(Intent.createChooser(sendIntent, "Share Webpage"))
                                                }
                                            )

                                            DropdownMenuItem(
                                                text = { Text("Delete browsing data") },
                                                leadingIcon = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
                                                onClick = {
                                                    showBrowserMenu = false
                                                    showClearDataDialog = true
                                                }
                                            )

                                            DropdownMenuItem(
                                                text = { Text("Help & feedback") },
                                                leadingIcon = { Icon(Icons.Default.Help, contentDescription = null) },
                                                onClick = {
                                                    showBrowserMenu = false
                                                    showHelpFeedbackDialog = true
                                                }
                                            )

                                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                            DropdownMenuItem(
                                                text = { Text("Settings") },
                                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                                onClick = {
                                                    showBrowserMenu = false
                                                    context.startActivity(Intent(context, com.swift.browser.settingsengine.AdvancedSettingsActivity::class.java))
                                                }
                                            )
                                        }
                                    }
                                }

                                // Web Loader linear progress indicator
                                if (webProgress in 1..99) {
                                    LinearProgressIndicator(
                                        progress = { webProgress / 100f },
                                        modifier = Modifier.fillMaxWidth().height(2.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = Color.Transparent
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(2.dp))
                                }
                            }
                        }



                        // Find in Page Bar
                        if (isFindInPageOpen) {
                            Surface(
                                color = Color(0xFF1E293B),
                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = findInPageQuery,
                                        onValueChange = {
                                            findInPageQuery = it
                                            findInPageEngine.search(it, activeWebView)
                                        },
                                        singleLine = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            color = Color.White,
                                            fontSize = 13.sp
                                        ),
                                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                                    )

                                    Text(
                                        text = "${findInPageState.currentMatch}/${findInPageState.totalMatches}",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp
                                    )

                                    IconButton(
                                        onClick = { findInPageEngine.findNext(false, activeWebView) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match", tint = Color.White)
                                    }

                                    IconButton(
                                        onClick = { findInPageEngine.findNext(true, activeWebView) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match", tint = Color.White)
                                    }

                                    IconButton(
                                        onClick = {
                                            isFindInPageOpen = false
                                            findInPageEngine.toggleFindInPage(false, activeWebView)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Close search", tint = Color.White)
                                    }
                                }
                            }
                        }

                        // Translate Bar
                        if (isTranslateBarOpen || translateUiState.isVisible) {
                            com.swift.browser.translateengine.ui.TranslateEngineUi(
                                engine = viewModel.translateEngine,
                                isDesktopMode = isDesktopMode,
                                activeWebView = activeWebView,
                                tabId = activeTab.id,
                                currentUrl = currentWebUrl
                            )
                        }

                        // Real WebView Canvas
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            key(activeTab.id) {
                                AndroidView(
                                    modifier = Modifier.fillMaxSize().testTag("browser_webview"),
                                    factory = { ctx ->
                                        val existing = viewModel.tabEngine.getWebView(activeTab.id) as? BrowserWebView
                                        if (existing != null) {
                                            (existing.parent as? ViewGroup)?.removeView(existing)
                                            activeWebView = existing
                                            existing
                                        } else {
                                            val newWebView = webViewController.createAndConfigureWebView(
                                                tabId = activeTab.id,
                                                config = BrowserWebViewController.WebViewConfiguration(
                                                    isJavaScriptEnabled = true,
                                                    isHardwareAccelerationEnabled = true,
                                                    isDesktopMode = isDesktopMode,
                                                    isIncognito = activeTab.isIncognito
                                                ),
                                                getTab = { id -> viewModel.tabEngine.getTab(id) },
                                                updateTabModel = { id, updater -> viewModel.tabEngine.updateTab(id, updater) },
                                                triggerTabUpdatedEvent = { tabId, url -> extensionApi.notifyTabUpdated(tabId, url) },
                                                extensionSetup = { wv, tabId -> extensionApi.setupWebView(wv, tabId) },
                                                injectContentScripts = { wv, url, runAt ->
                                                    val isPrivate = (wv as? com.swift.browser.browserengine.BrowserWebView)?.isPrivate ?: false
                                                    val pId = (wv as? com.swift.browser.browserengine.BrowserWebView)?.privateSessionId
                                                    extensionApi.injectContentScripts(wv, url, runAt, isPrivate, pId)
                                                },
                                                flushCookies = { com.swift.browser.cookieengine.CookieEngineApi.getInstance(context).flush() },
                                                permissionEngine = viewModel.permissionEngine,
                                                uiCallbacks = object : BrowserWebViewController.WebViewUiCallbacks {
                                                    override fun onContextMenuRequested(url: String, isImage: Boolean, isImageLink: Boolean) {
                                                        viewModel.showContextMenu(
                                                            url = url,
                                                            isImage = isImage,
                                                            isImageLink = isImageLink,
                                                            imageUrl = if (isImage || isImageLink) url else "",
                                                            tabId = activeTab?.id
                                                        )
                                                    }
                                                    override fun onDownloadRequested(url: String, userAgent: String, contentDisposition: String, mimetype: String, contentLength: Long) {
                                                        Toast.makeText(ctx, "Starting download: ${URLUtil.guessFileName(url, contentDisposition, mimetype)}", Toast.LENGTH_SHORT).show()
                                                        viewModel.downloadActiveFile(url, mimetype, contentDisposition)
                                                    }
                                                    override fun onBlockedAdCountIncremented(tabId: String) {}
                                                    override fun onPageStarted(tabId: String, url: String) {
                                                        viewModel.tabEngine.updateTab(tabId) { it.copy(url = url, isLoading = true) }
                                                        coroutineScope.launch {
                                                            viewModel.translateEngine.onPageStarted(
                                                                context = ctx,
                                                                tabId = tabId,
                                                                webView = activeWebView,
                                                                url = url,
                                                                isDesktop = isDesktopMode,
                                                                currentTranslateTargetCode = viewModel.translateEngine.uiState.value.targetLanguageCode,
                                                                onAutoTranslateRequested = { langCode, langName ->
                                                                    viewModel.translateEngine.executeGoogleTranslation(
                                                                        webView = activeWebView,
                                                                        tabId = tabId,
                                                                        currentUrl = url,
                                                                        langCode = langCode,
                                                                        langName = langName,
                                                                        isDesktop = isDesktopMode
                                                                    )
                                                                }
                                                            )
                                                        }
                                                    }
                                                    override fun onPageFinished(tabId: String, url: String, title: String) {
                                                        val tab = viewModel.tabEngine.getTab(tabId)
                                                        val isIncognitoTab = tab?.isIncognito ?: false
                                                        viewModel.tabEngine.updateTab(tabId) { it.copy(url = url, title = currentWebTitle, isLoading = false) }
                                                        if (!isIncognitoTab && url != "swift://newtab" && url != "swift://newtab-incognito" && !url.startsWith("about:")) {
                                                            viewModel.addHistory(url, currentWebTitle)
                                                        }
                                                    }
                                                    override fun onProgressChanged(tabId: String, progress: Int) {
                                                    }
                                                    override fun onTitleReceived(tabId: String, title: String) {
                                                        viewModel.tabEngine.updateTab(tabId) { it.copy(title = title) }
                                                    }
                                                    override fun onFaviconReceived(tabId: String, favicon: Bitmap) {
                                                        viewModel.tabEngine.updateTab(tabId) { it.copy(favicon = favicon) }
                                                    }
                                                    override fun onErrorPageRequested(view: WebView?, errorType: String, failingUrl: String) {
                                                        ErrorPageEngine.loadErrorPage(view, errorType, failingUrl)
                                                    }
                                                    override fun onShowFileChooser(filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: WebChromeClient.FileChooserParams?) {
                                                        fileUploadCallback?.onReceiveValue(null)
                                                        fileUploadCallback = filePathCallback
                                                        try {
                                                            val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                                                addCategory(Intent.CATEGORY_OPENABLE)
                                                                type = "*/*"
                                                            }
                                                            fileChooserLauncher.launch(intent)
                                                        } catch (e: Exception) {
                                                            fileUploadCallback?.onReceiveValue(null)
                                                            fileUploadCallback = null
                                                        }
                                                    }
                                                    override fun onShowCustomView(view: android.view.View, callback: WebChromeClient.CustomViewCallback) {
                                                        val activity = context as? android.app.Activity
                                                        if (activity != null) {
                                                            com.swift.browser.videoengine.api.VideoEngineApi.showCustomView(activity, view, callback)
                                                        }
                                                    }
                                                    override fun onHideCustomView() {
                                                        val activity = context as? android.app.Activity
                                                        if (activity != null) {
                                                            com.swift.browser.videoengine.api.VideoEngineApi.hideCustomView(activity)
                                                        }
                                                    }
                                                    override fun onNewTabRequested(url: String, isIncognito: Boolean): WebView? {
                                                        val nTab = viewModel.tabEngine.createTab(url, url, isIncognito)
                                                        viewModel.tabEngine.switchTab(nTab.id)
                                                        return viewModel.tabEngine.getWebView(nTab.id)
                                                    }
                                                }
                                            )
                                            viewModel.tabEngine.putWebView(activeTab.id, newWebView)
                                            if (activeTab.url != "swift://newtab" && activeTab.url.isNotEmpty()) {
                                                com.swift.browser.browserengine.BrowserNavigationApi.navigate(
                                                    com.swift.browser.browserengine.NavigationRequest(
                                                        tabId = activeTab.id,
                                                        url = activeTab.url,
                                                        source = com.swift.browser.browserengine.NavigationSource.NEW_TAB,
                                                        applyDesktopPolicy = true,
                                                        webView = newWebView
                                                    )
                                                )
                                            }
                                            activeWebView = newWebView
                                            newWebView
                                        }
                                    },
                                    update = { webView ->
                                        activeWebView = webView
                                        (webView as? com.swift.browser.browserengine.BrowserWebView)?.scrollListener = com.swift.browser.browserengine.BrowserWebViewScrollListener { diffY, isAtTop ->
                                            viewModel.scrollChromeController.onScroll(diffY, isAtTop)
                                        }

                                    }
                                )

                                // Floating Salmon Download FAB (Matching Video 00:20)
                                com.swift.browser.extensionengine.ui.ExtensionChromeStoreFloatingButton(
                                    currentUrl = currentWebUrl,
                                    api = extensionApi,
                                    modifier = Modifier.align(Alignment.TopCenter)
                                )

                                FloatingActionButton(
                                    onClick = { showMediaDetectedDialog = true },
                                    containerColor = Color(0xFFFF7A59),
                                    contentColor = Color.White,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 16.dp, bottom = 16.dp)
                                        .size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FileDownload,
                                        contentDescription = "Fast Download Detected Media",
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            // Biometric Lock Guard for Active Private Tab Canvas
                            if ((activeTab.isIncognito || activeTab.isPrivate) && !isBiometricUnlocked) {
                                com.swift.browser.privatemode.ui.PrivateTabsBiometricLockView(
                                    modifier = Modifier.fillMaxSize(),
                                    engine = viewModel.privateModeEngine,
                                    onAuthenticated = {
                                        // Biometric authentication unlocked private mode
                                    },
                                    onCloseAllPrivateTabs = {
                                        viewModel.tabEngine.groups.value.flatMap { it.tabs }.filter { it.isIncognito || it.isPrivate }.forEach {
                                            viewModel.closeTab(it.id)
                                        }
                                    }
                                )
                            }

                            if (isUrlEditing) {
                                val searchSuggestions by viewModel.searchSuggestionsProvider.suggestions.collectAsState()
                                val isPrivateMode = uiState.isIncognito

                                LaunchedEffect(urlInputText, isPrivateMode) {
                                    viewModel.searchSuggestionsProvider.fetchSuggestions(
                                        query = urlInputText,
                                        engineName = viewModel.preferenceManager?.getString("default_search_engine", "Google") ?: "Google",
                                        historyResults = if (isPrivateMode) emptyList() else historyList.map { com.swift.browser.searchengine.SearchSuggestion(com.swift.browser.searchengine.SuggestionType.HISTORY, it.title, it.url) },
                                        bookmarkResults = bookmarksList.map { com.swift.browser.searchengine.SearchSuggestion(com.swift.browser.searchengine.SuggestionType.BOOKMARK, it.title, it.url) },
                                        coroutineScope = this,
                                        browsingContext = if (isPrivateMode) com.swift.browser.searchengine.BrowsingContext.PRIVATE else com.swift.browser.searchengine.BrowsingContext.NORMAL
                                    )
                                }

                                com.swift.browser.searchengine.ui.SearchFocusedOverlay(
                                    activeTab = com.swift.browser.searchengine.ui.SearchTabPreview(
                                        title = currentWebTitle,
                                        url = currentWebUrl
                                    ),
                                    searchSuggestions = searchSuggestions,
                                    currentInputUrl = urlInputText,
                                    addressBarPosition = "top",
                                    history = if (isPrivateMode) emptyList() else historyList.map { com.swift.browser.searchengine.ui.SearchHistoryPreview(it.title, it.url) },
                                    onSearch = { query ->
                                        isUrlEditing = false
                                        focusManager.clearFocus()
                                        viewModel.openUrl(query, false)
                                    },
                                    onEdit = { text ->
                                        urlInputText = text
                                    },
                                    isGlass = false,
                                    bottomBar = {
                                        QuickAccessToolsBar(
                                            onToolSelect = { tool ->
                                                if (tool == "video" || tool == "music") {
                                                    pendingToolLaunch = tool
                                                } else {
                                                    activeQuickTool = tool
                                                }
                                                isUrlEditing = false
                                            },
                                            isIncognito = activeTab.isIncognito,
                                            modifier = Modifier.fillMaxWidth().imePadding()
                                        )
                                    }
                                )
                            }
                        }

                        // Bottom Tab Strip layout (Legacy Tab Strip with Group Awareness & Hide on Scroll)
                        val allTabs = groupsList.flatMap { it.tabs }

                        val shouldShowBottom =
                            !isTabSwitcherOpen &&
                            !isReaderModeOpen &&
                            !isNewTab

                        if (shouldShowBottom) {
                            val hideProgress by viewModel.scrollChromeController.hideProgress.collectAsState()
                            Box(
                                modifier = Modifier
                                    .layout { measurable, constraints ->
                                        val placeable = measurable.measure(constraints)
                                        val offset = (placeable.height * hideProgress).toInt()
                                        layout(placeable.width, (placeable.height - offset).coerceAtLeast(0)) {
                                            placeable.placeRelative(0, offset)
                                        }
                                    }
                                    .graphicsLayer {
                                        alpha = 1f - (hideProgress * 0.5f)
                                        translationY = size.height * hideProgress
                                    }
                            ) {
                                com.swift.browser.tabengine.ui.BottomTabStripLayout(
                                    tabs = allTabs,
                                    activeTabId = activeTab.id,
                                    onTabSelect = { tabId ->
                                        viewModel.tabEngine.switchTab(tabId)
                                    },
                                    onTabClose = { tabId ->
                                        viewModel.tabEngine.closeTab(tabId)
                                    },
                                    onNewTab = {
                                        val nTab = viewModel.tabEngine.createTab("swift://newtab", "New Tab", false, activeTab.groupId)
                                        viewModel.tabEngine.switchTab(nTab.id)
                                    },
                                    onOpenTabSwitcher = {
                                        isTabSwitcherOpen = true
                                    },
                                    onVoiceClick = {
                                        try {
                                            val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
                                                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak to search...")
                                            }
                                            speechRecognizerLauncher.launch(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Voice recognition not available", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    isGlass = false,
                                    applyNavigationPadding = !isNewTab
                                )
                            }
                        }
                    }
                }
            }
        }

        // High-Performance Animated Tab Switcher Overlay
        androidx.compose.animation.AnimatedVisibility(
            visible = isTabSwitcherOpen,
            enter = com.swift.browser.tabengine.animation.TabAnimationTransitions.tabSwitcherEnter,
            exit = com.swift.browser.tabengine.animation.TabAnimationTransitions.tabSwitcherExit
        ) {
            com.swift.browser.tabengine.ui.TabSwitcherLayout(
                groups = groupsList,
                activeGroupId = activeTabGroup?.id,
                activeTabId = activeTab.id,
                onGroupSelected = { groupId -> viewModel.tabEngine.switchGroup(groupId) },
                onNewGroup = { viewModel.createNewTabGroup("New Group", false) },
                onTabSelected = { tabId ->
                    viewModel.tabEngine.switchTab(tabId)
                    isTabSwitcherOpen = false
                },
                onTabClosed = { tabId -> viewModel.closeTab(tabId) },
                onNewTab = { isIncognito ->
                    viewModel.tabEngine.createTab("swift://newtab", if (isIncognito) "Incognito Tab" else "New Tab", isIncognito = isIncognito)
                    isTabSwitcherOpen = false
                },
                onCloseSwitcher = { isTabSwitcherOpen = false },
                isPrivateUnlocked = isBiometricUnlocked,
                onAuthenticateBiometric = {
                    val activity = (context as? androidx.fragment.app.FragmentActivity)
                        ?: (context as? android.content.ContextWrapper)?.let {
                            var ctx: android.content.Context? = it
                            while (ctx is android.content.ContextWrapper) {
                                if (ctx is androidx.fragment.app.FragmentActivity) break
                                ctx = ctx.baseContext
                            }
                            ctx as? androidx.fragment.app.FragmentActivity
                        }
                    if (activity != null) {
                        viewModel.authenticatePrivateTabs(activity)
                    } else {
                        viewModel.unlockPrivateTabs()
                    }
                },
                onCloseAllPrivateTabs = {
                    viewModel.tabEngine.groups.value.flatMap { it.tabs }.filter { it.isIncognito || it.isPrivate }.forEach {
                        viewModel.closeTab(it.id)
                    }
                }
            )
        }

        // Context Menu for Long-Pressed Links & Images
        WebContextMenuBottomSheet(
            state = contextMenuState,
            onDismiss = { viewModel.dismissContextMenu() },
            onOpenInNewTab = { url ->
                viewModel.addNewTab(url)
            },
            onOpenInNewTabGroup = { url ->
                viewModel.openInNewTabInGroup(url, contextMenuState.tabId ?: activeTab?.id)
            },
            onOpenInIncognito = { url ->
                val newTab = viewModel.tabEngine.createTab(url, url, isIncognito = true)
                viewModel.tabEngine.switchTab(newTab.id)
            },
            onDownloadLink = { url ->
                Toast.makeText(context, "Starting download...", Toast.LENGTH_SHORT).show()
                viewModel.downloadActiveFile(url)
            },
            onAddToReadingList = { url, title ->
                // ReaderEngine / Reading list hook
            }
        )

        // Global About App Dialog
        AboutAppDialog(
            show = isAboutDialogOpen,
            onDismiss = { isAboutDialogOpen = false }
        )

        // Add to Home Screen Shortcut Dialog
        AddShortcutDialog(
            show = showAddShortcutDialog,
            url = currentWebUrl,
            title = currentWebTitle,
            onDismiss = { showAddShortcutDialog = false },
            onConfirm = { name, url ->
                ShortcutUtils.pinWebpageShortcut(context, url, name, currentFavicon)
                Toast.makeText(context, "Shortcut added to home screen", Toast.LENGTH_SHORT).show()
            }
        )

        // Clear Browsing Data Dialog
        ClearBrowsingDataDialog(
            show = showClearDataDialog,
            onDismiss = { showClearDataDialog = false },
            onConfirm = { clearHistory, clearCookies, clearCache, clearDownloads ->
                viewModel.clearBrowsingData(clearHistory, clearCookies, clearCache, clearDownloads, context)
                Toast.makeText(context, "Browsing data cleared", Toast.LENGTH_SHORT).show()
            }
        )

        // Recent Tabs Dialog
        RecentTabsDialog(
            show = showRecentTabsDialog,
            recentTabs = groupsList.flatMap { it.tabs },
            onDismiss = { showRecentTabsDialog = false },
            onTabSelected = { tab ->
                viewModel.tabEngine.switchTab(tab.id)
            }
        )

        // Help & Feedback Dialog
        HelpFeedbackDialog(
            show = showHelpFeedbackDialog,
            onDismiss = { showHelpFeedbackDialog = false }
        )

        // Extension Engine UI Overlay Host
        com.swift.browser.extensionengine.ui.ExtensionEngineUi(api = extensionApi)

        // Extensions Dialog
        ExtensionsManagementDialog(
            show = showExtensionsDialog,
            onDismiss = { showExtensionsDialog = false }
        )


        // Web Notifications Host (Delegated to :notification-engine)
        if (showWebNotificationsDialog) {
            com.swift.browser.notificationengine.api.NotificationEngineProvider.api.NotificationCenterUi(
                onBack = { showWebNotificationsDialog = false },
                onOpenUrl = { url ->
                    viewModel.addNewTab(url)
                    showWebNotificationsDialog = false
                },
                modifier = Modifier
            )
        }

        // Canonical Site Info Bottom Sheet
        SiteInfoBottomSheet(
            show = showSslDialog,
            url = currentWebUrl,
            title = currentWebTitle,
            onDismiss = { showSslDialog = false },
            onOpenSiteSettings = {
                showSslDialog = false
            }
        )

        // Media Resource Detected Bottom Sheet (Matching video 00:20-00:30)
        MediaResourceDetectedDialog(
            show = showMediaDetectedDialog,
            url = currentWebUrl,
            onDismiss = { showMediaDetectedDialog = false },
            onDownload = { fileName, threads ->
                viewModel.downloadActiveFile(currentWebUrl, "video/mp4", "")
            }
        )

        // Orion AI Assistant Bottom Sheet (Matching video 00:40)
        OrionAiAssistantBottomSheet(
            show = showOrionAiAssistantBottomSheet,
            pageTitle = currentWebTitle,
            pageUrl = currentWebUrl,
            onDismiss = { showOrionAiAssistantBottomSheet = false }
        )

        // Print Preview Dialog (Matching video 00:50)
        PrintPreviewDialog(
            show = showPrintPreviewDialog,
            title = currentWebTitle,
            url = currentWebUrl,
            onDismiss = { showPrintPreviewDialog = false },
            onPrint = {
                PrintEngine.printCurrentPage(context, activeWebView)
            }
        )

        // Canonical Single Authority Website Permission Dialog
        PermissionPromptDialog()
    }
}
