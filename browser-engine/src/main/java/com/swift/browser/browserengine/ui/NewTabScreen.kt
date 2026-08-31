package com.swift.browser.browserengine.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.swift.browser.browserengine.BrowserUiState
import com.swift.browser.data.TopSite
import com.swift.browser.historyengine.HistoryItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NewTabScreen(
    state: BrowserUiState,
    newsState: com.swift.browser.newsengine.state.NewsUiState = com.swift.browser.newsengine.state.NewsUiState(
        feedCategory = state.feedCategory,
        isFeedLoading = state.isFeedLoading,
        articles = state.articles
    ),
    activeTab: com.swift.browser.tabengine.model.TabModel?,
    tabCount: Int,
    topSites: List<TopSite>,
    recentHistory: List<HistoryItem>,
    onSearch: (String) -> Unit,
    onArticleClick: (String) -> Unit = onSearch,
    onAddShortcut: (String, String) -> Unit,
    onRemoveTopSite: (TopSite) -> Unit,
    onCategorySelected: (String) -> Unit = {},
    onRefresh: () -> Unit = {},
    onTabSwitcherClick: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onBookmarksClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onAIChatClick: () -> Unit = {},
    onQuickToolSelected: (String) -> Unit = {},
    onVoiceClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    onRequestSearchFocus: () -> Unit = {},
    optionsMenuContent: @Composable (Boolean, () -> Unit) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { com.swift.browser.databasecore.PreferenceManager(context) }
    var searchEngineSelected by remember { mutableStateOf(prefs.getString("default_search_engine", "Google")) }
    var showEngineMenu by remember { mutableStateOf(false) }

    val speechRecognizerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrEmpty()) {
                onSearch(spokenText)
            }
        }
    }

    // Interactive dialogs
    var showAddDialog by remember { mutableStateOf(false) }
    var siteToRemove by remember { mutableStateOf<TopSite?>(null) }
    var searchInput by remember { mutableStateOf("") }
    
    val isIncognito = activeTab?.isIncognito == true

    if (isIncognito) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF202124))
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 500.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(48.dp))
                Icon(
                    imageVector = Icons.Default.PrivacyTip,
                    contentDescription = "Private Mode",
                    tint = Color(0xFFE8EAED),
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "You've entered Private Mode",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFFE8EAED),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Now you can browse privately, and other people who use this device won't see your activity. Orion does not save your browsing history, cookies, or site data after your private session ends.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF9AA0A6),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(32.dp))

                // Incognito Search Bar
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clickable { onRequestSearchFocus() },
                    shape = RoundedCornerShape(26.dp),
                    color = Color(0xFF303134),
                    border = BorderStroke(1.dp, Color(0xFF5F6368))
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).clickable { onRequestSearchFocus() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF9AA0A6))
                        Spacer(modifier = Modifier.width(12.dp))
                        BasicTextFieldWithoutLabel(
                            value = searchInput,
                            onValueChange = {
                                searchInput = it
                            },
                            onDone = {
                                if (searchInput.isNotBlank()) {
                                    onSearch(searchInput)
                                }
                            },
                            placeholder = "Search privately in Private Mode",
                            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                            modifier = Modifier.weight(1f).clickable { onRequestSearchFocus() }
                        )
                        if (searchInput.isNotEmpty()) {
                            IconButton(onClick = { searchInput = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF9AA0A6))
                            }
                        }
                    }
                }
            }
        }
    } else {
        val isDark = isSystemInDarkTheme()
        val bgBrush = if (isDark) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0F172A), // Slate 900
                    Color(0xFF020617)  // Slate 950
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFF8FAFC), // Slate 50
                    Color(0xFFEDF2F7)  // Very soft gray tint
                )
            )
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(bgBrush)
        ) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                // 1. Dynamic Header (Title, Tab Badge, Downloads Tray, Menu)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Styled Gradient 'S' Logo Container
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    if (isDark) Color(0xFF1E293B) else Color.White,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val width = size.width
                                val height = size.height
                                val gradient = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF6366F1), // Indigo
                                        Color(0xFFEC4899), // Pink
                                        Color(0xFF3B82F6)  // Blue
                                    )
                                )
                                val path = Path().apply {
                                    moveTo(width * 0.75f, height * 0.2f)
                                    cubicTo(width * 0.6f, height * 0.05f, width * 0.25f, height * 0.1f, width * 0.25f, height * 0.35f)
                                    cubicTo(width * 0.25f, height * 0.55f, width * 0.75f, height * 0.45f, width * 0.75f, height * 0.65f)
                                    cubicTo(width * 0.75f, height * 0.9f, width * 0.4f, height * 0.95f, width * 0.25f, height * 0.8f)
                                }
                                drawPath(
                                    path = path,
                                    brush = gradient,
                                    style = Stroke(
                                        width = 4.dp.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Swift Browser",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else Color(0xFF0F172A),
                            fontFamily = FontFamily.SansSerif
                        )
                    }

                    // Action Controls Block
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Square Tab Badge Trigger
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clickable { onTabSwitcherClick() }
                                .border(
                                    1.5.dp,
                                    if (isDark) Color.White.copy(alpha = 0.8f) else Color(0xFF0F172A),
                                    RoundedCornerShape(6.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tabCount.toString(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else Color(0xFF0F172A)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(14.dp))
                        
                        // Download Tray Trigger
                        IconButton(
                            onClick = { onDownloadsClick() },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Downloads Panel",
                                tint = if (isDark) Color.White else Color(0xFF0F172A),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Connect 3-dots to main browser options menu
                        var showLocalMenu by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                            IconButton(
                                onClick = { showLocalMenu = true },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    tint = if (isDark) Color.White else Color(0xFF0F172A),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            optionsMenuContent(showLocalMenu) { showLocalMenu = false }
                        }
                    }
                }

                // 2. Main Content (Scrollable Container)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Search Bar
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clickable { onRequestSearchFocus() }
                            .shadow(2.dp, RoundedCornerShape(26.dp), ambientColor = Color.Black.copy(0.05f), spotColor = Color.Black.copy(0.1f)),
                        shape = RoundedCornerShape(26.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF1E293B) else Color.White)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp).clickable { onRequestSearchFocus() },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Search Engine Selector Icon
                            Box {
                                IconButton(
                                    onClick = { showEngineMenu = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search Provider",
                                        tint = if (isDark) Color(0xFF818CF8) else Color(0xFF3B82F6),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showEngineMenu,
                                    onDismissRequest = { showEngineMenu = false }
                                ) {
                                    listOf("Google", "DuckDuckGo", "Bing", "Yahoo", "Baidu", "Ecosia").forEach { engine ->
                                        DropdownMenuItem(
                                            text = { Text(engine) },
                                            trailingIcon = if (searchEngineSelected == engine) {
                                                { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                            } else null,
                                            onClick = {
                                                searchEngineSelected = engine
                                                prefs.setString("default_search_engine", engine)
                                                showEngineMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            BasicTextFieldWithoutLabel(
                                value = searchInput,
                                onValueChange = {
                                    searchInput = it
                                },
                                onDone = {
                                    if (searchInput.isNotBlank()) {
                                        onSearch(searchInput)
                                    }
                                },
                                placeholder = "Search or enter URL",
                                textStyle = TextStyle(
                                    color = if (isDark) Color.White else Color(0xFF0F172A),
                                    fontSize = 15.sp
                                ),
                                modifier = Modifier.weight(1f).clickable { onRequestSearchFocus() }
                            )

                            if (searchInput.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchInput = "" },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear text",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            
                            IconButton(
                                onClick = {
                                    val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
                                        putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak to search...")
                                    }
                                    try {
                                        speechRecognizerLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        onVoiceClick()
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Mic, "Voice", tint = Color(0xFF818CF8))
                            }
                            
                            IconButton(
                                onClick = {
                                    Toast.makeText(context, "Scanning QR / Barcode...", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.QrCodeScanner, "Scan", tint = Color.LightGray)
                            }
                            
                            IconButton(
                                onClick = { onAIChatClick() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, "AI", tint = Color(0xFF818CF8))
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Quick Access Section
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⚡", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Quick Access", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color.White else Color(0xFF0F172A))
                        }
                        Text(
                            text = "Manage",
                            fontSize = 12.sp,
                            color = Color(0xFF3B82F6),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable {
                                onBookmarksClick()
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val firstHistoryItem = recentHistory.firstOrNull { it.url != "swift://newtab" && it.url != "swift://newtab-incognito" }
                        if (firstHistoryItem != null) {
                            item {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable { onSearch(firstHistoryItem.url) }
                                        .width(56.dp)
                                ) {
                                    var domain = ""
                                    try {
                                        domain = android.net.Uri.parse(firstHistoryItem.url).host?.replace("www.", "") ?: "Site"
                                    } catch (e: Exception) {
                                        domain = "Site"
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .background(Color(0xFF3B82F6).copy(alpha = 0.15f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = domain.take(1).uppercase(),
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF3B82F6)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .align(Alignment.BottomEnd)
                                                .background(if (isDark) Color(0xFF0F172A) else Color.White, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Schedule,
                                                contentDescription = "Continue",
                                                modifier = Modifier.size(12.dp),
                                                tint = Color.Gray
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Continue",
                                        fontSize = 11.sp,
                                        color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        
                        items(topSites) { site ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .combinedClickable(
                                        onClick = { onSearch(site.url) },
                                        onLongClick = { siteToRemove = site }
                                    )
                                    .width(56.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(if (isDark) Color(0xFF1E293B) else Color.White, CircleShape)
                                        .border(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val domainHost = try {
                                        android.net.Uri.parse(site.url).host ?: ""
                                    } catch (e: Exception) { "" }
                                    
                                    val siteUrlLower = site.url.lowercase()
                                                                        val isGoogle = siteUrlLower.contains("google.com") && !siteUrlLower.contains("chromewebstore")

                                    when {
                                        isGoogle -> {
                                            Text(
                                                text = "G",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 22.sp,
                                                color = Color(0xFF4285F4)
                                            )
                                        }
                                        domainHost.isNotBlank() -> {
                                            AsyncImage(
                                                model = "https://www.google.com/s2/favicons?domain=${site.url}&sz=128",
                                                contentDescription = site.title,
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                            )
                                        }
                                        else -> {
                                            Text(
                                                text = site.title.take(1).uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = site.title,
                                    fontSize = 11.sp,
                                    color = if (isDark) Color.LightGray else Color.DarkGray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        
                        item {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable { showAddDialog = true }
                                    .width(56.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(if (isDark) Color(0xFF1E293B) else Color.White, CircleShape)
                                        .border(1.dp, Color.Gray.copy(0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Shortcut",
                                        tint = if (isDark) Color.White else Color.Black,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Add",
                                    fontSize = 11.sp,
                                    color = if (isDark) Color.LightGray else Color.DarkGray
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // 3. AI Chat & Tools Section (Matching Video)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFFA855F7),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "AI Chat & Tools",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else Color(0xFF0F172A)
                            )
                        }
                        Text(
                            text = "View All >",
                            fontSize = 12.sp,
                            color = Color(0xFFA855F7),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { onAIChatClick() }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 4 AI Cards Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Card 1: AI Chat
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(76.dp)
                                .clickable { onAIChatClick() },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF581C87).copy(alpha = 0.6f)),
                            border = BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color(0xFFA855F7).copy(alpha = 0.25f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Chat,
                                        contentDescription = "AI Chat",
                                        tint = Color(0xFFE9D5FF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "AI Chat",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }
                        }

                        // Card 2: Image Gen
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(76.dp)
                                .clickable { onQuickToolSelected("image") },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A8A).copy(alpha = 0.6f)),
                            border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color(0xFF3B82F6).copy(alpha = 0.25f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = "Image Gen",
                                        tint = Color(0xFFBFDBFE),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Image Gen",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }
                        }

                        // Card 3: Summarizer
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(76.dp)
                                .clickable { onQuickToolSelected("summarizer") },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B).copy(alpha = 0.6f)),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color(0xFF10B981).copy(alpha = 0.25f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Subject,
                                        contentDescription = "Summarizer",
                                        tint = Color(0xFFA7F3D0),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Summarizer",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }
                        }

                        // Card 4: Translator
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(76.dp)
                                .clickable { onQuickToolSelected("translate") },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF7C2D12).copy(alpha = 0.6f)),
                            border = BorderStroke(1.dp, Color(0xFFF97316).copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color(0xFFF97316).copy(alpha = 0.25f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Translate,
                                        contentDescription = "Translator",
                                        tint = Color(0xFFFED7AA),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Translator",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Smart Widgets Section
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Smart Widgets",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else Color(0xFF0F172A)
                            )
                        }
                        Text(
                            text = "Customize >",
                            fontSize = 12.sp,
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3 Smart Widget Cards Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Weather Widget Card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(76.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                            ),
                            border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(Color(0xFFF59E0B).copy(alpha = 0.18f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WbSunny,
                                        contentDescription = "Weather",
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Pramod 28°C",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color.White else Color(0xFF0F172A),
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "Mostly Sunny",
                                        fontSize = 9.sp,
                                        color = Color.Gray,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Antivirus Card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(76.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                            ),
                            border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(Color(0xFF10B981).copy(alpha = 0.18f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VerifiedUser,
                                        contentDescription = "Antivirus",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Antivirus",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color.White else Color(0xFF0F172A),
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "Scan Now",
                                        fontSize = 9.sp,
                                        color = Color(0xFF10B981),
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Data Saver Card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(76.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                            ),
                            border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(Color(0xFF3B82F6).copy(alpha = 0.18f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DataSaverOn,
                                        contentDescription = "Data Saver",
                                        tint = Color(0xFF3B82F6),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Data Saver",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color.White else Color(0xFF0F172A),
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "Active",
                                        fontSize = 9.sp,
                                        color = Color(0xFF3B82F6),
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    // News Section (Engine Owned)
                    com.swift.browser.newsengine.ui.NewsSection(
                        state = newsState,
                        onCategorySelected = onCategorySelected,
                        onArticleClick = onArticleClick
                    )

                    Spacer(modifier = Modifier.height(80.dp))
                }
            }


        }
    }

    // A. Add Custom Shortcut Dialog
    if (showAddDialog) {
        var addTitle by remember { mutableStateOf("") }
        var addUrl by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showAddDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = if (isSystemInDarkTheme()) Color(0xFF1E293B) else Color.White
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Add Shortcut",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSystemInDarkTheme()) Color.White else Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = addTitle,
                        onValueChange = { addTitle = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = addUrl,
                        onValueChange = { addUrl = it },
                        label = { Text("URL (e.g. google.com)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAddDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                if (addUrl.isNotBlank()) {
                                    val formatted = if (!addUrl.contains("://")) "https://$addUrl" else addUrl
                                    onAddShortcut(addTitle.ifBlank { addUrl }, formatted)
                                    showAddDialog = false
                                }
                            },
                            enabled = addUrl.isNotBlank()
                        ) {
                            Text("Add")
                        }
                    }
                }
            }
        }
    }

    // B. Remove Top Site Dialog
    if (siteToRemove != null) {
        val targetSite = siteToRemove!!
        AlertDialog(
            onDismissRequest = { siteToRemove = null },
            title = { Text("Remove Shortcut") },
            text = { Text("Remove \"${targetSite.title}\" from your shortcuts?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveTopSite(targetSite)
                        siteToRemove = null
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { siteToRemove = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// Custom Basic Text Field helper
@Composable
fun BasicTextFieldWithoutLabel(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    placeholder: String,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                color = textStyle.color.copy(alpha = 0.5f),
                fontSize = textStyle.fontSize
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = textStyle,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search,
                keyboardType = KeyboardType.Text
            ),
            keyboardActions = KeyboardActions(
                onSearch = { onDone() },
                onGo = { onDone() },
                onDone = { onDone() }
            ),
            cursorBrush = SolidColor(textStyle.color),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        )
    }
}
