package com.swift.browser.audioengine.manager

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import com.swift.browser.audioengine.model.AudioAlbum
import com.swift.browser.audioengine.model.AudioArtist
import com.swift.browser.audioengine.model.AudioFolder
import com.swift.browser.audioengine.model.AudioTrackItem
import com.swift.browser.audioengine.model.SortOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class AudioLibraryManager(private val context: Context) {
    private val sortManager = AudioSortManager()

    private val _allTracks = MutableStateFlow<List<AudioTrackItem>>(emptyList())
    val allTracks: StateFlow<List<AudioTrackItem>> = _allTracks.asStateFlow()

    private val _albums = MutableStateFlow<List<AudioAlbum>>(emptyList())
    val albums: StateFlow<List<AudioAlbum>> = _albums.asStateFlow()

    private val _artists = MutableStateFlow<List<AudioArtist>>(emptyList())
    val artists: StateFlow<List<AudioArtist>> = _artists.asStateFlow()

    private val _folders = MutableStateFlow<List<AudioFolder>>(emptyList())
    val folders: StateFlow<List<AudioFolder>> = _folders.asStateFlow()

    private val _currentSortOption = MutableStateFlow(SortOption.NAME_A_TO_Z)
    val currentSortOption: StateFlow<SortOption> = _currentSortOption.asStateFlow()

    suspend fun scanLocalLibrary(): List<AudioTrackItem> = withContext(Dispatchers.IO) {
        val audioList = com.swift.browser.audioengine.scanner.AudioScanner.scanAudio(context)
        val sorted = sortManager.sortTracks(audioList, _currentSortOption.value)
        _allTracks.value = sorted
        rebuildGroupings(sorted)
        sorted
    }

    fun setSortOption(option: SortOption) {
        _currentSortOption.value = option
        val sorted = sortManager.sortTracks(_allTracks.value, option)
        _allTracks.value = sorted
    }

    private fun rebuildGroupings(tracks: List<AudioTrackItem>) {
        // Albums
        val albumGroup = tracks.groupBy { it.album ?: "Unknown Album" }
            .map { (name, list) ->
                AudioAlbum(
                    name = name,
                    artist = list.firstOrNull()?.artist,
                    trackCount = list.size,
                    artworkUri = list.firstOrNull()?.artworkUri
                )
            }
        _albums.value = albumGroup

        // Artists
        val artistGroup = tracks.groupBy { it.artist ?: "Unknown Artist" }
            .map { (name, list) ->
                val albumCount = list.mapNotNull { it.album }.distinct().size
                AudioArtist(
                    name = name,
                    trackCount = list.size,
                    albumCount = albumCount
                )
            }
        _artists.value = artistGroup

        // Folders
        val folderGroup = tracks.groupBy { it.folderPath.ifBlank { "Music" } }
            .map { (name, list) ->
                AudioFolder(
                    name = name,
                    path = list.firstOrNull()?.filePath?.substringBeforeLast("/") ?: "",
                    trackCount = list.size
                )
            }
        _folders.value = folderGroup
    }

    suspend fun deleteTrack(track: AudioTrackItem): Boolean = withContext(Dispatchers.IO) {
        var deleted = false
        try {
            val file = File(track.filePath)
            if (file.exists()) {
                deleted = file.delete()
            }
            try {
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track.id.toLongOrNull() ?: 0L)
                context.contentResolver.delete(uri, null, null)
                deleted = true
            } catch (e: Exception) {
                // Ignore fallback
            }
        } catch (e: Exception) {
            Log.e("AudioLibraryManager", "Error deleting audio file", e)
        }

        val newList = _allTracks.value.filter { it.id != track.id }
        _allTracks.value = newList
        rebuildGroupings(newList)
        deleted
    }

    suspend fun renameTrack(track: AudioTrackItem, newName: String): Boolean = withContext(Dispatchers.IO) {
        if (newName.isBlank()) return@withContext false
        try {
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track.id.toLongOrNull() ?: 0L)
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, newName)
            }
            context.contentResolver.update(uri, values, null, null)
        } catch (e: Exception) {
            Log.e("AudioLibraryManager", "Error renaming track in MediaStore", e)
        }

        val newList = _allTracks.value.map {
            if (it.id == track.id) it.copy(title = newName) else it
        }
        _allTracks.value = newList
        rebuildGroupings(newList)
        true
    }
}
