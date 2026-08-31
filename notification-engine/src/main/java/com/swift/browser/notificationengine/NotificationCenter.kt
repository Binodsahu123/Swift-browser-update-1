package com.swift.browser.notificationengine

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterScreen(
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val repository = remember { com.swift.browser.notificationengine.data.NotificationRepository(context) }
    val historyManager = remember { NotificationHistoryManager(context) }
    val settingsManager = remember { NotificationSettingsManager(context) }

    // State bindings
    val historyList by repository.getAllHistoryFlow().collectAsState(initial = emptyList())
    val subscriptionsList by repository.getAllSubscriptionsFlow().collectAsState(initial = emptyList())

    // Tabs
    var selectedTab by remember { mutableStateOf(0) } // 0 = Inbox, 1 = Config/Websites, 2 = Diagnostic Panels

    // Filter operations
    var searchQuery by remember { mutableStateOf("") }
    var ageFilter by remember { mutableStateOf("all") } // all, today, yesterday, last_7_days
    var sourceFilter by remember { mutableStateOf("all") } // all, websiteURL

    // Active diagnostic status state
    var engineStatus by remember { mutableStateOf("PENDING") }
    LaunchedEffect(Unit) {
        BackgroundNotificationService.getEngineStatus(context) { status ->
            engineStatus = status
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notification Engine Center") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(onClick = {
                            scope.launch { historyManager.clearHistory() }
                        }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All Logs")
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Screen Navigation Tabs
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Inbox / Logs") },
                    icon = { Icon(Icons.Default.Notifications, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Websites Config") },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Diagnostic") },
                    icon = { Icon(Icons.Default.Build, contentDescription = null) }
                )
            }

            when (selectedTab) {
                0 -> InboxTab(
                    items = historyList,
                    subscriptions = subscriptionsList,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    ageFilter = ageFilter,
                    onAgeFilterChange = { ageFilter = it },
                    sourceFilter = sourceFilter,
                    onSourceFilterChange = { sourceFilter = it },
                    onItemClick = { item ->
                        scope.launch { historyManager.markAsRead(item.id) }
                        onOpenUrl(item.clickUrl)
                    },
                    onItemDelete = { item ->
                        scope.launch { historyManager.deleteItem(item.id) }
                    },
                    historyManager = historyManager
                )
                1 -> WebsitesConfigTab(
                    subscriptions = subscriptionsList,
                    settingsManager = settingsManager,
                    onDeleteSub = { sub ->
                        scope.launch { repository.deleteSubscription(sub.websiteUrl) }
                    }
                )
                2 -> DiagnosticTab(
                    engineStatus = engineStatus,
                    subscriptions = subscriptionsList,
                    onForceSync = {
                        BackgroundNotificationService.forceImmediateSync(context)
                        scope.launch {
                            BackgroundNotificationService.getEngineStatus(context) { status ->
                                engineStatus = status
                            }
                        }
                    },
                    onToggleEngine = {
                        if (engineStatus == "INACTIVE") {
                            BackgroundNotificationService.startEngine(context)
                        } else {
                            BackgroundNotificationService.stopEngine(context)
                        }
                        scope.launch {
                            BackgroundNotificationService.getEngineStatus(context) { status ->
                                engineStatus = status
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun InboxTab(
    items: List<NotificationHistoryItem>,
    subscriptions: List<NotificationSubscription>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    ageFilter: String,
    onAgeFilterChange: (String) -> Unit,
    sourceFilter: String,
    onSourceFilterChange: (String) -> Unit,
    onItemClick: (NotificationHistoryItem) -> Unit,
    onItemDelete: (NotificationHistoryItem) -> Unit,
    historyManager: NotificationHistoryManager
) {
    // Apply filters
    var filtered = historyManager.filterHistory(items, ageFilter)
    filtered = historyManager.searchHistory(filtered, searchQuery)
    if (sourceFilter != "all") {
        filtered = filtered.filter { it.websiteUrl == sourceFilter }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search & Filter Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search updates...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Time Interval filter chips
                    val ageFilters = listOf("all" to "All Time", "today" to "Today", "yesterday" to "Yesterday", "last_7_days" to "7 Days")
                    LazyColumn(
                        modifier = Modifier.weight(1f).height(62.dp)
                    ) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                ageFilters.forEach { (key, label) ->
                                    FilterChip(
                                        selected = ageFilter == key,
                                        onClick = { onAgeFilterChange(key) },
                                        label = { Text(label, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }

                    // Source website filter dropdown equivalent
                    var showSourcesDrop by remember { mutableStateOf(false) }
                    Box {
                        Button(onClick = { showSourcesDrop = true }) {
                            Text("Source", fontSize = 12.sp)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showSourcesDrop,
                            onDismissRequest = { showSourcesDrop = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Websites") },
                                onClick = {
                                    onSourceFilterChange("all")
                                    showSourcesDrop = false
                                }
                            )
                            subscriptions.forEach { sub ->
                                DropdownMenuItem(
                                    text = { Text(sub.websiteName) },
                                    onClick = {
                                        onSourceFilterChange(sub.websiteUrl)
                                        showSourcesDrop = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.NotificationsOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No updates found in Inbox.", fontWeight = FontWeight.Bold)
                    Text("Allow notification permissions on websites to see alerts here.", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onItemClick(item) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (item.isRead) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item.websiteName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = formatTime(item.timestamp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.title,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.body,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { onItemDelete(item) }) {
                                Icon(Icons.Default.Clear, contentDescription = "Delete Item", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WebsitesConfigTab(
    subscriptions: List<NotificationSubscription>,
    settingsManager: NotificationSettingsManager,
    onDeleteSub: (NotificationSubscription) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    if (subscriptions.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.Language,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("No Registered Websites", fontWeight = FontWeight.Bold)
                Text("Manage permissions directly during browsing.", style = MaterialTheme.typography.bodySmall)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(subscriptions, key = { it.websiteUrl }) { sub ->
                var expandedSettings by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(sub.websiteName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(sub.websiteUrl, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            
                            // Permission Level Indicator / Button
                            val permissionLabel = when(sub.permission) {
                                "ALLOW" -> "Allowed"
                                "BLOCK" -> "Blocked"
                                else -> "Blocked / Block"
                            }
                            
                            AssistChip(
                                onClick = { 
                                    scope.launch {
                                        val newPermission = if (sub.permission == "ALLOW") "BLOCK" else "ALLOW"
                                        val store = WebsitePermissionStore(context)
                                        store.setPermission(sub.websiteUrl, sub.websiteName, newPermission)
                                        settingsManager.updateConfig(
                                            sub.websiteUrl,
                                            isMuted = newPermission == "BLOCK",
                                            priority = sub.priority,
                                            soundEnabled = sub.soundEnabled,
                                            vibrationEnabled = sub.vibrationEnabled
                                        )
                                    }
                                },
                                label = { Text(permissionLabel) },
                                leadingIcon = {
                                    Icon(
                                        if (sub.permission == "ALLOW") Icons.Default.Check else Icons.Default.Block,
                                        contentDescription = null,
                                        tint = if (sub.permission == "ALLOW") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                            
                            IconButton(onClick = { expandedSettings = !expandedSettings }) {
                                Icon(
                                    if (expandedSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Expand Per-Site Controls"
                                )
                            }
                        }

                        // Detailed per-website configs
                        AnimatedVisibility(visible = expandedSettings) {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                Divider()
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Priority control Slider/Switch
                                Text("Alert Priority", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = sub.priority == 0,
                                        onClick = { scope.launch { settingsManager.updateConfig(sub.websiteUrl, sub.isMuted, 0, sub.soundEnabled, sub.vibrationEnabled) }},
                                        label = { Text("Low") }
                                    )
                                    FilterChip(
                                        selected = sub.priority == 1,
                                        onClick = { scope.launch { settingsManager.updateConfig(sub.websiteUrl, sub.isMuted, 1, sub.soundEnabled, sub.vibrationEnabled) }},
                                        label = { Text("Normal") }
                                    )
                                    FilterChip(
                                        selected = sub.priority == 2,
                                        onClick = { scope.launch { settingsManager.updateConfig(sub.websiteUrl, sub.isMuted, 2, sub.soundEnabled, sub.vibrationEnabled) }},
                                        label = { Text("High") }
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Toggle switches
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Sound Alerts")
                                    Switch(
                                        checked = sub.soundEnabled,
                                        onCheckedChange = { checked ->
                                            scope.launch { settingsManager.updateConfig(sub.websiteUrl, sub.isMuted, sub.priority, checked, sub.vibrationEnabled) }
                                        }
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Vibrate Alerts")
                                    Switch(
                                        checked = sub.vibrationEnabled,
                                        onCheckedChange = { checked ->
                                            scope.launch { settingsManager.updateConfig(sub.websiteUrl, sub.isMuted, sub.priority, sub.soundEnabled, checked) }
                                        }
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Mute Notifications")
                                    Switch(
                                        checked = sub.isMuted,
                                        onCheckedChange = { checked ->
                                            scope.launch { settingsManager.updateConfig(sub.websiteUrl, checked, sub.priority, sub.soundEnabled, sub.vibrationEnabled) }
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Pausing Controls
                                Text("Pause Updates Until", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val isPaused = sub.pauseUntil > System.currentTimeMillis()
                                    if (isPaused) {
                                        Button(onClick = { scope.launch { settingsManager.resumeNotifications(sub.websiteUrl) } }) {
                                            Text("Resume Now", fontSize = 11.sp)
                                        }
                                    } else {
                                        val pauseDurations = listOf("1 Hr" to 3600000L, "8 Hr" to 28800000L, "24 Hr" to 86400000L)
                                        pauseDurations.forEach { (label, duration) ->
                                            OutlinedButton(onClick = { scope.launch { settingsManager.pauseNotifications(sub.websiteUrl, duration) } }) {
                                                Text(label, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = { onDeleteSub(sub) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Delete Entire Core Subscription")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticTab(
    engineStatus: String,
    subscriptions: List<NotificationSubscription>,
    onForceSync: () -> Unit,
    onToggleEngine: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("System Diagnostic Monitor", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Active Background Engine State:")
                        Text(engineStatus, fontWeight = FontWeight.Bold, color = if (engineStatus == "ENQUEUED" || engineStatus == "RUNNING") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Active Subscriptions Count:")
                        Text("${subscriptions.size}", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(onClick = onForceSync, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Force Sync Now")
                        }
                        
                        OutlinedButton(onClick = onToggleEngine, modifier = Modifier.weight(1f)) {
                            Text(if (engineStatus == "INACTIVE") "Start Engine" else "Stop Engine")
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Platform Capabilities Audit", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val audits = NotificationRegistry.getWebsitesSupportStatus()
                    audits.forEach { (site, status) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(site, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.3f))
                            Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.7f))
                        }
                        Divider()
                    }
                }
            }
        }
    }
}

fun formatTime(timestamp: Long): String {
    return try {
        val sdf = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(timestamp))
    } catch (e: Exception) {
        "Just now"
    }
}
