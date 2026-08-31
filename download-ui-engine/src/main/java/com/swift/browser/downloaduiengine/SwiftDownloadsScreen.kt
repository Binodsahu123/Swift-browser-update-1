package com.swift.browser.downloaduiengine

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.downloadengine.DownloadConfig
import com.swift.browser.downloadengine.DownloadEngine
import com.swift.browser.downloadengine.DownloadItem
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DownloadSection(val title: String, val icon: ImageVector) {
    ALL("All Downloads", Icons.Default.AllInbox),
    ACTIVE("Active Downloads", Icons.Default.PlayArrow),
    COMPLETED("Completed Downloads", Icons.Default.CheckCircle),
    PAUSED("Paused Downloads", Icons.Default.PauseCircle),
    FAILED("Failed Downloads", Icons.Default.Error),
    SCHEDULED("Scheduled Downloads", Icons.Default.Schedule),
    QUEUE("Download Queue", Icons.Default.Queue),
    HISTORY("History Logs", Icons.Default.History),
    SETTINGS("Download Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwiftDownloadsScreen(
    engine: DownloadEngine,
    onBack: () -> Unit,
    onOpenFile: (filePath: String, fileName: String, mimeType: String) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToUrl: ((String) -> Unit)? = null,
    onLaunchDiagnostics: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Screen-level state
    var selectedSection by remember { mutableStateOf(DownloadSection.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Core database lists
    var fullDownloadsList by remember { mutableStateOf<List<DownloadItem>>(emptyList()) }
    var currentConfig by remember { mutableStateOf(engine.getConfig()) }

    // Dialog state variables
    var itemToRename by remember { mutableStateOf<DownloadItem?>(null) }
    var renameInputName by remember { mutableStateOf("") }
    var itemToDelete by remember { mutableStateOf<DownloadItem?>(null) }
    var deletePhysicalFileCheckbox by remember { mutableStateOf(true) }
    var itemToSchedule by remember { mutableStateOf<DownloadItem?>(null) }

    // History paging
    var historyPageSize by remember { mutableIntStateOf(10) }

    // Collect list updates
    LaunchedEffect(Unit) {
        engine.getDownloadsFlow().collectLatest { list ->
            fullDownloadsList = list
        }
    }

    // Dynamic config collection (triggers once at start)
    LaunchedEffect(Unit) {
        currentConfig = engine.getConfig()
    }

    // Base background styling: Deep metallic slate theme
    val backgroundBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0F0F14),
                Color(0xFF14141E)
            )
        )
    }

    // Calculated badge counts
    val allCount = fullDownloadsList.size
    val activeCount = fullDownloadsList.count { it.status == "RUNNING" || it.status == "PENDING" }
    val completedCount = fullDownloadsList.count { it.status == "COMPLETED" }
    val pausedCount = fullDownloadsList.count { it.status == "PAUSED" }
    val failedCount = fullDownloadsList.count { it.status == "FAILED" }
    val scheduledCount = fullDownloadsList.count { it.status == "SCHEDULED" }
    val queueCount = activeCount + scheduledCount

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // SCREEN TOP BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF1E1E28), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = "Swift Download Center",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Chrome-level stability • High-performance segments",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }

            // SECTION NAVIGATION DRAWER ROW (Swipeable pills for clean navigation)
            ScrollableTabRow(
                selectedTabIndex = selectedSection.ordinal,
                containerColor = Color.Transparent,
                edgePadding = 16.dp,
                divider = {},
                indicator = {}
            ) {
                DownloadSection.values().forEach { sec ->
                    val isSelected = selectedSection == sec
                    val countText = when (sec) {
                        DownloadSection.ALL -> "($allCount)"
                        DownloadSection.ACTIVE -> "($activeCount)"
                        DownloadSection.COMPLETED -> "($completedCount)"
                        DownloadSection.PAUSED -> "($pausedCount)"
                        DownloadSection.FAILED -> "($failedCount)"
                        DownloadSection.SCHEDULED -> "($scheduledCount)"
                        DownloadSection.QUEUE -> "($queueCount)"
                        else -> ""
                    }
                    
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp, bottom = 4.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) Color(0xFFFF2E2E)
                                else Color(0xFF1E1E2A)
                            )
                            .clickable { selectedSection = sec }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = sec.icon,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${sec.title} $countText",
                                color = if (isSelected) Color.White else Color.LightGray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // SEARCH BAR (Always present except in Settings)
            if (selectedSection != DownloadSection.SETTINGS) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search files by name...", color = Color.Gray, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFF2E2E),
                        unfocusedBorderColor = Color(0xFF2C2C3C),
                        focusedContainerColor = Color(0xFF15151F),
                        unfocusedContainerColor = Color(0xFF15151F)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // CONTENT HANDLER BY SELECTION
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedSection) {
                    DownloadSection.SETTINGS -> {
                        DownloadSettingsUI(
                            config = currentConfig,
                            engine = engine,
                            onConfigChanged = { currentConfig = it },
                            onLaunchDiagnostics = onLaunchDiagnostics ?: {}
                        )
                    }
                    else -> {
                        val filteredList = remember(fullDownloadsList, selectedSection, searchQuery) {
                            var intermediate = when (selectedSection) {
                                DownloadSection.ALL -> fullDownloadsList
                                DownloadSection.ACTIVE -> fullDownloadsList.filter { it.status == "RUNNING" || it.status == "PENDING" }
                                DownloadSection.COMPLETED -> fullDownloadsList.filter { it.status == "COMPLETED" }
                                DownloadSection.PAUSED -> fullDownloadsList.filter { it.status == "PAUSED" }
                                DownloadSection.FAILED -> fullDownloadsList.filter { it.status == "FAILED" }
                                DownloadSection.SCHEDULED -> fullDownloadsList.filter { it.status == "SCHEDULED" }
                                DownloadSection.QUEUE -> fullDownloadsList.filter { it.status == "PENDING" || it.status == "RUNNING" || it.status == "SCHEDULED" }
                                DownloadSection.HISTORY -> fullDownloadsList.filter { it.status == "COMPLETED" }
                                else -> fullDownloadsList
                            }
                            if (searchQuery.trim().isNotEmpty()) {
                                intermediate = intermediate.filter { it.title.contains(searchQuery, ignoreCase = true) }
                            }
                            intermediate
                        }

                        val listToShow = if (selectedSection == DownloadSection.HISTORY) {
                            filteredList.take(historyPageSize)
                        } else {
                            filteredList
                        }

                        if (listToShow.isEmpty()) {
                            EmptySectionSplash(selectedSection)
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                LazyColumn(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(listToShow, key = { it.id }) { item ->
                                        if (item.status == "COMPLETED") {
                                            CompletedDownloadCard(
                                                item = item,
                                                onOpen = { onOpenFile(item.filePath, item.title, item.mimeType) },
                                                onShare = { shareFile(context, item.filePath, item.mimeType) },
                                                onRenameRequest = {
                                                    itemToRename = item
                                                    renameInputName = item.title.substringBeforeLast(".")
                                                },
                                                onDeleteRequest = {
                                                    itemToDelete = item
                                                    deletePhysicalFileCheckbox = true
                                                },
                                                onLocateRequest = { openFileDirectory(context, item.filePath) }
                                            )
                                        } else {
                                            ActiveDownloadCard(
                                                item = item,
                                                onPause = { coroutineScope.launch { engine.pauseDownload(item.id) } },
                                                onResume = { coroutineScope.launch { engine.resumeDownload(item.id) } },
                                                onCancel = { coroutineScope.launch { engine.cancelDownload(item.id) } },
                                                onLocate = { openFileDirectory(context, item.filePath) },
                                                onScheduleClick = { itemToSchedule = item }
                                            )
                                        }
                                    }
                                    
                                    if (selectedSection == DownloadSection.HISTORY && filteredList.size > historyPageSize) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 12.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Button(
                                                    onClick = { historyPageSize += 10 },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E28)),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text("Load More (${filteredList.size - historyPageSize} remaining)", color = Color.White, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // RENAME DIALOG WINDOW
        if (itemToRename != null) {
            val originalItem = itemToRename!!
            AlertDialog(
                onDismissRequest = { itemToRename = null },
                containerColor = Color(0xFF161622),
                titleContentColor = Color.White,
                textContentColor = Color.LightGray,
                title = { Text("Rename Downloaded File", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Provide a new physical name for the file:", fontSize = 12.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = renameInputName,
                            onValueChange = { renameInputName = it },
                            placeholder = { Text("Enter new name") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFFF2E2E),
                                unfocusedBorderColor = Color(0xFF2C2C3C),
                                focusedContainerColor = Color(0xFF0F0F14),
                                unfocusedContainerColor = Color(0xFF0F0F14)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2E2E)),
                        onClick = {
                            val targetItem = originalItem
                            val ext = targetItem.filePath.substringAfterLast(".", "")
                            val finalNewName = if (ext.isNotEmpty()) "$renameInputName.$ext" else renameInputName
                            
                            coroutineScope.launch {
                                val oldFile = File(targetItem.filePath)
                                if (oldFile.exists()) {
                                    val newFile = File(oldFile.parentFile, finalNewName)
                                    if (oldFile.renameTo(newFile)) {
                                        val updatedItem = targetItem.copy(
                                            title = finalNewName,
                                            filePath = newFile.absolutePath
                                        )
                                        engine.startDownload(updatedItem.url, updatedItem.title, updatedItem.mimeType, updatedItem.threads)
                                        engine.deleteDownload(targetItem.id)
                                    }
                                }
                                itemToRename = null
                            }
                        }
                    ) {
                        Text("Rename", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { itemToRename = null }) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            )
        }

        // PHYSICAL DELETION DIALOG
        if (itemToDelete != null) {
            val target = itemToDelete!!
            AlertDialog(
                onDismissRequest = { itemToDelete = null },
                containerColor = Color(0xFF161622),
                titleContentColor = Color.White,
                textContentColor = Color.LightGray,
                title = { Text("Delete Download Record?", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Are you sure you want to remove this download record from database logs?", fontSize = 13.sp)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { deletePhysicalFileCheckbox = !deletePhysicalFileCheckbox }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = deletePhysicalFileCheckbox,
                                onCheckedChange = { deletePhysicalFileCheckbox = it },
                                colors = CheckboxDefaults.colors(checkedColor = Color(0xFFFF2E2E))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete actual downloaded file from device storage", fontSize = 13.sp, color = Color.White)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2E2E)),
                        onClick = {
                            coroutineScope.launch {
                                if (deletePhysicalFileCheckbox) {
                                    try {
                                        val file = File(target.filePath)
                                        if (file.exists()) file.delete()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                engine.deleteDownload(target.id)
                                itemToDelete = null
                            }
                        }
                    ) {
                        Text("Remove", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { itemToDelete = null }) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            )
        }

        // TIMER SCHEDULER POPUP DIALOG
        if (itemToSchedule != null) {
            val target = itemToSchedule!!
            AlertDialog(
                onDismissRequest = { itemToSchedule = null },
                containerColor = Color(0xFF161622),
                titleContentColor = Color.White,
                textContentColor = Color.LightGray,
                title = { Text("Schedule Download Task", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Select delay to initiate download queue process automatically:", fontSize = 12.sp, color = Color.Gray)
                        
                        val times = listOf(
                            Pair("Delay 1 Min", 60L * 1000),
                            Pair("Delay 5 Mins", 5L * 60 * 1000),
                            Pair("Delay 15 Mins", 15L * 60 * 1000),
                            Pair("Delay 1 Hour", 60L * 60 * 1000),
                            Pair("Delay 4 Hours", 4L * 3600 * 1000)
                        )

                        times.forEach { (label, duration) ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F14)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch {
                                            engine.pauseDownload(target.id)
                                            val scheduledTime = System.currentTimeMillis() + duration
                                            val scheduledItem = target.copy(
                                                status = "SCHEDULED",
                                                scheduledTime = scheduledTime,
                                                speed = "Scheduled"
                                            )
                                            engine.startDownload(scheduledItem.url, scheduledItem.title, scheduledItem.mimeType, scheduledItem.threads)
                                            val db = com.swift.browser.downloadengine.DownloadDatabase.getDatabase(context)
                                            db.downloadDao().insertDownload(scheduledItem)
                                            itemToSchedule = null
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.AccessTime, contentDescription = null, tint = Color(0xFFFF2E2E), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(label, color = Color.White, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { itemToSchedule = null }) {
                        Text("Close", color = Color.Gray)
                    }
                }
            )
        }
    }
}

// EMPTY SPLASH FOR ZERO ITEMS
@Composable
fun EmptySectionSplash(section: DownloadSection) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = section.icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color.Gray.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "No items in ${section.title}",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Tasks matching this state will automatically appear right here.",
            color = Color.Gray,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

// ACTIVE PROGRESS CARDS
@Composable
fun ActiveDownloadCard(
    item: DownloadItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onLocate: () -> Unit,
    onScheduleClick: () -> Unit
) {
    val remainingTime = remember(item.downloadedSize, item.totalSize, item.speed) {
        calculateRemainingTime(item.downloadedSize, item.totalSize, item.speed)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
        border = BorderStroke(1.dp, Color(0xFF232333)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFF2E2E).copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getCategoryIcon(item.category),
                        contentDescription = null,
                        tint = Color(0xFFFF2E2E),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (item.status == "SCHEDULED") "Scheduled" else item.status,
                            color = when (item.status) {
                                "PENDING" -> Color.Yellow
                                "RUNNING" -> Color(0xFF25D366)
                                "PAUSED" -> Color.LightGray
                                "SCHEDULED" -> Color(0xFF00B0FF)
                                else -> Color.Red
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Divider(
                            modifier = Modifier
                                .height(10.dp)
                                .width(1.dp),
                            color = Color.DarkGray
                        )
                        Text(
                            text = getMetadataRow(item),
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (item.status == "SCHEDULED") {
                val formattedTime = remember(item.scheduledTime) {
                    val date = Date(item.scheduledTime)
                    val format = SimpleDateFormat("hh:mm a", Locale.getDefault())
                    format.format(date)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0F0F14))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Scheduled launch at $formattedTime",
                        color = Color(0xFF00B0FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${item.progress}% Finished",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (item.status == "RUNNING") "Speed: ${item.speed}" else "State: ${item.status}",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = item.progress.toFloat() / 100f,
                    trackColor = Color(0xFF222232),
                    color = Color(0xFFFF2E2E),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = remainingTime,
                        color = if (remainingTime.contains("remaining")) Color(0xFF25D366) else Color.Gray,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "Threads: ${item.threads}",
                        color = Color.DarkGray,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (item.status == "RUNNING" || item.status == "PENDING" || item.status == "SCHEDULED") {
                    Button(
                        onClick = onPause,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E2C)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pause", color = Color.White, fontSize = 11.sp)
                    }
                } else if (item.status == "PAUSED" || item.status == "FAILED" || item.status == "CANCELLED") {
                    Button(
                        onClick = onResume,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2E2E)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Resume", color = Color.White, fontSize = 11.sp)
                    }
                }

                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E2C)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cancel", color = Color.Gray, fontSize = 11.sp)
                }

                Button(
                    onClick = onScheduleClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E2C)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Schedule", color = Color.Gray, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = onLocate,
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color(0xFF1E1E2C), RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.Folder, contentDescription = "Locate", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// COMPLETED ARCHIVED CARD
@Composable
fun CompletedDownloadCard(
    item: DownloadItem,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRenameRequest: () -> Unit,
    onDeleteRequest: () -> Unit,
    onLocateRequest: () -> Unit
) {
    val completedDateStr = remember(item.timestamp) {
        val date = Date(item.timestamp)
        val format = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        format.format(date)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF15151F)),
        border = BorderStroke(1.dp, Color(0xFF20202F)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF25D366).copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getCategoryIcon(item.category),
                        contentDescription = null,
                        tint = Color(0xFF25D366),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatBytes(item.totalSize),
                            color = Color.DarkGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Divider(
                            modifier = Modifier
                                .height(10.dp)
                                .width(1.dp),
                            color = Color.DarkGray
                        )
                        Text(
                            text = completedDateStr,
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onOpen,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2E2E)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Launch, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Open", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onShare,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22222F)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share", color = Color.LightGray, fontSize = 11.sp)
                }

                Button(
                    onClick = onRenameRequest,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22222F)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rename", color = Color.LightGray, fontSize = 11.sp)
                }

                Button(
                    onClick = onDeleteRequest,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22222F)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", color = Color.Gray, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = onLocateRequest,
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFF22222F), RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.Folder, contentDescription = "Locate", tint = Color.LightGray, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

// SETTINGS VIEW
@Composable
fun DownloadSettingsUI(
    config: DownloadConfig,
    engine: DownloadEngine,
    onConfigChanged: (DownloadConfig) -> Unit,
    onLaunchDiagnostics: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var threadsCount by remember { mutableFloatStateOf(config.maxThreadsPerDownload.toFloat()) }
    var concurrentCount by remember { mutableFloatStateOf(config.maxConcurrentDownloads.toFloat()) }
    var wifiOnlyMode by remember { mutableStateOf(config.wifiOnly) }
    var autoResumeMode by remember { mutableStateOf(config.autoResume) }
    var smartRetryMode by remember { mutableStateOf(true) }
    var notificationsMode by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        
        Text("SPEED & CONCURRENCY", color = Color(0xFFFF2E2E), fontSize = 11.sp, fontWeight = FontWeight.Bold)

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF15151F)),
            border = BorderStroke(1.dp, Color(0xFF22222F)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Connection Threads per Download", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Higher threads speeds up downloads up to 32x (IDM-level)", color = Color.Gray, fontSize = 11.sp)
                    }
                    Text("${threadsCount.toInt()} Threads", color = Color(0xFFFF2E2E), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                
                Slider(
                    value = threadsCount,
                    onValueChange = {
                        val activeVal = when (it.toInt()) {
                            in 1..2 -> 2
                            in 3..4 -> 4
                            in 5..8 -> 8
                            in 9..16 -> 16
                            else -> 32
                        }
                        threadsCount = activeVal.toFloat()
                        val newConf = config.copy(maxThreadsPerDownload = activeVal)
                        engine.setConfig(newConf)
                        onConfigChanged(newConf)
                    },
                    valueRange = 1f..32f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFF2E2E),
                        activeTrackColor = Color(0xFFFF2E2E),
                        inactiveTrackColor = Color(0xFF2E2E3E)
                    ),
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF15151F)),
            border = BorderStroke(1.dp, Color(0xFF22222F)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Maximum Concurrent Downloads", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Maximum files to process inside the Queue simultaneously", color = Color.Gray, fontSize = 11.sp)
                    }
                    Text("${concurrentCount.toInt()} Queue Size", color = Color(0xFFFF2E2E), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                
                Slider(
                    value = concurrentCount,
                    onValueChange = {
                        concurrentCount = it.toInt().toFloat()
                        val newConf = config.copy(maxConcurrentDownloads = it.toInt())
                        engine.setConfig(newConf)
                        onConfigChanged(newConf)
                    },
                    valueRange = 1f..10f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFF2E2E),
                        activeTrackColor = Color(0xFFFF2E2E),
                        inactiveTrackColor = Color(0xFF2E2E3E)
                    ),
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }
        }

        Text("NETWORK & RECOVERY", color = Color(0xFFFF2E2E), fontSize = 11.sp, fontWeight = FontWeight.Bold)

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF15151F)),
            border = BorderStroke(1.dp, Color(0xFF22222F)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Wi-Fi Only Mode", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Only download files when connected to a Wi-Fi network", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = wifiOnlyMode,
                        onCheckedChange = {
                            wifiOnlyMode = it
                            val newConf = config.copy(wifiOnly = it)
                            engine.setConfig(newConf)
                            onConfigChanged(newConf)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFF2E2E), checkedTrackColor = Color(0xFFFF2E2E).copy(alpha = 0.3f))
                    )
                }

                Divider(color = Color(0xFF22222F))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto Resume Support", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Automatically resume failed files on app start or recovery", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = autoResumeMode,
                        onCheckedChange = {
                            autoResumeMode = it
                            val newConf = config.copy(autoResume = it)
                            engine.setConfig(newConf)
                            onConfigChanged(newConf)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFF2E2E), checkedTrackColor = Color(0xFFFF2E2E).copy(alpha = 0.3f))
                    )
                }

                Divider(color = Color(0xFF22222F))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Smart Retry & Recovery", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Reroutes connection and restarts stream on network recovery", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = smartRetryMode,
                        onCheckedChange = { smartRetryMode = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFF2E2E), checkedTrackColor = Color(0xFFFF2E2E).copy(alpha = 0.3f))
                    )
                }

                Divider(color = Color(0xFF22222F))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Notification System Progress", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Show active progress/speeds inside device status notifications", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = notificationsMode,
                        onCheckedChange = { notificationsMode = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFF2E2E), checkedTrackColor = Color(0xFFFF2E2E).copy(alpha = 0.3f))
                    )
                }
            }
        }

        Text("STORAGE & CACHE MANAGEMENT", color = Color(0xFFFF2E2E), fontSize = 11.sp, fontWeight = FontWeight.Bold)

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF15151F)),
            border = BorderStroke(1.dp, Color(0xFF22222F)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Primary Location:", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F0F14))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "/Internal Storage/Download/SwiftBrowserDownload",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                // Wipe all logs
                                val database = com.swift.browser.downloadengine.DownloadDatabase.getDatabase(engine as? Context ?: return@launch)
                                database.downloadDao().deleteAll()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2E2E)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Clear All Lists History", color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }

        Text("DEVELOPER DIAGNOSTICS", color = Color(0xFFFF2E2E), fontSize = 11.sp, fontWeight = FontWeight.Bold)

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF15151F)),
            border = BorderStroke(1.dp, Color(0xFF22222F)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Diagnostics & Traces:", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Inspect real-time download segments, assembly tasks, and JavaScript probe events live.", color = Color.Gray, fontSize = 11.sp)
                Button(
                    onClick = onLaunchDiagnostics,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E2C)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Launch Diagnostics Console", color = Color.White, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

// REMAINING CALCULATOR HELPER
private fun calculateRemainingTime(downloaded: Long, total: Long, speedText: String): String {
    if (total <= 0 || downloaded >= total) return "Finished"
    val remainingBytes = total - downloaded
    
    val speedInBytes = parseSpeedToBytes(speedText)
    if (speedInBytes <= 0) return "Calculating time..."
    
    val seconds = remainingBytes / speedInBytes
    if (seconds <= 0) return "1s remaining"
    if (seconds >= 3600 * 24) return "> 1 day remaining"
    
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    
    return buildString {
        if (h > 0) append("${h}h ")
        if (m > 0) append("${m}m ")
        if (s > 0 || isEmpty()) append("${s}s")
        append(" remaining")
    }
}

private fun parseSpeedToBytes(speedText: String): Long {
    try {
        val text = speedText.trim().lowercase()
        if (text.contains("paused") || text.contains("pending") || text.contains("connecting")) return 0L
        
        val numberPart = text.replace(Regex("[^0-9.]"), "")
        val value = numberPart.toDoubleOrNull() ?: return 0L
        
        return when {
            text.contains("mb/s") -> (value * 1024 * 1024).toLong()
            text.contains("kb/s") -> (value * 1024).toLong()
            text.contains("gb/s") -> (value * 1024 * 1024 * 1024).toLong()
            else -> value.toLong()
        }
    } catch (e: Exception) {
        return 0L
    }
}

// METADATA FORMATTER
private fun getMetadataRow(item: DownloadItem): String {
    val downloaded = formatBytes(item.downloadedSize)
    val total = if (item.totalSize > 0) formatBytes(item.totalSize) else "Unknown"
    return "$downloaded of $total"
}

// BYTES VALUE FORMATTERS
private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.size - 1) {
        value /= 1024
        unitIndex++
    }
    return String.format("%.1f %s", value, units[unitIndex])
}

// DYNAMIC CATEGORY METRICS ICON SELECTOR
private fun getCategoryIcon(category: String): ImageVector {
    return when (category) {
        "Videos" -> Icons.Default.Movie
        "Audio" -> Icons.Default.MusicNote
        "Images" -> Icons.Default.Image
        "Archives" -> Icons.Default.Folder
        else -> Icons.Default.Description
    }
}

// NATIVE FILE SHARING INTENTS VIA PROVIDERS
private fun shareFile(context: Context, filePath: String, mimeType: String) {
    try {
        val file = File(filePath)
        if (file.exists()) {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Share Downloaded File"))
        } else {
            android.widget.Toast.makeText(context, "File does not exist", android.widget.Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Cannot share: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

// DIRECT FILE LOCATOR DIRECTORY VIEW OPENER
private fun openFileDirectory(context: Context, filePath: String) {
    try {
        val file = File(filePath)
        val dir = file.parentFile ?: return
        if (dir.exists()) {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    dir
                )
                setDataAndType(uri, "resource/folder")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } else {
            android.widget.Toast.makeText(context, "Directory does not exist", android.widget.Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        // Fallback standard file manager opener
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (ex: Exception) {
            android.widget.Toast.makeText(context, "Cannot locate folder: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}


