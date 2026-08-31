package com.swift.browser.browserengine.ui

import com.swift.browser.browserengine.MediaType
import com.swift.browser.browserengine.LocalMediaItem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentLibrary(
    viewModel: MediaCenterViewModel,
    viewMode: ViewMode,
    searchQuery: String,
    sortBy: SortBy,
    sortOrder: SortOrder,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val permissionGranted by viewModel.docsPermissionGranted.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val docs by viewModel.docs.collectAsState()

    var activeSubTab by remember { mutableStateOf("All Docs") }

    // Simulation of active/historical downloads inside the Download Center
    val activeDownloads = remember {
        mutableStateListOf(
            ActiveDownloadTask("dl_task_1", "swift_v2.1_update_arm64.apk", "6.2 MB/s", 0.85f, 32, "48.5 MB"),
            ActiveDownloadTask("dl_task_2", "nature_wallpaper_uhd.png", "1.1 MB/s", 1.0f, 8, "2.4 MB"),
            ActiveDownloadTask("dl_task_3", "research_paper_quantum.pdf", "450 KB/s", 1.0f, 4, "124 KB")
        )
    }

    if (!permissionGranted) {
        Box(
            modifier = modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Access Local Documents",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Swift's file center structures documents, PDFs, ZIP archives, and downloaded APK installations with offline viewing protocols.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            viewModel.requestLibraryPermission(MediaType.DOCUMENT) {}
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Grant File Access", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    } else {
        // Documents Categorization and Scanning
        val filteredDocs = remember(docs, searchQuery, sortBy, sortOrder) {
            var result = docs.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.folder.contains(searchQuery, ignoreCase = true)
            }

            result = when (sortBy) {
                SortBy.NAME -> {
                    if (sortOrder == SortOrder.NEW_TO_OLD || sortOrder == SortOrder.DESCENDING) {
                        result.sortedByDescending { it.title.lowercase() }
                    } else {
                        result.sortedBy { it.title.lowercase() }
                    }
                }
                SortBy.SIZE -> {
                    if (sortOrder == SortOrder.NEW_TO_OLD || sortOrder == SortOrder.DESCENDING) {
                        result.sortedByDescending { it.size }
                    } else {
                        result.sortedBy { it.size }
                    }
                }
                SortBy.DATE -> {
                    if (sortOrder == SortOrder.NEW_TO_OLD || sortOrder == SortOrder.DESCENDING) {
                        result.sortedByDescending { it.dateAdded }
                    } else {
                        result.sortedBy { it.dateAdded }
                    }
                }
                else -> result
            }
            result
        }

        val displayDocs = remember(filteredDocs, activeSubTab) {
            when (activeSubTab) {
                "PDFs" -> filteredDocs.filter { it.mimeType.contains("pdf", ignoreCase = true) || it.title.endsWith(".pdf", ignoreCase = true) }
                "APKs" -> filteredDocs.filter { it.mimeType.contains("package-archive", ignoreCase = true) || it.title.endsWith(".apk", ignoreCase = true) }
                "Archives" -> filteredDocs.filter { it.mimeType.contains("zip", ignoreCase = true) || it.mimeType.contains("rar", ignoreCase = true) || it.title.endsWith(".zip", ignoreCase = true) }
                "Text/Docs" -> filteredDocs.filter { it.mimeType.contains("text", ignoreCase = true) || it.mimeType.contains("word", ignoreCase = true) || it.title.endsWith(".txt", ignoreCase = true) || it.title.endsWith(".docx", ignoreCase = true) }
                else -> filteredDocs
            }
        }

        Column(modifier = modifier.fillMaxSize()) {
            TabBarComponent(
                tabs = listOf("All Docs", "PDFs", "APKs", "Archives", "Text/Docs", "Active DLs"),
                selectedTab = activeSubTab,
                onTabSelected = { activeSubTab = it }
            )

            if (isScanning && displayDocs.isEmpty() && activeSubTab != "Active DLs") {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (activeSubTab == "Active DLs") {
                        // Live Multi-Socket Downloader Status Panel (From DownloadCenterScreen requirement)
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 100.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                ) {
                                    Icon(Icons.Default.DownloadForOffline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Segmented Multi-Channel Pipeline", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }

                            items(activeDownloads, key = { it.id }) { task ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    task.filename,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    maxLines = 1
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    "Chunks active: ${task.chunks} | Size: ${task.totalSize}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            if (task.progress < 1f) {
                                                Text(
                                                    task.speed,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontSize = 12.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = "Finished",
                                                    tint = Color(0xFF4CAF50),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))
                                        LinearProgressIndicator(
                                            progress = task.progress,
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                                            color = if (task.progress < 1.0f) MaterialTheme.colorScheme.primary else Color(0xFF4CAF50)
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (task.progress < 1.0f) {
                                                OutlinedButton(
                                                    onClick = {
                                                        val index = activeDownloads.indexOfFirst { it.id == task.id }
                                                        if (index != -1) {
                                                            activeDownloads[index] = task.copy(speed = "Paused", progress = task.progress)
                                                            Toast.makeText(context, "Paused download pipeline", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Text("Pause", fontSize = 11.sp)
                                                }
                                            } else {
                                                Button(
                                                    onClick = {
                                                        Toast.makeText(context, "Opening file: ${task.filename}", Toast.LENGTH_SHORT).show()
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Text("Open File", fontSize = 11.sp, color = Color.White)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Standard Documents List
                        if (displayDocs.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No documents found matching this filter.",
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(bottom = 100.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(displayDocs, key = { it.id }) { doc ->
                                    MediaItemCard(
                                        item = doc,
                                        viewMode = viewMode,
                                        onClick = {
                                            Toast.makeText(context, "Opening document: ${doc.title}", Toast.LENGTH_SHORT).show()
                                        },
                                        onDelete = { viewModel.deleteItem(doc) },
                                        onRename = { viewModel.renameItem(doc, it) },
                                        onToggleFavorite = { viewModel.toggleFavorite(doc) },
                                        onAddToPlaylist = {}
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

data class ActiveDownloadTask(
    val id: String,
    val filename: String,
    val speed: String,
    val progress: Float,
    val chunks: Int,
    val totalSize: String
)

