package com.swift.browser.historyengine.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.historyengine.HistoryItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryOverlay(
    history: List<HistoryItem>,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
    onDelete: (Int) -> Unit,
    onClearAll: () -> Unit,
    onClearBrowsingData: ((Boolean, Boolean, Boolean, Int) -> Unit)? = null,
    isGlass: Boolean = false,
    initialSearchQuery: String = ""
) {
    var searchQuery by remember(initialSearchQuery) { mutableStateOf(initialSearchQuery) }
    val selectedIds = remember { mutableStateListOf<Int>() }
    var showLocalClearDataDialog by remember { mutableStateOf(false) }

    val filteredHistory = remember(history, searchQuery) {
        history.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    it.url.contains(searchQuery, ignoreCase = true)
        }
    }

    val groupedByDate = remember(filteredHistory) {
        filteredHistory.groupBy { timestamp ->
            val isToday = android.text.format.DateUtils.isToday(timestamp.timestamp)
            if (isToday) {
                "Today"
            } else {
                val isYesterday = android.text.format.DateUtils.isToday(timestamp.timestamp + 24 * 3600 * 1000L)
                if (isYesterday) {
                    "Yesterday"
                } else {
                    val format = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
                    format.format(Date(timestamp.timestamp))
                }
            }
        }
    }

    Surface(
        color = if (isGlass) Color(0xFF0B1220) else MaterialTheme.colorScheme.background,
        contentColor = if (isGlass) Color.White else MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (selectedIds.isNotEmpty()) {
                                selectedIds.clear()
                            } else {
                                onDismiss()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isGlass) Color.White else LocalContentColor.current
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedIds.isNotEmpty()) "${selectedIds.size} Selected" else "History",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row {
                    if (selectedIds.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                selectedIds.forEach { id -> onDelete(id) }
                                selectedIds.clear()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Selected",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        if (history.isNotEmpty()) {
                            TextButton(onClick = onClearAll) {
                                Text("Clear All", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                placeholder = { Text("Search history", fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    focusedContainerColor = if (isGlass) Color.White.copy(alpha = 0.05f) else Color.Transparent,
                    unfocusedContainerColor = if (isGlass) Color.White.copy(alpha = 0.03f) else Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (onClearBrowsingData != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .clickable { showLocalClearDataDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Clear browsing data...",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            if (filteredHistory.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (searchQuery.isEmpty()) "No history recorded yet." else "No matching results found.",
                        color = if (isGlass) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    groupedByDate.forEach { (dateStr, items) ->
                        item {
                            Text(
                                text = dateStr,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(items) { hm ->
                            val isSelected = selectedIds.contains(hm.id)
                            HistoryItemRow(
                                historyItem = hm,
                                onClick = {
                                    if (selectedIds.isNotEmpty()) {
                                        if (isSelected) selectedIds.remove(hm.id) else selectedIds.add(hm.id)
                                    } else {
                                        onNavigate(hm.url)
                                    }
                                },
                                onLongClick = {
                                    if (!selectedIds.contains(hm.id)) {
                                        selectedIds.add(hm.id)
                                    }
                                },
                                onDelete = { onDelete(hm.id) },
                                isSelectionMode = selectedIds.isNotEmpty(),
                                isSelected = isSelected,
                                isGlass = isGlass
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLocalClearDataDialog && onClearBrowsingData != null) {
        HistoryClearDataDialog(
            onClear = { hist, cook, cach, rangeIndex ->
                showLocalClearDataDialog = false
                onClearBrowsingData(hist, cook, cach, rangeIndex)
            },
            onDismiss = { showLocalClearDataDialog = false }
        )
    }
}

@Composable
fun HistoryClearDataDialog(
    onClear: (clearHistory: Boolean, clearCookies: Boolean, clearCache: Boolean, timeRangeIndex: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var clearHistory by remember { mutableStateOf(true) }
    var clearCookies by remember { mutableStateOf(true) }
    var clearCache by remember { mutableStateOf(true) }
    var selectedRangeIndex by remember { mutableStateOf(0) }
    val ranges = listOf("Last Hour", "Last 24 Hours", "Last 7 Days", "Last 4 Weeks", "All Time")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear Browsing Data") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Time Range", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                ranges.forEachIndexed { index, range ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { selectedRangeIndex = index }
                    ) {
                        RadioButton(
                            selected = (selectedRangeIndex == index),
                            onClick = { selectedRangeIndex = index }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(range, fontSize = 13.sp)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { clearHistory = !clearHistory }
                ) {
                    Checkbox(checked = clearHistory, onCheckedChange = { clearHistory = it })
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Browsing History", fontSize = 13.sp)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { clearCookies = !clearCookies }
                ) {
                    Checkbox(checked = clearCookies, onCheckedChange = { clearCookies = it })
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cookies & Site Data", fontSize = 13.sp)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { clearCache = !clearCache }
                ) {
                    Checkbox(checked = clearCache, onCheckedChange = { clearCache = it })
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cached Images & Files", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onClear(clearHistory, clearCookies, clearCache, selectedRangeIndex) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Clear Data")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryItemRow(
    historyItem: HistoryItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    isGlass: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            } else if (isGlass) {
                Color.White.copy(alpha = 0.05f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        ),
        border = if (isSelected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else if (isGlass) {
            BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = if (isGlass) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.padding(end = 12.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = historyItem.title.ifBlank { "Visited Site" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isGlass) Color.White else Color.Unspecified
                )
                Text(
                    text = historyItem.url,
                    fontSize = 11.sp,
                    color = if (isGlass) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!isSelectionMode) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete history item",
                        tint = if (isGlass) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
