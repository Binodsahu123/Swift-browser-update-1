package com.swift.browser.browserengine.ui

import com.swift.browser.browserengine.BrowserViewModel

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import com.swift.browser.aiengine.AIWebsiteBridgeSystem
import com.swift.browser.aiengine.AIAnalysisResult
import com.swift.browser.aiengine.AISessionManager
import com.swift.browser.aiengine.AISummaryCache
import com.swift.browser.aiengine.AISummaryEngine
import com.swift.browser.aiengine.AISettingsManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatPanel(
    tabId: String,
    url: String,
    pageText: String,
    onDismiss: () -> Unit,
    viewModel: BrowserViewModel? = null
) {
    val context = LocalContext.current
    val settingsManager = remember { AISettingsManager(context) }
    val scope = rememberCoroutineScope()

    val bridgeSystem = remember { AIWebsiteBridgeSystem.getInstance(context) }
    val currentProvider = settingsManager.defaultProvider
    val isLoginNeeded by bridgeSystem.getLoginNeededFlow(currentProvider).collectAsState()
    val isBridgeProvider = true

    var showProviderWebLoginDialog by remember { mutableStateOf(false) }
    var summaryTriggerKey by remember { mutableStateOf(0) }

    LaunchedEffect(currentProvider) {
        bridgeSystem.getOrCreateWebView(currentProvider)
    }

    // Query active cached session for state persistence within the same tab/website
    val cachedSession = remember(tabId, url) { AISessionManager.getSession(tabId) }

    var activeTab by remember { mutableStateOf("summary") } // "summary" or "chat"
    
    // Summary loading flag and loaded results
    var summaryLoading by remember { mutableStateOf(cachedSession?.parsedSummary == null && pageText.isNotBlank()) }
    var analysisResult by remember { mutableStateOf<AIAnalysisResult?>(cachedSession?.parsedSummary) }
    
    // Initialize stateful chat session histories
    val chatHistory = remember { 
        val list = mutableStateListOf<Pair<String, String>>()
        cachedSession?.chatHistory?.let { list.addAll(it) }
        list
    }
    
    var chatInputText by remember { mutableStateOf("") }
    var chalLoading by remember { mutableStateOf(false) }
    val chatListState = rememberLazyListState()

    // Interactive display modes: "sheet" (bottom sheet height fraction), "full" (fullscreen), "side" (side-panel width), "quick" (micro outline)
    var viewMode by remember { mutableStateOf("sheet") }
    var showMemoryManagerDialog by remember { mutableStateOf(false) }

    // Dynamic browser context builder
    val browserContext = remember(tabId, url, viewModel?.uiState?.value, viewModel?.downloads?.value, viewModel?.bookmarks?.value, viewModel?.history?.value) {
        val uiStateVal = viewModel?.uiState?.value
        val openTabsStr = viewModel?.tabEngine?.groups?.value?.flatMap { it.tabs }?.mapIndexed { idx, tab ->
            "Tab #${idx + 1}: Title: \"${tab.title}\", URL: \"${tab.url}\", ID: \"${tab.id}\", InGroup: ${tab.groupName != null} (${tab.groupName ?: ""})"
        }?.joinToString("\n") ?: "None"

        val downloadsStr = viewModel?.downloads?.value?.take(10)?.map {
            "- ID: ${it.id}, File: \"${it.title}\", Status: ${it.status}, URL: ${it.url}"
        }?.joinToString("\n") ?: "None"

        val bookmarksStr = viewModel?.bookmarks?.value?.take(15)?.map {
            "- ${it.title} (${it.url})"
        }?.joinToString("\n") ?: "None"

        val historyStr = viewModel?.history?.value?.take(15)?.map {
            "- ${it.title} (${it.url})"
        }?.joinToString("\n") ?: "None"

        val adBlockStatus = uiStateVal?.globalAdBlockEnabled == true
        val jsStatus = uiStateVal?.isJavaScriptEnabled == true

        """
        [SYSTEM BROWSER CONTEXT]
        Active Tab ID: "$tabId"
        Active Tab URL: "$url"
        Active Tab Title: "${viewModel?.tabEngine?.groups?.value?.flatMap { it.tabs }?.find { it.id == tabId }?.title ?: ""}"
        
        Open Tabs List:
        $openTabsStr
        
        Recent Downloads:
        $downloadsStr
        
        Bookmarks:
        $bookmarksStr
        
        Recent History:
        $historyStr
        
        Browser Settings:
        - Ad Block: ${if (adBlockStatus) "ENABLED" else "DISABLED"}
        - JavaScript: ${if (jsStatus) "ENABLED" else "DISABLED"}
        
        CRITICAL OPERATIONAL RULES:
        1. If the user request relates to a browser action (e.g. open a tab, close a tab, check download state, show history, delete a download, pause download, resume download, open panels, toggle ad block, translate page, set bookmark), append exactly one or more command blocks at the very end of your response:
        [COMMAND: open_tab url="TARGET_URL"]
        [COMMAND: close_tab tab_id="TAB_ID"]
        [COMMAND: create_tab_group name="NAME" color="4284613200"]
        [COMMAND: open_downloads_panel]
        [COMMAND: open_history_panel]
        [COMMAND: open_bookmarks_panel]
        [COMMAND: toggle_ad_block]
        [COMMAND: translate_page lang="LANGUAGE_CODE"]
        [COMMAND: delete_download id="DOWNLOAD_ID"]
        [COMMAND: add_bookmark url="URL" title="TITLE"]
        [COMMAND: read_aloud]

        Confirm your execution in a warm, conversational voice (e.g. "Certainly! Opening google.com in a new tab for you."). Keep commands strictly formatted.
        """.trimIndent()
    }

    // Trigger page summary once on open (re-triggered upon completion of auth/login)
    LaunchedEffect(pageText, tabId, url, summaryTriggerKey) {
        // 1. Check if cachedSession or AISummaryCache already has the completed result
        val sessionItem = AISessionManager.getSession(tabId)
        if (sessionItem != null && sessionItem.websiteUrl == url && sessionItem.parsedSummary != null && summaryTriggerKey == 0) {
            analysisResult = sessionItem.parsedSummary
            summaryLoading = false
            return@LaunchedEffect
        }
        
        val cachedItemFirst = AISummaryCache.get(url)
        if (cachedItemFirst?.analysisResult != null) {
            analysisResult = cachedItemFirst.analysisResult
            summaryLoading = false
            return@LaunchedEffect
        }

        if (url.isBlank() || url.startsWith("swift://") || url.startsWith("about:") || url.startsWith("file://")) {
            summaryLoading = false
            analysisResult = AIAnalysisResult(
                mainTopic = "System Page",
                summary = "The AI Assistant is available on standard web pages. Navigate to a website to see summaries."
            )
            return@LaunchedEffect
        }

        // 2. If background analysis is active, poll AISummaryCache or AISessionManager for up to 5 seconds
        summaryLoading = true
        var pollAttempts = 0
        while (pollAttempts < 10) { // 10 * 500ms = 5 seconds
            val polledItem = AISummaryCache.get(url)
            val polledSession = AISessionManager.getSession(tabId)
            
            if (polledSession != null && polledSession.websiteUrl == url && polledSession.parsedSummary != null) {
                analysisResult = polledSession.parsedSummary
                summaryLoading = false
                return@LaunchedEffect
            }
            if (polledItem?.analysisResult != null) {
                analysisResult = polledItem.analysisResult
                summaryLoading = false
                return@LaunchedEffect
            }
            
            // Wait 500ms before next poll
            kotlinx.coroutines.delay(500)
            pollAttempts++
        }

        // 3. Fallback: Display a pending state without triggering any fresh extraction or scanning
        summaryLoading = false
        analysisResult = AIAnalysisResult(
            mainTopic = cachedItemFirst?.title ?: "Analysis Pending",
            summary = "The page background analysis is taking a moment to complete. Please try again shortly.",
            keyPoints = listOf("Background pre-loading active", "Analysis in progress", "Please try again shortly")
        )
    }

    fun postChatMessage(message: String) {
        if (message.isBlank()) return
        chatHistory.add("user" to message)
        AISessionManager.updateChatHistory(tabId, chatHistory.toList())
        chatInputText = ""
        chalLoading = true
        
        scope.launch {
            if (chatHistory.isNotEmpty()) {
                chatListState.animateScrollToItem(chatHistory.size - 1)
            }

            // A. Intercept for client-side quick local actions to render instant response offline
            val lowerMsg = message.trim().lowercase()
            var handledQuickly = false
            when {
                lowerMsg.startsWith("open tab ") || lowerMsg.startsWith("go to ") -> {
                    val target = message.substringAfter("tab ").substringAfter("to ").trim()
                    val formatted = if (target.startsWith("http")) target else "https://$target"
                    viewModel?.addNewTab(formatted)
                    chatHistory.add("assistant" to "Opening $formatted in a new tab for you!")
                    handledQuickly = true
                }
                lowerMsg == "close current tab" || lowerMsg == "close tab" -> {
                    viewModel?.closeTab(tabId)
                    chatHistory.add("assistant" to "Closing the current tab.")
                    handledQuickly = true
                }
                lowerMsg == "show downloads" || lowerMsg == "open downloads" || lowerMsg == "downloads" -> {
                    viewModel?.setDownloadsOpen(true)
                    chatHistory.add("assistant" to "Opening your downloads history.")
                    handledQuickly = true
                }
                lowerMsg == "show history" || lowerMsg == "open history" -> {
                    viewModel?.setHistoryOpen(true)
                    chatHistory.add("assistant" to "Opening your browsing history.")
                    handledQuickly = true
                }
                lowerMsg == "show bookmarks" || lowerMsg == "open bookmarks" -> {
                    viewModel?.setBookmarksOpen(true)
                    chatHistory.add("assistant" to "Opening your browser bookmarks.")
                    handledQuickly = true
                }
            }

            if (handledQuickly) {
                chalLoading = false
                AISessionManager.updateChatHistory(tabId, chatHistory.toList())
                if (chatHistory.isNotEmpty()) {
                    chatListState.animateScrollToItem(chatHistory.size - 1)
                }
                return@launch
            }

            // B. Clean chat history of messy system context blobs to avoid token bloat
            val cleanChatHistory = chatHistory.map { (role, text) ->
                val cleanText = if (role == "user") {
                    text.substringBefore("\n\n---")
                } else {
                    text
                }
                role to cleanText
            }

            val promptWithContext = """
                $message
                
                ---
                $browserContext
            """.trimIndent()
            
            val response = AISummaryEngine.chatSession(cleanChatHistory, promptWithContext, settingsManager, context)
            
            // C. Intercept and parse AI commands
            val commandRegex = Regex("""\[COMMAND:\s*(\w+)([^\]]*)]""")
            val commandMatches = commandRegex.findAll(response).toList()
            val cleanResponse = response.replace(commandRegex, "").trim()
            
            chatHistory.add("assistant" to cleanResponse)
            AISessionManager.updateChatHistory(tabId, chatHistory.toList())
            chalLoading = false
            
            if (chatHistory.isNotEmpty()) {
                chatListState.animateScrollToItem(chatHistory.size - 1)
            }

            // D. Execute commanded actions on Browser Core dynamically!
            commandMatches.forEach { match ->
                val cmdType = match.groups[1]?.value?.trim()?.lowercase() ?: ""
                val cmdArgsRaw = match.groups[2]?.value ?: ""
                
                fun getArg(name: String): String {
                    val r = Regex("""$name=["']([^"']*)["']""")
                    return r.find(cmdArgsRaw)?.groups?.get(1)?.value ?: ""
                }
                
                try {
                    when (cmdType) {
                        "open_tab" -> {
                            val targetUrl = getArg("url")
                            if (targetUrl.isNotEmpty()) {
                                val formatted = if (targetUrl.startsWith("http")) targetUrl else "https://$targetUrl"
                                viewModel?.addNewTab(formatted)
                            }
                        }
                        "close_tab" -> {
                            val targetTabId = getArg("tab_id")
                            if (targetTabId.isNotEmpty()) {
                                viewModel?.closeTab(targetTabId)
                            } else {
                                viewModel?.closeTab(tabId)
                            }
                        }
                        "create_tab_group" -> {
                            val name = getArg("name")
                            val colorVal = getArg("color").toLongOrNull() ?: 0xFF35A2FF
                            if (name.isNotEmpty()) {
                                viewModel?.createNewTabGroup(name, false)
                            }
                        }
                        "open_downloads_panel" -> {
                            viewModel?.setDownloadsOpen(true)
                        }
                        "open_history_panel" -> {
                            viewModel?.setHistoryOpen(true)
                        }
                        "open_bookmarks_panel" -> {
                            viewModel?.setBookmarksOpen(true)
                        }
                        "toggle_ad_block" -> {
                            val current = viewModel?.uiState?.value?.globalAdBlockEnabled == true
                            viewModel?.setGlobalAdBlockEnabled(!current)
                        }
                        "translate_page" -> {
                            val lang = getArg("lang")
                            if (lang.isNotEmpty()) {
                                viewModel?.translateActivePage(lang)
                            }
                        }
                        "delete_download" -> {
                            val rawId = getArg("id").toLongOrNull()
                            if (rawId != null) {
                                viewModel?.deleteDownloads(setOf(rawId))
                            }
                        }
                        "add_bookmark" -> {
                            val u = getArg("url")
                            val t = getArg("title")
                            if (u.isNotEmpty()) {
                                viewModel?.addBookmarkExternally(u, t.ifEmpty { "Bookmarked" })
                            }
                        }
                        "read_aloud" -> {
                            viewModel?.startListeningToPageText()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Memory management dialog popup rule compliance
    if (showMemoryManagerDialog) {
        Dialog(onDismissRequest = { showMemoryManagerDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Swift Assistant Memory Manager", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    
                    Text(
                        text = "Swift AI retains secure, client-side offline memory to improve your user experience, without uploading sensitive personal details or metadata to any server.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    
                    HorizontalDivider()
                    
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Active Session Memory", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Currently holds ${chatHistory.size} conversation messages in this tab's memory footprint.", fontSize = 11.sp, color = Color.Gray)
                        Button(
                            onClick = {
                                chatHistory.clear()
                                AISessionManager.updateChatHistory(tabId, emptyList())
                                Toast.makeText(context, "Session chat history flushed.", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Clear Session Chat History", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Long-Term Intelligence Profile", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Assistant preference: language (English/Hindi auto), settings toggles, favorite shortcuts.", fontSize = 11.sp, color = Color.Gray)
                        Button(
                            onClick = {
                                Toast.makeText(context, "Long-term AI memory profile reset.", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.outline),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Reset Long-Term Assistant Profile", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Button(
                        onClick = { showMemoryManagerDialog = false },
                        modifier = Modifier.align(Alignment.End),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = if (viewMode == "side") Alignment.CenterEnd else Alignment.BottomCenter
    ) {
        Surface(
            modifier = when (viewMode) {
                "full" -> Modifier.fillMaxSize()
                "side" -> Modifier.fillMaxHeight().width(360.dp)
                "quick" -> Modifier.fillMaxWidth().fillMaxHeight(0.42f)
                else -> Modifier.fillMaxWidth().fillMaxHeight(0.78f)
            },
            color = MaterialTheme.colorScheme.surface,
            shape = if (viewMode == "full" || viewMode == "side") RoundedCornerShape(0.dp) else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            shadowElevation = 16.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
            if (isBridgeProvider) {
                Surface(
                    color = if (isLoginNeeded) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isLoginNeeded) Icons.Default.VpnKey else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isLoginNeeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isLoginNeeded) "$currentProvider Login Required" else "$currentProvider Session Connected",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isLoginNeeded) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        
                        if (isLoginNeeded) {
                            Button(
                                onClick = { 
                                    showProviderWebLoginDialog = true
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Log In", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            TextButton(
                                onClick = {
                                    showProviderWebLoginDialog = true
                                },
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Manage Session", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (showProviderWebLoginDialog) {
                Dialog(
                    onDismissRequest = { 
                        showProviderWebLoginDialog = false
                        bridgeSystem.checkLoginStateAndUrl(currentProvider, bridgeSystem.getOrCreateWebView(currentProvider).url)
                        summaryTriggerKey++
                    }
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.85f),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("$currentProvider Web Session", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                IconButton(onClick = { 
                                    showProviderWebLoginDialog = false 
                                    bridgeSystem.checkLoginStateAndUrl(currentProvider, bridgeSystem.getOrCreateWebView(currentProvider).url)
                                    summaryTriggerKey++
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                            }
                            
                            Box(modifier = Modifier.weight(1f)) {
                                AndroidView(
                                    factory = { 
                                        bridgeSystem.getOrCreateWebView(currentProvider)
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Button(
                                onClick = { 
                                    showProviderWebLoginDialog = false 
                                    bridgeSystem.checkLoginStateAndUrl(currentProvider, bridgeSystem.getOrCreateWebView(currentProvider).url)
                                    summaryTriggerKey++
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Finish & Return", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            // Sliding handle & Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp, 4.dp)
                        .background(Color.Gray.copy(alpha = 0.5f), CircleShape)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Star",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Swift AI Assistant",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Memory Profile Manager
                    IconButton(onClick = { showMemoryManagerDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = "Memory controls",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Display Mode selectors
                    IconButton(onClick = { viewMode = "sheet" }) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Bottom Sheet Mode",
                            tint = if (viewMode == "sheet") MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = { viewMode = "full" }) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Full Screen Mode",
                            tint = if (viewMode == "full") MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = { viewMode = "side" }) {
                        Icon(
                            imageVector = Icons.Default.VerticalSplit,
                            contentDescription = "Side Panel Mode",
                            tint = if (viewMode == "side") MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(onClick = { viewMode = "quick" }) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Quick Ask Mode",
                            tint = if (viewMode == "quick") MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close AI panel", modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Tab Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Summary" to Icons.Default.Analytics,
                    "Key Points" to Icons.Default.Star,
                    "Ask Question" to Icons.Default.Chat,
                    "Fact Check" to Icons.Default.CheckCircle,
                    "Explain" to Icons.Default.Lightbulb
                ).forEach { (name, icon) ->
                    val selected = when (name) {
                        "Summary" -> activeTab == "summary"
                        "Key Points" -> activeTab == "key_points"
                        "Ask Question" -> activeTab == "chat"
                        "Fact Check" -> activeTab == "fact_check"
                        "Explain" -> activeTab == "explain"
                        else -> false
                    }
                    FilterChip(
                        selected = selected,
                        onClick = {
                            activeTab = when (name) {
                                "Summary" -> "summary"
                                "Key Points" -> "key_points"
                                "Ask Question" -> "chat"
                                "Fact Check" -> "fact_check"
                                "Explain" -> "explain"
                                else -> "summary"
                            }
                        },
                        label = { Text(name, fontSize = 12.sp) },
                        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))

            // Tab Contents
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (activeTab != "chat") {
                    if (summaryLoading) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Extracting webpage context & initiating synthesis...",
                                fontSize = 13.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else if (analysisResult != null) {
                        val result = analysisResult!!
                        if (activeTab == "summary") {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                            // Main Topic Card
                            if (result.mainTopic.isNotEmpty()) {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Default.Topic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                Text("PRIMARY SUBJECT", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(result.mainTopic, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            // Reading Time Chip Card
                            if (result.readingTime > 0) {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.wrapContentSize()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AccessTime,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "Estimated Reading Time: ${result.readingTime} min",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            // Short Summary Card
                            if (result.shortSummary.isNotEmpty()) {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.FlashOn,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.secondary
                                                )
                                                Text(
                                                    text = "QUICK TAKE-AWAY",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = result.shortSummary,
                                                fontSize = 13.sp,
                                                lineHeight = 18.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }

                            // Summary Card (Detailed Summary)
                            if (result.summary.isNotEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Default.Description, contentDescription = null)
                                                Text("DETAILED SUMMARY", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(result.summary, fontSize = 13.sp, lineHeight = 19.sp)
                                        }
                                    }
                                }
                            }

                            // Key Takeaways KeyPoints Card
                            if (result.keyPoints.isNotEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFBBF24))
                                                Text("CRITICAL TAKEAWAYS", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            result.keyPoints.forEach { point ->
                                                Row(
                                                    modifier = Modifier.padding(vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text("•", fontWeight = FontWeight.Bold)
                                                    Text(point, fontSize = 13.sp, lineHeight = 18.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Highlights and Takeaways Card
                            if (result.highlights.isNotEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Lightbulb,
                                                    contentDescription = null,
                                                    tint = Color(0xFFEAB308)
                                                )
                                                Text(
                                                    text = "KEY HIGHLIGHTS & INSIGHTS",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            result.highlights.forEach { h ->
                                                Row(
                                                    modifier = Modifier.padding(vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text("✦", color = Color(0xFFEAB308), fontWeight = FontWeight.Bold)
                                                    Text(text = h, fontSize = 13.sp, lineHeight = 18.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Pros and Cons Cards
                            if (result.pros.isNotEmpty() || result.cons.isNotEmpty()) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        if (result.pros.isNotEmpty()) {
                                            Card(
                                                modifier = Modifier.weight(1f),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFFFEE))
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Text("PROS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF22C55E))
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    result.pros.forEach { item ->
                                                        Text("• $item", fontSize = 12.sp, lineHeight = 16.sp, color = Color.DarkGray)
                                                    }
                                                }
                                            }
                                        }

                                        if (result.cons.isNotEmpty()) {
                                            Card(
                                                modifier = Modifier.weight(1f),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F1))
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Text("CONS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFEF4444))
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    result.cons.forEach { item ->
                                                        Text("• $item", fontSize = 12.sp, lineHeight = 16.sp, color = Color.DarkGray)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Important Stats Card
                            if (result.factsAndStats.isNotEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Default.Insights, contentDescription = null)
                                                Text("KEY STATISTICS & FACTS", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            result.factsAndStats.forEach { stat ->
                                                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                                                    Text("📊 ", fontSize = 12.sp)
                                                    Text(stat, fontSize = 13.sp, lineHeight = 18.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Timeline Dates Card
                            if (result.dates.isNotEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Default.DateRange, contentDescription = null)
                                                Text("CRITICAL TIMELINE DATES", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            result.dates.forEach { date ->
                                                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                                                    Text("📅 ", fontSize = 12.sp)
                                                    Text(date, fontSize = 13.sp, lineHeight = 17.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Important People & Entities
                            if (result.peopleAndEntities.isNotEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Default.People, contentDescription = null)
                                                Text("PEOPLE & ENTITIES", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            result.peopleAndEntities.forEach { item ->
                                                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                                                    Text("🏢 ", fontSize = 12.sp)
                                                    Text(item, fontSize = 13.sp, lineHeight = 17.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Important Page Links Card
                            if (result.links.isNotEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Link,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "IMPORTANT PAGE LINKS",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            result.links.forEach { (labelText, urlString) ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp)
                                                        .clickable {
                                                            if (urlString.startsWith("http", ignoreCase = true)) {
                                                                try {
                                                                    val intent = android.content.Intent(
                                                                        android.content.Intent.ACTION_VIEW,
                                                                        android.net.Uri.parse(urlString)
                                                                    )
                                                                    context.startActivity(intent)
                                                                } catch (e: Exception) {
                                                                    android.widget.Toast.makeText(context, "Navigating to: $urlString", android.widget.Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        },
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowOutward,
                                                        contentDescription = null,
                                                        tint = if (urlString.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.Gray,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Column {
                                                        Text(
                                                            text = labelText,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = if (urlString.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                        )
                                                        if (urlString.isNotEmpty()) {
                                                            Text(
                                                                text = urlString,
                                                                fontSize = 11.sp,
                                                                color = Color.Gray,
                                                                maxLines = 1
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (activeTab == "key_points") {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Reading Time Chip Card
                            if (result.readingTime > 0) {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.wrapContentSize()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AccessTime,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "Estimated Reading Time: ${result.readingTime} min",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            // Key Takeaways KeyPoints Card
                            if (result.keyPoints.isNotEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFBBF24))
                                                Text("CRITICAL TAKEAWAYS", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            result.keyPoints.forEach { point ->
                                                Row(
                                                    modifier = Modifier.padding(vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text("•", fontWeight = FontWeight.Bold)
                                                    Text(point, fontSize = 13.sp, lineHeight = 18.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Highlights and Takeaways Card
                            if (result.highlights.isNotEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Lightbulb,
                                                    contentDescription = null,
                                                    tint = Color(0xFFEAB308)
                                                )
                                                Text(
                                                    text = "KEY HIGHLIGHTS & INSIGHTS",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            result.highlights.forEach { h ->
                                                Row(
                                                    modifier = Modifier.padding(vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text("✦", color = Color(0xFFEAB308), fontWeight = FontWeight.Bold)
                                                    Text(text = h, fontSize = 13.sp, lineHeight = 18.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Pros and Cons Cards
                            if (result.pros.isNotEmpty() || result.cons.isNotEmpty()) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        if (result.pros.isNotEmpty()) {
                                            Card(
                                                modifier = Modifier.weight(1f),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFFFEE))
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Text("PROS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF22C55E))
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    result.pros.forEach { item ->
                                                        Text("• $item", fontSize = 12.sp, lineHeight = 16.sp, color = Color.DarkGray)
                                                    }
                                                }
                                            }
                                        }

                                        if (result.cons.isNotEmpty()) {
                                            Card(
                                                modifier = Modifier.weight(1f),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F1))
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Text("CONS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFEF4444))
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    result.cons.forEach { item ->
                                                        Text("• $item", fontSize = 12.sp, lineHeight = 16.sp, color = Color.DarkGray)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (activeTab == "fact_check") {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Text("FACT VERIFIER", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Verify core numbers, dates, claims and statistical statements present on this webpage instantly.", fontSize = 13.sp, color = Color.Gray)
                                }
                            }

                            if (result.factsAndStats.isNotEmpty() || result.dates.isNotEmpty()) {
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (result.factsAndStats.isNotEmpty()) {
                                        item {
                                            Text("Verified Statistics & Facts", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
                                        }
                                        items(result.factsAndStats) { stat ->
                                            Card(modifier = Modifier.fillMaxWidth()) {
                                                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text("📊")
                                                    Text(stat, fontSize = 13.sp, lineHeight = 18.sp)
                                                }
                                            }
                                        }
                                    }

                                    if (result.dates.isNotEmpty()) {
                                        item {
                                            Text("Time Anchor Dates", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
                                        }
                                        items(result.dates) { date ->
                                            Card(modifier = Modifier.fillMaxWidth()) {
                                                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text("📅")
                                                    Text(date, fontSize = 13.sp, lineHeight = 18.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No baseline statistics extracted automatically. Use the deep fact check button below to analyze claims.", fontSize = 13.sp, color = Color.Gray, textAlign = TextAlign.Center)
                                }
                            }

                            Button(
                                onClick = {
                                    activeTab = "chat"
                                    postChatMessage("Please execute a detailed fact-check of the main statements, figures, and claims made on this webpage and list anything that requires validation or source citation.")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Insights, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Perform AI Deep Fact-Check", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (activeTab == "explain") {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Text("EXPLAIN SIMPLIFIER", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Distill the core concept, technology, news, or subject matter of this webpage into extremely straightforward, layman-friendly concepts.", fontSize = 13.sp)
                                }
                            }

                            Card(
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            ) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize().padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    item {
                                        Text("Layman Distillation Mode", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }
                                    item {
                                        Text(
                                            text = "This mode strips away industry-heavy vocabulary, buzzwords, or complicated terms. It explains the core subject matter of this webpage with simple metaphors and straightforward language.",
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp,
                                            color = Color.DarkGray
                                        )
                                    }
                                    item {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Button(
                                            onClick = {
                                                activeTab = "chat"
                                                postChatMessage("Explain this webpage's core topic in extremely simple terms to me, using direct analogies and avoiding any complex jargon.")
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                        ) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Explain Simple (ELI5)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                    // Chat Interface
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Options Row for Chatbot Role and Google Search Grounding
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            var showRoleMenu by remember { mutableStateOf(false) }
                            var currentRole by remember { mutableStateOf(settingsManager.chatbotRole) }

                            Box {
                                SuggestionChip(
                                    onClick = { showRoleMenu = true },
                                    label = { Text(currentRole, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    icon = { Icon(Icons.Default.Face, null, modifier = Modifier.size(14.dp)) }
                                )
                                DropdownMenu(
                                    expanded = showRoleMenu,
                                    onDismissRequest = { showRoleMenu = false }
                                ) {
                                    listOf("General Assistant", "Web Researcher", "Hindi Specialist", "Code Explainer").forEach { roleName ->
                                        DropdownMenuItem(
                                            text = { Text(roleName, fontSize = 12.sp) },
                                            onClick = {
                                                currentRole = roleName
                                                settingsManager.chatbotRole = roleName
                                                showRoleMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            var searchGrounding by remember { mutableStateOf(settingsManager.searchGroundingEnabled) }

                            FilterChip(
                                selected = searchGrounding,
                                onClick = {
                                    val newVal = !searchGrounding
                                    searchGrounding = newVal
                                    settingsManager.searchGroundingEnabled = newVal
                                },
                                label = { Text("Google Search Grounding", fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Public,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = if (searchGrounding) MaterialTheme.colorScheme.primary else Color.Gray
                                    )
                                }
                            )
                        }

                        // Chats Scroll Area
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            if (chatHistory.isEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChatBubbleOutline,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Session Memory Instantiated",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Ask any question, fact check statements, request bullet notes, draft highlights, or translate specific sentences on the page.",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            } else {
                                LazyColumn(
                                    state = chatListState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp),
                                    contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(chatHistory) { (role, message) ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = if (role == "user") Arrangement.End else Arrangement.Start
                                        ) {
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (role == "user") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                                ),
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier.widthIn(max = 280.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Text(
                                                        text = if (role == "user") "You" else "Specialist",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = if (role == "user") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.secondary
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(message, fontSize = 13.sp, lineHeight = 18.sp)
                                                }
                                            }
                                        }
                                    }

                                    if (chalLoading) {
                                        item {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.Start
                                            ) {
                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                                    shape = RoundedCornerShape(16.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(12.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                        Text("Drafting thinking process...", fontSize = 12.sp, color = Color.Gray)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Ten custom browser options for webpage actions
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val actionPrompts = listOf(
                                "Summarize Page" to "Perform a clear, readable summary of this whole webpage.",
                                "Explain Page" to "Explain this page in extremely clear terms to me as if explaining to a five-year-old child.",
                                "Key Points" to "Extract the top 5 key takeaways/points from this webpage.",
                                "Fact Check" to "Execute a thorough fact check of the core statistics and statements on this page.",
                                "Important Facts" to "Identify all critical facts and important statistics on this webpage.",
                                "Important People" to "List and describe all the important people and major entities mentioned on this website.",
                                "Important Dates" to "List any important timeline dates mentioned on this webpage.",
                                "Translate Summary" to "Translate the webpage summary into the active model's default output configuration.",
                                "Create Notes" to "Draft a set of structured study notes or quick bullet points based on this page.",
                                "Ask Questions" to "Generate a couple of great context-specific questions that I can follow up with on this webpage."
                            )

                            actionPrompts.forEach { (label, prompt) ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.clickable {
                                        activeTab = "chat"
                                        postChatMessage(prompt)
                                    }
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // Input bottom bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = chatInputText,
                                onValueChange = { chatInputText = it },
                                placeholder = { Text("Ask about this website...") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(24.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = {
                                    if (chatInputText.isNotBlank()) {
                                        postChatMessage(chatInputText)
                                    }
                                })
                            )

                            FloatingActionButton(
                                onClick = {
                                    if (chatInputText.isNotBlank()) {
                                        postChatMessage(chatInputText)
                                    }
                                },
                                shape = CircleShape,
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Send")
                            }
                        }
                    }
                }
            }
        }
    }
}
}
