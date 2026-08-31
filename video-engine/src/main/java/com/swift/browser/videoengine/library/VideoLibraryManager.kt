package com.swift.browser.videoengine.library

import android.content.Context
import com.swift.browser.videoengine.history.WatchHistoryManager
import com.swift.browser.videoengine.model.MediaFolder
import com.swift.browser.videoengine.model.VideoItem
import com.swift.browser.videoengine.playlist.VideoPlaylistManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class VideoSortBy {
    DATE, NAME, SIZE, DURATION
}

enum class VideoSortOrder {
    ASCENDING, DESCENDING
}

enum class VideoViewMode {
    LIST, GRID, COMPACT_GRID, LARGE_GRID
}

class VideoLibraryManager(private val context: Context) {
    private val repository = VideoRepository(context)
    private val prefs = context.applicationContext.getSharedPreferences("video_player_prefs", Context.MODE_PRIVATE)

    val historyManager = WatchHistoryManager(context)
    val playlistManager = VideoPlaylistManager(context)

    private val _allVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val allVideos: StateFlow<List<VideoItem>> = _allVideos.asStateFlow()

    private val _folders = MutableStateFlow<List<MediaFolder>>(emptyList())
    val folders: StateFlow<List<MediaFolder>> = _folders.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    val searchQuery = MutableStateFlow("")

    private val _viewMode = MutableStateFlow(
        try { VideoViewMode.valueOf(prefs.getString("view_mode", "LIST") ?: "LIST") }
        catch (e: Exception) { VideoViewMode.LIST }
    )
    val viewMode: StateFlow<VideoViewMode> = _viewMode.asStateFlow()

    private val _sortBy = MutableStateFlow(
        try { VideoSortBy.valueOf(prefs.getString("sort_by", "DATE") ?: "DATE") }
        catch (e: Exception) { VideoSortBy.DATE }
    )
    val sortBy: StateFlow<VideoSortBy> = _sortBy.asStateFlow()

    private val _sortOrder = MutableStateFlow(VideoSortOrder.DESCENDING)
    val sortOrder: StateFlow<VideoSortOrder> = _sortOrder.asStateFlow()

    fun setViewMode(mode: VideoViewMode) {
        _viewMode.value = mode
        prefs.edit().putString("view_mode", mode.name).apply()
    }

    fun setSortBy(sort: VideoSortBy) {
        _sortBy.value = sort
        prefs.edit().putString("sort_by", sort.name).apply()
    }

    fun setSortOrder(order: VideoSortOrder) {
        _sortOrder.value = order
    }

    fun scanVideos(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            _isScanning.value = true
            repository.scanVideosFlow().collect { videos ->
                _allVideos.value = videos
                historyManager.loadHistory(videos)

                val folderMap = videos.groupBy { it.folder }
                val folderList = folderMap.map { (folderName, items) ->
                    MediaFolder(
                        name = folderName,
                        itemCount = items.size,
                        previewPath = items.firstOrNull()?.thumbnailUri
                    )
                }
                _folders.value = folderList
                _isScanning.value = false
            }
        }
    }

    fun renameVideo(scope: CoroutineScope, item: VideoItem, newName: String) {
        scope.launch(Dispatchers.IO) {
            if (repository.renameVideo(item, newName)) {
                scanVideos(scope)
            }
        }
    }

    fun deleteVideo(scope: CoroutineScope, item: VideoItem) {
        scope.launch(Dispatchers.IO) {
            if (repository.deleteVideo(item)) {
                historyManager.removeFromHistory(item)
                playlistManager.removeVideoFromAllPlaylists(item.id)
                scanVideos(scope)
            }
        }
    }

    fun getFilteredAndSortedVideos(): List<VideoItem> {
        val query = searchQuery.value.trim()
        val currentVideos = _allVideos.value

        var filtered = if (query.isEmpty()) currentVideos else {
            currentVideos.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.folder.contains(query, ignoreCase = true)
            }
        }

        val desc = _sortOrder.value == VideoSortOrder.DESCENDING
        filtered = when (_sortBy.value) {
            VideoSortBy.NAME -> if (desc) filtered.sortedByDescending { it.title.lowercase() } else filtered.sortedBy { it.title.lowercase() }
            VideoSortBy.SIZE -> if (desc) filtered.sortedByDescending { it.size } else filtered.sortedBy { it.size }
            VideoSortBy.DURATION -> if (desc) filtered.sortedByDescending { it.duration ?: 0L } else filtered.sortedBy { it.duration ?: 0L }
            VideoSortBy.DATE -> if (desc) filtered.sortedByDescending { it.dateAdded } else filtered.sortedBy { it.dateAdded }
        }

        return filtered
    }
}
