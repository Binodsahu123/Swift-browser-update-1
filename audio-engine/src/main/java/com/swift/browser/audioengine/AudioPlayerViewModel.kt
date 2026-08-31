package com.swift.browser.audioengine

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.swift.browser.audioengine.api.AudioEngineApi
import com.swift.browser.audioengine.model.AudioAlbum
import com.swift.browser.audioengine.model.AudioArtist
import com.swift.browser.audioengine.model.AudioFolder
import com.swift.browser.audioengine.model.AudioPlaylist
import com.swift.browser.audioengine.model.AudioTrackItem
import com.swift.browser.audioengine.model.SortOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioPlayerViewModel(private val context: Context) : ViewModel() {
    val audioEngine: AudioEngineApi = AudioPlayerEngine.getInstance(context)

    val currentTrack = audioEngine.currentTrack
    val isPlaying = audioEngine.isPlaying
    val currentPositionMs = audioEngine.currentPositionMs
    val durationMs = audioEngine.durationMs
    val queueState = audioEngine.queueState
    val favorites = audioEngine.favorites
    val playlists = audioEngine.playlists
    val albums = audioEngine.albums
    val artists = audioEngine.artists
    val folders = audioEngine.folders
    val currentSortOption = audioEngine.currentSortOption
    val sourceTab = audioEngine.sourceTab
    val sourceGroupTitle = audioEngine.sourceGroupTitle

    val onlineTitle = audioEngine.onlineTitle
    val onlineUrl = audioEngine.onlineUrl
    val isOnlinePlaying = audioEngine.isOnlinePlaying
    val playbackSource = audioEngine.playbackSource

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    val localTracks: StateFlow<List<AudioTrackItem>> = (audioEngine as AudioPlayerEngine).localLibrary

    init {
        scanLocalLibrary()
    }

    fun scanLocalLibrary() {
        _isScanning.value = true
        audioEngine.scanLocalAudio {
            _isScanning.value = false
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun playTrack(track: AudioTrackItem, queue: List<AudioTrackItem> = emptyList(), tab: String = "Offline", groupTitle: String = "") {
        audioEngine.playTrack(track, queue, tab, groupTitle)
    }

    fun setSourceContext(tab: String, groupTitle: String) {
        audioEngine.setSourceContext(tab, groupTitle)
    }

    fun togglePlayPause() {
        audioEngine.togglePlayPause()
    }

    fun next() {
        audioEngine.next()
    }

    fun previous() {
        audioEngine.previous()
    }

    fun seekTo(positionMs: Int) {
        audioEngine.seekTo(positionMs)
    }

    fun toggleShuffle() {
        audioEngine.toggleShuffle()
    }

    fun toggleRepeat() {
        audioEngine.toggleRepeat()
    }

    fun deleteTrack(track: AudioTrackItem) {
        audioEngine.deleteAudio(track)
    }

    fun renameTrack(track: AudioTrackItem, newName: String) {
        audioEngine.renameAudio(track, newName)
    }

    fun toggleFavorite(track: AudioTrackItem) {
        audioEngine.toggleFavorite(track)
    }

    fun setSortOption(sortOption: SortOption) {
        audioEngine.setSortOption(sortOption)
    }

    fun createPlaylist(name: String, tracks: List<AudioTrackItem> = emptyList()): AudioPlaylist {
        return audioEngine.createPlaylist(name, tracks)
    }

    fun deletePlaylist(playlistId: String) {
        audioEngine.deletePlaylist(playlistId)
    }

    fun addTrackToPlaylist(playlistId: String, track: AudioTrackItem) {
        audioEngine.addTrackToPlaylist(playlistId, track)
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        audioEngine.removeTrackFromPlaylist(playlistId, trackId)
    }

    fun searchOnline(query: String) {
        audioEngine.searchOnline(query)
    }

    fun loadOnlineHome() {
        audioEngine.loadOnlineHome()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AudioPlayerViewModel(context) as T
        }
    }
}
