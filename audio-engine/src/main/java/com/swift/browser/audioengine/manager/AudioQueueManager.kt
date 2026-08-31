package com.swift.browser.audioengine.manager

import com.swift.browser.audioengine.model.AudioQueueState
import com.swift.browser.audioengine.model.AudioTrackItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

class AudioQueueManager {
    private val _queueState = MutableStateFlow(AudioQueueState())
    val queueState: StateFlow<AudioQueueState> = _queueState.asStateFlow()

    fun setQueue(tracks: List<AudioTrackItem>, startIndex: Int = 0) {
        val safeIndex = if (startIndex in tracks.indices) startIndex else if (tracks.isNotEmpty()) 0 else -1
        _queueState.value = _queueState.value.copy(
            tracks = tracks,
            currentIndex = safeIndex
        )
    }

    fun playTrack(track: AudioTrackItem, queue: List<AudioTrackItem> = emptyList()) {
        val currentQueue = if (queue.isNotEmpty()) queue else _queueState.value.tracks.ifEmpty { listOf(track) }
        val index = currentQueue.indexOfFirst { it.filePath == track.filePath || it.id == track.id }
        val safeIndex = if (index != -1) index else 0
        _queueState.value = _queueState.value.copy(
            tracks = if (index != -1) currentQueue else currentQueue + track,
            currentIndex = if (index != -1) safeIndex else currentQueue.size
        )
    }

    fun next(): AudioTrackItem? {
        val state = _queueState.value
        val tracks = state.tracks
        if (tracks.isEmpty()) return null

        if (state.repeatMode == 2) { // Repeat One
            return state.currentTrack
        }

        val nextIndex = if (state.isShuffle) {
            if (tracks.size <= 1) 0 else Random.nextInt(tracks.size)
        } else {
            if (state.currentIndex + 1 < tracks.size) {
                state.currentIndex + 1
            } else if (state.repeatMode == 1) { // Repeat All
                0
            } else {
                -1
            }
        }

        if (nextIndex in tracks.indices) {
            _queueState.value = state.copy(currentIndex = nextIndex)
            return tracks[nextIndex]
        }
        return null
    }

    fun previous(): AudioTrackItem? {
        val state = _queueState.value
        val tracks = state.tracks
        if (tracks.isEmpty()) return null

        if (state.repeatMode == 2) { // Repeat One
            return state.currentTrack
        }

        val prevIndex = if (state.isShuffle) {
            if (tracks.size <= 1) 0 else Random.nextInt(tracks.size)
        } else {
            if (state.currentIndex > 0) {
                state.currentIndex - 1
            } else if (state.repeatMode == 1 && tracks.isNotEmpty()) {
                tracks.size - 1
            } else {
                0
            }
        }

        if (prevIndex in tracks.indices) {
            _queueState.value = state.copy(currentIndex = prevIndex)
            return tracks[prevIndex]
        }
        return null
    }

    fun toggleShuffle(): Boolean {
        val newShuffle = !_queueState.value.isShuffle
        _queueState.value = _queueState.value.copy(isShuffle = newShuffle)
        return newShuffle
    }

    fun toggleRepeat(): Int {
        val nextMode = (_queueState.value.repeatMode + 1) % 3
        _queueState.value = _queueState.value.copy(repeatMode = nextMode)
        return nextMode
    }

    fun getCurrentTrack(): AudioTrackItem? = _queueState.value.currentTrack
}
