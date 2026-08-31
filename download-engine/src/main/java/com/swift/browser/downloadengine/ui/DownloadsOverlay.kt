package com.swift.browser.downloadengine.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.swift.browser.downloadengine.DownloadItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun getStorageInfo(context: Context): Pair<String, String> {
    return try {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong

        val totalBytes = totalBlocks * blockSize
        val availableBytes = availableBlocks * blockSize
        val usedBytes = totalBytes - availableBytes

        val usedStr = Formatter.formatShortFileSize(context, usedBytes)
        val totalStr = Formatter.formatShortFileSize(context, totalBytes)
        Pair(usedStr, totalStr)
    } catch (e: Exception) {
        Pair("389.99 MB", "115.87 GB")
    }
}

fun getGroupedDateString(timestamp: Long): String {
    val now = Calendar.getInstance()
    val time = Calendar.getInstance().apply { timeInMillis = timestamp }

    val isToday = now.get(Calendar.YEAR) == time.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == time.get(Calendar.DAY_OF_YEAR)

    now.add(Calendar.DAY_OF_YEAR, -1)
    val isYesterday = now.get(Calendar.YEAR) == time.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == time.get(Calendar.DAY_OF_YEAR)

    return when {
        isToday -> "Today - " + SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(timestamp))
        isYesterday -> "Yesterday - " + SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadsOverlay(
    downloads: List<DownloadItem>,
    onDismiss: () -> Unit,
    onOpenFile: (String, String, String) -> Unit,
    onDeleteDownloads: (Set<Long>) -> Unit,
    onDeleteDownload: (Long) -> Unit,
    onRenameDownloadFile: (Long, String, String) -> Boolean,
    isGlass: Boolean = false
) {
    val context = LocalContext.current
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    val storageInfo = remember { getStorageInfo(context) }

    val groupedAndSorted = remember(downloads) {
        downloads.groupBy { getGroupedDateString(it.timestamp) }
    }

    Surface(
        color = if (isGlass) Color(0xFF0B1220) else MaterialTheme.colorScheme.background,
        contentColor = if (isGlass) Color.White else MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (selectedIds.isNotEmpty()) {
                                selectedIds = emptySet()
                            } else {
                                onDismiss()
                            }
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isGlass) Color.White else LocalContentColor.current
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedIds.isNotEmpty()) "${selectedIds.size} selected" else "Downloads",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (selectedIds.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val selectedDownloads = downloads.filter { it.id in selectedIds }
                                val uris = ArrayList<Uri>()
                                selectedDownloads.forEach { dl ->
                                    val file = if (dl.filePath.isNotBlank()) {
                                        File(dl.filePath)
                                    } else {
                                        File(
                                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                            dl.title
                                        )
                                    }
                                    if (file.exists()) {
                                        try {
                                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                            uris.add(uri)
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }
                                }
                                if (uris.isNotEmpty()) {
                                    val shareIntent = Intent().apply {
                                        action = Intent.ACTION_SEND_MULTIPLE
                                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                        type = "*/*"
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share selected files"))
                                } else {
                                    Toast.makeText(context, "Cannot share: selected files missing on memory", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share selected",
                                tint = if (isGlass) Color.White else MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = {
                                onDeleteDownloads(selectedIds)
                                selectedIds = emptySet()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete selected",
                                tint = if (isGlass) Color.White else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Storage Details
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isGlass) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SdCard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Using ${storageInfo.first} of ${storageInfo.second}",
                        fontSize = 13.sp,
                        color = if (isGlass) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (downloads.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No downloads. Click download links to get files.",
                        color = if (isGlass) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    groupedAndSorted.forEach { (dateGroup, items) ->
                        item {
                            Text(
                                text = dateGroup,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isGlass) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                            )
                        }
                        items(items) { dl ->
                            DownloadItemRow(
                                download = dl,
                                isSelected = dl.id in selectedIds,
                                isInSelectionMode = selectedIds.isNotEmpty(),
                                onClick = {
                                    if (selectedIds.isNotEmpty()) {
                                        selectedIds = if (dl.id in selectedIds) {
                                            selectedIds - dl.id
                                        } else {
                                            selectedIds + dl.id
                                        }
                                    } else {
                                        val file = if (dl.filePath.isNotBlank()) {
                                            File(dl.filePath)
                                        } else {
                                            File(
                                                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                                dl.title
                                            )
                                        }
                                        if (file.exists()) {
                                            onOpenFile(file.absolutePath, dl.title, dl.mimeType)
                                        } else {
                                            Toast.makeText(context, "File does not exist or was deleted", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (selectedIds.isEmpty()) {
                                        selectedIds = setOf(dl.id)
                                    }
                                },
                                onDelete = {
                                    onDeleteDownload(dl.id)
                                },
                                onRename = { newName ->
                                    onRenameDownloadFile(dl.id, dl.title, newName)
                                },
                                isGlass = isGlass
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadItemRow(
    download: DownloadItem,
    isSelected: Boolean,
    isInSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Boolean,
    isGlass: Boolean = false
) {
    val context = LocalContext.current
    val file = remember(download.title, download.filePath) {
        if (download.filePath.isNotBlank()) {
            File(download.filePath)
        } else {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                download.title
            )
        }
    }
    val fileExists = remember(file) { file.exists() }
    val readableSize = remember(file, download.totalSize) {
        val size = if (file.exists()) file.length() else download.totalSize
        if (size > 0L) {
            Formatter.formatFileSize(context, size)
        } else {
            "Unknown size"
        }
    }

    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf(download.title) }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFileName.isNotBlank()) {
                            val success = onRename(newFileName)
                            if (success) {
                                showRenameDialog = false
                            } else {
                                Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isInSelectionMode && isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else if (isGlass) {
            Color.White.copy(alpha = 0.06f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        contentColor = if (isGlass) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isInSelectionMode) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .border(1.5.dp, (if (isGlass) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)), CircleShape)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            Icon(
                imageVector = when {
                    download.mimeType.startsWith("video/") -> Icons.Default.PlayCircle
                    download.mimeType.startsWith("image/") -> Icons.Default.Image
                    download.mimeType.startsWith("audio/") -> Icons.Default.MusicNote
                    else -> Icons.Default.InsertDriveFile
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = download.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isGlass) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (fileExists) readableSize else "File missing • $readableSize",
                    fontSize = 11.sp,
                    color = if (fileExists) {
                        (if (isGlass) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }

            if (!isInSelectionMode) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "File Options",
                            tint = if (isGlass) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                if (fileExists) {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        file
                                    )
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = download.mimeType
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share file"))
                                } else {
                                    Toast.makeText(context, "Cannot share: file missing on memory", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                newFileName = download.title
                                showRenameDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}
