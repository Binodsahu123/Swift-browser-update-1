package com.swift.browser.audioengine

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.swift.browser.audioengine.api.AudioEngineApi
import com.swift.browser.audioengine.engine.AudioSessionEngine
import com.swift.browser.audioengine.engine.LocalAudioEngine
import com.swift.browser.audioengine.manager.AudioFavoritesManager
import com.swift.browser.audioengine.manager.AudioLibraryManager
import com.swift.browser.audioengine.manager.AudioPlaylistManager
import com.swift.browser.audioengine.manager.AudioQueueManager
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
import com.swift.browser.audioengine.online.OnlineMusicWebViewManager
import com.swift.browser.audioengine.service.AudioPlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AudioPlayerEngine private constructor(private val context: Context) : AudioEngineApi {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sessionEngine = AudioSessionEngine(context)
    private val localEngine = LocalAudioEngine(context, sessionEngine)
    private val queueManager = AudioQueueManager()

    private val libraryManager = AudioLibraryManager(context)
    private val favoritesManager = AudioFavoritesManager(context)
    private val playlistManager = AudioPlaylistManager(context)

    private val _currentTrack = MutableStateFlow<AudioTrackItem?>(null)
    override val currentTrack: StateFlow<AudioTrackItem?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackState = MutableStateFlow(AudioState.IDLE)
    override val playbackState: StateFlow<AudioState> = _playbackState.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    override val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    override val queueState: StateFlow<AudioQueueState> = queueManager.queueState

    private val _sessionState = MutableStateFlow(AudioSessionState())
    override val sessionState: StateFlow<AudioSessionState> = _sessionState.asStateFlow()

    private val _playbackSource = MutableStateFlow(PlaybackSource.NONE)
    override val playbackSource: StateFlow<PlaybackSource> = _playbackSource.asStateFlow()

    private val _sourceTab = MutableStateFlow("Offline")
    override val sourceTab: StateFlow<String> = _sourceTab.asStateFlow()

    private val _sourceGroupTitle = MutableStateFlow("")
    override val sourceGroupTitle: StateFlow<String> = _sourceGroupTitle.asStateFlow()

    override val onlineTitle: StateFlow<String> = OnlineMusicWebViewManager.currentTitle
    override val onlineUrl: StateFlow<String> = OnlineMusicWebViewManager.currentUrl
    override val isOnlinePlaying: StateFlow<Boolean> = OnlineMusicWebViewManager.isPlaying
    override val isOnlineFavorite: StateFlow<Boolean> = OnlineMusicWebViewManager.isFavorite

    override val localLibrary: StateFlow<List<AudioTrackItem>> = libraryManager.allTracks
    override val favorites: StateFlow<List<AudioTrackItem>> = favoritesManager.favorites
    override val playlists: StateFlow<List<AudioPlaylist>> = playlistManager.playlists
    override val albums: StateFlow<List<AudioAlbum>> = libraryManager.albums
    override val artists: StateFlow<List<AudioArtist>> = libraryManager.artists
    override val folders: StateFlow<List<AudioFolder>> = libraryManager.folders
    override val currentSortOption: StateFlow<SortOption> = libraryManager.currentSortOption

    private val _lastError = MutableStateFlow<AudioError?>(null)
    override val lastError: StateFlow<AudioError?> = _lastError.asStateFlow()

    private val _sleepTimerMinutesLeft = MutableStateFlow(0)
    override val sleepTimerMinutesLeft: StateFlow<Int> = _sleepTimerMinutesLeft.asStateFlow()
    private var sleepTimerJob: kotlinx.coroutines.Job? = null

    init {
        localEngine.onCompletionListener = {
            val nextTrack = queueManager.next()
            if (nextTrack != null) {
                playTrack(nextTrack, queueManager.queueState.value.tracks, _sourceTab.value, _sourceGroupTitle.value)
            } else {
                _isPlaying.value = false
                _playbackState.value = AudioState.STOPPED
                sessionEngine.abandonAudioFocus()
            }
        }

        localEngine.onErrorListener = { errorMsg ->
            _lastError.value = AudioError(101, errorMsg)
            _isPlaying.value = false
            _playbackState.value = AudioState.ERROR
            sessionEngine.abandonAudioFocus()
        }

        startPositionUpdater()
        scanLocalAudio()

        engineScope.launch {
            OnlineMusicWebViewManager.isPlaying.collect { onlineIsPlaying ->
                if (onlineIsPlaying) {
                    if (_playbackSource.value == PlaybackSource.LOCAL && _isPlaying.value) {
                        localEngine.pause()
                        _isPlaying.value = false
                        _playbackState.value = AudioState.PAUSED
                    }
                    _playbackSource.value = PlaybackSource.ONLINE
                    _sourceTab.value = "Online"
                }
            }
        }
    }

    private fun startPositionUpdater() {
        engineScope.launch {
            while (true) {
                if (_isPlaying.value && _playbackSource.value == PlaybackSource.LOCAL) {
                    _currentPositionMs.value = localEngine.getCurrentPosition()
                    _durationMs.value = localEngine.getDuration()
                } else if (_playbackSource.value == PlaybackSource.ONLINE) {
                    _currentPositionMs.value = OnlineMusicWebViewManager.currentTimeMs.value
                    _durationMs.value = OnlineMusicWebViewManager.durationMs.value
                }
                delay(500)
            }
        }
    }

    override fun setSourceContext(tab: String, groupTitle: String) {
        _sourceTab.value = tab
        _sourceGroupTitle.value = groupTitle
    }

    override fun playTrack(
        track: AudioTrackItem,
        queue: List<AudioTrackItem>,
        tab: String,
        groupTitle: String
    ) {
        pauseOnlineInternal()
        _playbackSource.value = PlaybackSource.LOCAL
        setSourceContext(tab, groupTitle)

        // Request audio focus
        val granted = sessionEngine.requestAudioFocus(
            onLoss = { pause() },
            onDuck = { /* Volume ducking if needed */ },
            onGain = { resume() }
        )

        _currentTrack.value = track.copy(isFavorite = favoritesManager.isFavorite(track.id))
        if (queue.isNotEmpty()) {
            queueManager.setQueue(queue, queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
        }

        localEngine.playTrack(track) {
            _isPlaying.value = true
            _playbackState.value = AudioState.PLAYING
            _durationMs.value = localEngine.getDuration()
            _sessionState.value = _sessionState.value.copy(hasFocus = granted)
            startBackgroundService(track.title)
        }
    }

    override fun playSingleTrack(filePath: String, title: String, artist: String?) {
        val track = AudioTrackItem(
            id = filePath,
            title = title,
            artist = artist ?: "Unknown Artist",
            filePath = filePath
        )
        playTrack(track, listOf(track))
    }

    override fun play() {
        if (_playbackSource.value == PlaybackSource.LOCAL) {
            sessionEngine.requestAudioFocus(
                onLoss = { pause() },
                onDuck = {},
                onGain = { resume() }
            )
            localEngine.play()
            _isPlaying.value = true
            _playbackState.value = AudioState.PLAYING
        } else if (_playbackSource.value == PlaybackSource.ONLINE) {
            OnlineMusicWebViewManager.play()
        }
    }

    override fun pause() {
        localEngine.pause()
        _isPlaying.value = false
        _playbackState.value = AudioState.PAUSED
        OnlineMusicWebViewManager.pause()
    }

    override fun resume() {
        play()
    }

    override fun togglePlayPause() {
        if (_playbackSource.value == PlaybackSource.LOCAL) {
            if (_isPlaying.value) pause() else play()
        } else if (_playbackSource.value == PlaybackSource.ONLINE) {
            OnlineMusicWebViewManager.togglePlayPause()
        }
    }

    override fun stop() {
        localEngine.release()
        _isPlaying.value = false
        _playbackState.value = AudioState.STOPPED
        sessionEngine.abandonAudioFocus()
    }

    override fun next() {
        if (_playbackSource.value == PlaybackSource.LOCAL) {
            val nextTrack = queueManager.next()
            if (nextTrack != null) {
                playTrack(nextTrack, queueManager.queueState.value.tracks, _sourceTab.value, _sourceGroupTitle.value)
            }
        } else if (_playbackSource.value == PlaybackSource.ONLINE) {
            OnlineMusicWebViewManager.next()
        }
    }

    override fun previous() {
        if (_playbackSource.value == PlaybackSource.LOCAL) {
            val prevTrack = queueManager.previous()
            if (prevTrack != null) {
                playTrack(prevTrack, queueManager.queueState.value.tracks, _sourceTab.value, _sourceGroupTitle.value)
            }
        } else if (_playbackSource.value == PlaybackSource.ONLINE) {
            OnlineMusicWebViewManager.previous()
        }
    }

    override fun seekTo(positionMs: Int) {
        if (_playbackSource.value == PlaybackSource.LOCAL) {
            localEngine.seekTo(positionMs)
            _currentPositionMs.value = positionMs.toLong()
        } else if (_playbackSource.value == PlaybackSource.ONLINE) {
            OnlineMusicWebViewManager.seekTo(positionMs.toLong())
        }
    }

    override fun updatePosition() {
        if (_playbackSource.value == PlaybackSource.LOCAL) {
            _currentPositionMs.value = localEngine.getCurrentPosition()
        } else if (_playbackSource.value == PlaybackSource.ONLINE) {
            _currentPositionMs.value = OnlineMusicWebViewManager.currentTimeMs.value
        }
    }

    override fun setQueue(tracks: List<AudioTrackItem>, startIndex: Int) {
        queueManager.setQueue(tracks, startIndex)
    }

    override fun toggleShuffle() {
        queueManager.toggleShuffle()
    }

    override fun toggleRepeat() {
        queueManager.toggleRepeat()
    }

    override fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        _sleepTimerMinutesLeft.value = minutes
        sleepTimerJob = engineScope.launch {
            var remaining = minutes
            while (remaining > 0) {
                delay(60000L) // Wait 1 minute
                remaining--
                _sleepTimerMinutesLeft.value = remaining
                if (remaining == 0) {
                    pause()
                }
            }
        }
    }

    override fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerMinutesLeft.value = 0
    }

    override fun scanLocalAudio(onComplete: (List<AudioTrackItem>) -> Unit) {
        engineScope.launch {
            val list = libraryManager.scanLocalLibrary()
            favoritesManager.updateFavoritesWithLibrary(list)
            withContext(Dispatchers.Main) {
                onComplete(list)
            }
        }
    }

    override fun searchLocalAudio(query: String): List<AudioTrackItem> {
        val tracks = libraryManager.allTracks.value
        if (query.isBlank()) return tracks
        return tracks.filter {
            it.title.contains(query, ignoreCase = true) ||
                    (it.artist?.contains(query, ignoreCase = true) == true) ||
                    (it.album?.contains(query, ignoreCase = true) == true)
        }
    }

    override fun deleteAudio(track: AudioTrackItem) {
        engineScope.launch {
            libraryManager.deleteTrack(track)
            if (_currentTrack.value?.id == track.id) {
                stop()
                _currentTrack.value = null
            }
        }
    }

    override fun renameAudio(track: AudioTrackItem, newName: String) {
        engineScope.launch {
            libraryManager.renameTrack(track, newName)
            if (_currentTrack.value?.id == track.id) {
                _currentTrack.value = _currentTrack.value?.copy(title = newName)
            }
        }
    }

    override fun toggleFavorite(track: AudioTrackItem) {
        favoritesManager.toggleFavorite(track)
        val isFav = favoritesManager.isFavorite(track.id)
        if (_currentTrack.value?.id == track.id) {
            _currentTrack.value = _currentTrack.value?.copy(isFavorite = isFav)
        }
    }

    override fun setSortOption(sortOption: SortOption) {
        libraryManager.setSortOption(sortOption)
    }

    override fun createPlaylist(name: String, tracks: List<AudioTrackItem>): AudioPlaylist {
        return playlistManager.createPlaylist(name, tracks)
    }

    override fun deletePlaylist(playlistId: String) {
        playlistManager.deletePlaylist(playlistId)
    }

    override fun addTrackToPlaylist(playlistId: String, track: AudioTrackItem) {
        playlistManager.addTrackToPlaylist(playlistId, track)
    }

    override fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        playlistManager.removeTrackFromPlaylist(playlistId, trackId)
    }

    override fun playOnline(url: String, title: String) {
        stop()
        _playbackSource.value = PlaybackSource.ONLINE
        _sourceTab.value = "Online"
        OnlineMusicWebViewManager.updateMediaState(context, true, title)
        startBackgroundService(title)
    }

    override fun pauseOnline() {
        OnlineMusicWebViewManager.pause()
    }

    override fun toggleOnlinePlayPause() {
        OnlineMusicWebViewManager.togglePlayPause()
    }

    override fun nextOnline() {
        OnlineMusicWebViewManager.next()
    }

    override fun previousOnline() {
        OnlineMusicWebViewManager.previous()
    }

    override fun seekOnline(positionMs: Long) {
        OnlineMusicWebViewManager.seekTo(positionMs)
    }

    override fun loadOnlineHome() {
        _playbackSource.value = PlaybackSource.ONLINE
        _sourceTab.value = "Online"
        OnlineMusicWebViewManager.loadHome(context)
    }

    override fun searchOnline(query: String) {
        _playbackSource.value = PlaybackSource.ONLINE
        _sourceTab.value = "Online"
        OnlineMusicWebViewManager.search(query, context)
    }

    override fun goBackOnline(): Boolean {
        return OnlineMusicWebViewManager.goBack()
    }

    override fun canGoBackOnline(): Boolean {
        return OnlineMusicWebViewManager.canGoBack()
    }

    override fun reloadOnline() {
        OnlineMusicWebViewManager.reload()
    }

    override fun prewarmOnline(context: Context) {
        OnlineMusicWebViewManager.prewarm(context)
    }

    override fun toggleOnlineFavorite() {
        OnlineMusicWebViewManager.toggleFavorite(context)
    }

    private fun pauseOnlineInternal() {
        OnlineMusicWebViewManager.pause()
    }

    override fun release() {
        localEngine.release()
        sessionEngine.abandonAudioFocus()
        _isPlaying.value = false
        _playbackState.value = AudioState.STOPPED
        _playbackSource.value = PlaybackSource.NONE
    }

    private fun startBackgroundService(title: String) {
        try {
            val intent = Intent(context, AudioPlaybackService::class.java).apply {
                putExtra("TITLE", title)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e("AudioPlayerEngine", "Could not start AudioPlaybackService", e)
        }
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: AudioPlayerEngine? = null

        fun getInstance(context: Context): AudioPlayerEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AudioPlayerEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
