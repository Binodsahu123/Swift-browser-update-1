package com.swift.browser.browserengine.ui
import com.swift.browser.permissionengine.AndroidRuntimePermissionManager
import com.swift.browser.extensionengine.PermissionManager
import com.swift.browser.browserengine.MediaType
import com.swift.browser.browserengine.LocalMediaItem


import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class SortBy {
    DATE, NAME, SIZE, LENGTH
}

enum class SortOrder {
    NEW_TO_OLD, OLD_TO_NEW, ASCENDING, DESCENDING
}

enum class ViewMode {
    GRID, LIST, COMPACT_GRID, LARGE_GRID
}

class MediaCenterViewModel(private val context: Context) : ViewModel() {
     
    private val audioEngine = com.swift.browser.audioengine.api.AudioEngineApi.getInstance(context)
    private val videoApi = com.swift.browser.videoengine.api.VideoEngineApi

    // --- State: Media Playback Properties (Unified from old MediaCenterScreen) ---
    var playbackSpeed = MutableStateFlow(1.0f)
    var isPipEnabled = MutableStateFlow(false)
    var brightness = MutableStateFlow(0.8f)
    var volume = MutableStateFlow(0.6f)

    // --- State: Loading & Permissions ---
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _videoPermissionGranted = MutableStateFlow(false)
    val videoPermissionGranted: StateFlow<Boolean> = _videoPermissionGranted.asStateFlow()

    private val _galleryPermissionGranted = MutableStateFlow(false)
    val galleryPermissionGranted: StateFlow<Boolean> = _galleryPermissionGranted.asStateFlow()

    private val _docsPermissionGranted = MutableStateFlow(false)
    val docsPermissionGranted: StateFlow<Boolean> = _docsPermissionGranted.asStateFlow()

    // --- State: Raw Media Collections Delegated to VideoEngineApi ---
    val videoItems: StateFlow<List<com.swift.browser.videoengine.model.VideoItem>> = videoApi.getAllVideos(context)
    val videoPlaylistsState: StateFlow<List<com.swift.browser.videoengine.model.VideoPlaylist>> = videoApi.getPlaylists(context)

    private val _gallery = MutableStateFlow<List<LocalMediaItem>>(emptyList())
    val gallery: StateFlow<List<LocalMediaItem>> = _gallery.asStateFlow()

    private val _docs = MutableStateFlow<List<LocalMediaItem>>(emptyList())
    val docs: StateFlow<List<LocalMediaItem>> = _docs.asStateFlow()

    // --- State: Video Player Integration Delegated to VideoEngineApi ---
    val currentPlayingVideoItem: StateFlow<com.swift.browser.videoengine.model.VideoItem?> = 
        videoApi.getEngine(context).currentVideo

    // --- State: Active Filters, Layouts & Sorting ---
    val searchQuery = MutableStateFlow("")
    val selectedSortBy = MutableStateFlow(SortBy.DATE)
    val selectedSortOrder = MutableStateFlow(SortOrder.NEW_TO_OLD)
    val currentViewMode = MutableStateFlow(ViewMode.GRID)

    init {
        checkPermissions()
        videoApi.scanVideos(context, viewModelScope)
    }

    fun checkPermissions() {
        _videoPermissionGranted.value = checkPermission(MediaType.VIDEO)
        _galleryPermissionGranted.value = checkPermission(MediaType.IMAGE)
        _docsPermissionGranted.value = checkPermission(MediaType.DOCUMENT)

        // Trigger loading of whatever we can
        triggerRefresh()
    }

    private fun checkPermission(type: MediaType): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when (type) {
                MediaType.VIDEO -> com.swift.browser.permissionengine.AndroidRuntimePermissionManager.hasPermission(context, android.Manifest.permission.READ_MEDIA_VIDEO)
                MediaType.IMAGE -> com.swift.browser.permissionengine.AndroidRuntimePermissionManager.hasPermission(context, android.Manifest.permission.READ_MEDIA_IMAGES)
                MediaType.DOCUMENT -> com.swift.browser.permissionengine.AndroidRuntimePermissionManager.hasPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE)
                else -> false
            }
        } else {
            com.swift.browser.permissionengine.AndroidRuntimePermissionManager.hasPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun requestLibraryPermission(type: MediaType, onComplete: (Boolean) -> Unit) {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when (type) {
                MediaType.VIDEO -> listOf(android.Manifest.permission.READ_MEDIA_VIDEO)
                MediaType.IMAGE -> listOf(android.Manifest.permission.READ_MEDIA_IMAGES)
                MediaType.DOCUMENT -> listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                else -> listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        } else {
            listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val missing = permissions.filter { !com.swift.browser.permissionengine.AndroidRuntimePermissionManager.hasPermission(context, it) }
        if (missing.isEmpty()) {
            when (type) {
                MediaType.VIDEO -> {
                    _videoPermissionGranted.value = true
                    videoApi.scanVideos(context, viewModelScope)
                }
                MediaType.IMAGE -> {
                    _galleryPermissionGranted.value = true
                    scanMediaType(type)
                }
                MediaType.DOCUMENT -> {
                    _docsPermissionGranted.value = true
                    scanMediaType(type)
                }
                else -> {}
            }
            onComplete(true)
            return
        }

        com.swift.browser.permissionengine.AndroidRuntimePermissionManager.requestAndroidPermissions(
            context = context,
            requestId = "media_center_" + System.currentTimeMillis(),
            permissions = missing
        ) { result ->
            val granted = result.granted || missing.all { perm -> result.individuallyGrantedPermissions[perm] == true }
            when (type) {
                MediaType.VIDEO -> {
                    _videoPermissionGranted.value = granted
                    if (granted) videoApi.scanVideos(context, viewModelScope)
                }
                MediaType.IMAGE -> {
                    _galleryPermissionGranted.value = granted
                    if (granted) scanMediaType(type)
                }
                MediaType.DOCUMENT -> {
                    _docsPermissionGranted.value = granted
                    if (granted) scanMediaType(type)
                }
                else -> {}
            }
            onComplete(granted)
        }
    }

    fun triggerRefresh() {
        viewModelScope.launch {
            _isScanning.value = true
            videoApi.scanVideos(context, viewModelScope)
            scanMediaType(MediaType.IMAGE)
            scanMediaType(MediaType.DOCUMENT)
            _isScanning.value = false
        }
    }

    private fun scanMediaType(type: MediaType) {
        viewModelScope.launch {
            if (type == MediaType.VIDEO) return@launch // Video scanning delegated to VideoEngineApi
            val items = MediaScanner.scanMedia(context, type)
            when (type) {
                MediaType.IMAGE -> _gallery.value = items
                MediaType.DOCUMENT -> _docs.value = items
                else -> {}
            }
        }
    }

    // --- Media Action Operations ---
    fun deleteItem(item: LocalMediaItem) {
        viewModelScope.launch {
            if (item.type == MediaType.VIDEO) {
                videoApi.deleteVideo(context, viewModelScope, item.toVideoItem())
                Toast.makeText(context, "Deleted video: ${item.title}", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val file = File(item.path)
            try {
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                // Ignore
            }

            when (item.type) {
                MediaType.IMAGE -> _gallery.value = _gallery.value.filter { it.id != item.id }
                MediaType.DOCUMENT -> _docs.value = _docs.value.filter { it.id != item.id }
                else -> {}
            }

            com.swift.browser.networkstatsengine.TraceRepository.addTrace(
                com.swift.browser.networkstatsengine.MediaTraceModel(
                    message = "Successfully deleted media file: ${item.title} from path ${item.path}",
                    url = item.path,
                    mimeType = item.mimeType,
                    quality = "Deleted"
                )
            )

            Toast.makeText(context, "Deleted: ${item.title}", Toast.LENGTH_SHORT).show()
        }
    }

    fun renameItem(item: LocalMediaItem, newName: String) {
        viewModelScope.launch {
            if (newName.isBlank()) return@launch

            if (item.type == MediaType.VIDEO) {
                videoApi.renameVideo(context, viewModelScope, item.toVideoItem(), newName)
                Toast.makeText(context, "Renamed video to $newName", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val extension = if (item.title.contains(".")) item.title.substringAfterLast(".") else ""
            val cleanNewName = if (extension.isNotEmpty() && !newName.endsWith(".$extension")) "$newName.$extension" else newName

            val mapper = { currentItem: LocalMediaItem ->
                if (currentItem.id == item.id) {
                    currentItem.copy(title = cleanNewName)
                } else currentItem
            }

            when (item.type) {
                MediaType.IMAGE -> _gallery.value = _gallery.value.map(mapper)
                MediaType.DOCUMENT -> _docs.value = _docs.value.map(mapper)
                else -> {}
            }

            com.swift.browser.networkstatsengine.TraceRepository.addTrace(
                com.swift.browser.networkstatsengine.MediaTraceModel(
                    message = "Renamed media file from ${item.title} to $cleanNewName",
                    url = item.path,
                    mimeType = item.mimeType,
                    quality = "Renamed"
                )
            )

            Toast.makeText(context, "Renamed to $cleanNewName", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Favorite Management ---
    fun toggleFavorite(item: LocalMediaItem) {
        val mapper = { currentItem: LocalMediaItem ->
            if (currentItem.id == item.id) {
                currentItem.copy(isFavorite = !currentItem.isFavorite)
            } else currentItem
        }

        when (item.type) {
            MediaType.IMAGE -> _gallery.value = _gallery.value.map(mapper)
            MediaType.DOCUMENT -> _docs.value = _docs.value.map(mapper)
            else -> {}
        }
    }

    // --- Playlists Management Delegated to VideoEngineApi ---
    fun createPlaylist(name: String, type: MediaType) {
        if (name.isBlank()) return
        if (type == MediaType.VIDEO) {
            videoApi.createPlaylist(context, name)
            Toast.makeText(context, "Video playlist '$name' created", Toast.LENGTH_SHORT).show()
        }
    }

    fun addItemToPlaylist(playlistId: String, item: LocalMediaItem) {
        if (item.type == MediaType.VIDEO) {
            videoApi.addToPlaylist(context, playlistId, item.toVideoItem())
            Toast.makeText(context, "Added video to playlist", Toast.LENGTH_SHORT).show()
        }
    }

    fun removeItemFromPlaylist(playlistId: String, itemId: String, type: MediaType) {
        if (type == MediaType.VIDEO) {
            videoApi.removeFromPlaylist(context, playlistId, itemId)
        }
    }

    // --- Video Player Controller Delegated to VideoEngineApi ---
    fun selectVideo(video: LocalMediaItem?) {
        if (video != null) {
            videoApi.play(context, video.toVideoItem())
            val intent = android.content.Intent(context, com.swift.browser.videoengine.ui.VideoPlayerActivity::class.java).apply {
                putExtra("EXTRA_VIDEO_PATH", video.path)
                putExtra("EXTRA_VIDEO_TITLE", video.title)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private fun LocalMediaItem.toVideoItem(): com.swift.browser.videoengine.model.VideoItem {
        return com.swift.browser.videoengine.model.VideoItem(
            id = id,
            title = title,
            path = path,
            folder = folder,
            size = size,
            mimeType = mimeType,
            dateAdded = dateAdded,
            duration = duration,
            width = width,
            height = height,
            thumbnailUri = thumbnailUri
        )
    }
}
