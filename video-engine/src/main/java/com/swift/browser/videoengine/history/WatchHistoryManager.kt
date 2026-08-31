package com.swift.browser.videoengine.history

import android.content.Context
import com.swift.browser.videoengine.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WatchHistoryManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("video_player_prefs", Context.MODE_PRIVATE)

    private val _watchHistory = MutableStateFlow<List<VideoItem>>(emptyList())
    val watchHistory: StateFlow<List<VideoItem>> = _watchHistory.asStateFlow()

    fun loadHistory(allVideos: List<VideoItem>) {
        val historySet = prefs.getStringSet("history_paths", emptySet()) ?: emptySet()
        if (historySet.isNotEmpty()) {
            _watchHistory.value = historySet.mapNotNull { key -> 
                allVideos.find { it.path == key || it.id == key } 
            }
        }
    }

    fun addToHistory(video: VideoItem) {
        val current = _watchHistory.value.filter { it.path != video.path && it.id != video.id }.toMutableList()
        current.add(0, video)
        _watchHistory.value = current

        val historyPaths = current.map { if (it.id.isNotEmpty()) it.id else it.path }.toSet()
        prefs.edit().putStringSet("history_paths", historyPaths).apply()
    }

    fun removeFromHistory(video: VideoItem) {
        val current = _watchHistory.value.filter { it.path != video.path && it.id != video.id }
        _watchHistory.value = current
        val historyPaths = current.map { if (it.id.isNotEmpty()) it.id else it.path }.toSet()
        prefs.edit().putStringSet("history_paths", historyPaths).apply()
    }

    fun clearHistory() {
        _watchHistory.value = emptyList()
        prefs.edit().remove("history_paths").apply()
    }

    fun getWatchHistorySizeFormatted(): String {
        val bytes = _watchHistory.value.sumOf { it.size }
        if (bytes <= 0L) return "0 MB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            else -> String.format("%.0f KB", kb)
        }
    }
}
