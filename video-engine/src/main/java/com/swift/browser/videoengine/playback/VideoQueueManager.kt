package com.swift.browser.videoengine.playback

import com.swift.browser.videoengine.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VideoQueueManager {
    private val _queue = MutableStateFlow<List<VideoItem>>(emptyList())
    val queue: StateFlow<List<VideoItem>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    val currentVideo: VideoItem?
        get() {
            val idx = _currentIndex.value
            val q = _queue.value
            return if (idx in q.indices) q[idx] else null
        }

    fun setQueue(videos: List<VideoItem>, initialIndex: Int = 0) {
        _queue.value = videos
        _currentIndex.value = if (videos.isNotEmpty()) initialIndex.coerceIn(0, videos.lastIndex) else -1
    }

    fun playAt(index: Int): VideoItem? {
        val q = _queue.value
        if (index in q.indices) {
            _currentIndex.value = index
            return q[index]
        }
        return null
    }

    fun playVideo(video: VideoItem) {
        val q = _queue.value
        val existingIndex = q.indexOfFirst { it.id == video.id || it.path == video.path }
        if (existingIndex != -1) {
            _currentIndex.value = existingIndex
        } else {
            val updated = q.toMutableList().apply { add(video) }
            _queue.value = updated
            _currentIndex.value = updated.lastIndex
        }
    }

    fun next(): VideoItem? {
        val q = _queue.value
        if (q.isEmpty()) return null
        val nextIdx = (_currentIndex.value + 1) % q.size
        _currentIndex.value = nextIdx
        return q[nextIdx]
    }

    fun previous(): VideoItem? {
        val q = _queue.value
        if (q.isEmpty()) return null
        val prevIdx = if (_currentIndex.value - 1 < 0) q.lastIndex else _currentIndex.value - 1
        _currentIndex.value = prevIdx
        return q[prevIdx]
    }

    fun addToQueue(video: VideoItem) {
        val updated = _queue.value.toMutableList().apply { add(video) }
        _queue.value = updated
        if (_currentIndex.value == -1) {
            _currentIndex.value = 0
        }
    }

    fun removeFromQueue(video: VideoItem) {
        val q = _queue.value.toMutableList()
        val index = q.indexOfFirst { it.id == video.id }
        if (index != -1) {
            q.removeAt(index)
            _queue.value = q
            if (_currentIndex.value >= q.size) {
                _currentIndex.value = (q.size - 1).coerceAtLeast(-1)
            }
        }
    }

    fun reorderQueue(newQueue: List<VideoItem>) {
        val current = currentVideo
        _queue.value = newQueue
        if (current != null) {
            val newIdx = newQueue.indexOfFirst { it.id == current.id }
            _currentIndex.value = if (newIdx != -1) newIdx else 0
        }
    }
}
