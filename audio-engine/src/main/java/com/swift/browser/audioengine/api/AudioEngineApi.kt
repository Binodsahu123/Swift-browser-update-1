package com.swift.browser.audioengine.api

import android.content.Context
import com.swift.browser.audioengine.AudioPlayerEngine
import com.swift.browser.audioengine.model.AudioAlbum
import com.swift.browser.audioengine.model.AudioArtist
import com.swift.browser.audioengine.model.AudioError
import com.swift.browser.audioengine.model.AudioFolder
import com.swift.browser.audioengine.model.AudioPlaylist
import com.swift.browser.audioengine.model.AudioQueueState
import com.swift.browser.audioengine.model.AudioSessionState
import com.swift.browser.audioengine.model.AudioState
import com.swift.browser.audioengine.model.AudioTrackItem
import com.swift.browser.audioengine.model.PlaybackSource
import com.swift.browser.audioengine.model.SortOption
import kotlinx.coroutines.flow.StateFlow

interface AudioEngineApi {
    val currentTrack: StateFlow<AudioTrackItem?>
    val isPlaying: StateFlow<Boolean>
    val localLibrary: StateFlow<List<AudioTrackItem>>
    val playbackState: StateFlow<AudioState>
    val currentPositionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>
    val queueState: StateFlow<AudioQueueState>
    val sessionState: StateFlow<AudioSessionState>
    val playbackSource: StateFlow<PlaybackSource>
    val sourceTab: StateFlow<String>
    val sourceGroupTitle: StateFlow<String>
    val onlineTitle: StateFlow<String>
    val onlineUrl: StateFlow<String>
    val isOnlinePlaying: StateFlow<Boolean>
    val isOnlineFavorite: StateFlow<Boolean>
    val favorites: StateFlow<List<AudioTrackItem>>
    val playlists: StateFlow<List<AudioPlaylist>>
    val albums: StateFlow<List<AudioAlbum>>
    val artists: StateFlow<List<AudioArtist>>
    val folders: StateFlow<List<AudioFolder>>
    val currentSortOption: StateFlow<SortOption>
    val lastError: StateFlow<AudioError?>
    val sleepTimerMinutesLeft: StateFlow<Int>

    fun setSourceContext(tab: String, groupTitle: String = "")
    fun playTrack(track: AudioTrackItem, queue: List<AudioTrackItem> = emptyList(), tab: String = "Offline", groupTitle: String = "")
    fun playSingleTrack(filePath: String, title: String, artist: String? = null)
    fun play()
    fun pause()
    fun resume()
    fun togglePlayPause()
    fun stop()
    fun next()
    fun previous()
    fun seekTo(positionMs: Int)
    fun updatePosition()
    fun setQueue(tracks: List<AudioTrackItem>, startIndex: Int = 0)
    fun toggleShuffle()
    fun toggleRepeat()
    fun startSleepTimer(minutes: Int)
    fun cancelSleepTimer()

    // Local library management operations
    fun scanLocalAudio(onComplete: (List<AudioTrackItem>) -> Unit = {})
    fun searchLocalAudio(query: String): List<AudioTrackItem>
    fun deleteAudio(track: AudioTrackItem)
    fun renameAudio(track: AudioTrackItem, newName: String)
    fun toggleFavorite(track: AudioTrackItem)
    fun setSortOption(sortOption: SortOption)

    // Playlist operations
    fun createPlaylist(name: String, tracks: List<AudioTrackItem> = emptyList()): AudioPlaylist
    fun deletePlaylist(playlistId: String)
    fun addTrackToPlaylist(playlistId: String, track: AudioTrackItem)
    fun removeTrackFromPlaylist(playlistId: String, trackId: String)

    // Online Music operations
    fun playOnline(url: String, title: String)
    fun pauseOnline()
    fun toggleOnlinePlayPause()
    fun nextOnline()
    fun previousOnline()
    fun seekOnline(positionMs: Long)
    fun loadOnlineHome()
    fun searchOnline(query: String)
    fun goBackOnline(): Boolean
    fun canGoBackOnline(): Boolean
    fun reloadOnline()
    fun prewarmOnline(context: Context)
    fun toggleOnlineFavorite()

    fun release()

    companion object {
        fun getInstance(context: Context): AudioEngineApi {
            return AudioPlayerEngine.getInstance(context)
        }
    }
}
