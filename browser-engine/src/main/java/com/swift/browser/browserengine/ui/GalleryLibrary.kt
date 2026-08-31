package com.swift.browser.browserengine.ui

import com.swift.browser.browserengine.MediaType
import com.swift.browser.browserengine.LocalMediaItem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryLibrary(
    viewModel: MediaCenterViewModel,
    viewMode: ViewMode,
    searchQuery: String,
    sortBy: SortBy,
    sortOrder: SortOrder,
    onImageSelected: (LocalMediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val permissionGranted by viewModel.galleryPermissionGranted.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val gallery by viewModel.gallery.collectAsState()

    var activeSubTab by remember { mutableStateOf("All Images") }

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
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Access Local Images",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Swift's native high-performance offline gallery scans screenshots, wallpapers, downloads, and camera directories with fluid grid resizing.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            viewModel.requestLibraryPermission(MediaType.IMAGE) {}
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Grant Gallery Access", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    } else {
        // Filters & Sorting image lists
        val filteredImages = remember(gallery, searchQuery, sortBy, sortOrder) {
            var result = gallery.filter {
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
                else -> result.sortedByDescending { it.dateAdded } // Fallback to date
            }
            result
        }

        // Sub-sections based on folder
        val displayImages = remember(filteredImages, activeSubTab) {
            if (activeSubTab == "All Images") {
                filteredImages
            } else {
                filteredImages.filter { it.folder.contains(activeSubTab, ignoreCase = true) }
            }
        }

        val folders = remember(filteredImages) {
            filteredImages.groupBy { it.folder }.keys.toList()
        }

        Column(modifier = modifier.fillMaxSize()) {
            TabBarComponent(
                tabs = listOf("All Images") + folders,
                selectedTab = activeSubTab,
                onTabSelected = { activeSubTab = it }
            )

            if (isScanning && displayImages.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (displayImages.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No images found.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            } else {
                val gridColumns = when (viewMode) {
                    ViewMode.LIST -> 1
                    ViewMode.COMPACT_GRID -> 4
                    ViewMode.LARGE_GRID -> 2
                    ViewMode.GRID -> 3
                }

                if (viewMode == ViewMode.LIST) {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(displayImages, key = { it.id }) { image ->
                            MediaItemCard(
                                item = image,
                                viewMode = viewMode,
                                onClick = { onImageSelected(image) },
                                onDelete = { viewModel.deleteItem(image) },
                                onRename = { viewModel.renameItem(image, it) },
                                onToggleFavorite = { viewModel.toggleFavorite(image) },
                                onAddToPlaylist = {}
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns),
                        contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(displayImages, key = { it.id }) { image ->
                            MediaItemCard(
                                item = image,
                                viewMode = viewMode,
                                onClick = { onImageSelected(image) },
                                onDelete = { viewModel.deleteItem(image) },
                                onRename = { viewModel.renameItem(image, it) },
                                onToggleFavorite = { viewModel.toggleFavorite(image) },
                                onAddToPlaylist = {}
                            )
                        }
                    }
                }
            }
        }
    }
}
