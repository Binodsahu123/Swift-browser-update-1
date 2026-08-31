package com.swift.browser.audioengine.manager

import android.content.Context
import android.content.SharedPreferences
import com.swift.browser.audioengine.model.AudioPlaylist
import com.swift.browser.audioengine.model.AudioTrackItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class AudioPlaylistManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("swift_audio_playlists", Context.MODE_PRIVATE)

    private val _playlists = MutableStateFlow<List<AudioPlaylist>>(emptyList())
    val playlists: StateFlow<List<AudioPlaylist>> = _playlists.asStateFlow()

    init {
        loadPlaylists()
    }

    private fun loadPlaylists() {
        val jsonStr = prefs.getString("playlists_json", null) ?: return
        try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<AudioPlaylist>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id", UUID.randomUUID().toString())
                val name = obj.optString("name", "Playlist")
                val dateCreated = obj.optLong("dateCreatedMs", System.currentTimeMillis())
                
                val tracksArray = obj.optJSONArray("tracks") ?: JSONArray()
                val tracks = mutableListOf<AudioTrackItem>()
                for (j in 0 until tracksArray.length()) {
                    val tObj = tracksArray.getJSONObject(j)
                    tracks.add(
                        AudioTrackItem(
                            id = tObj.optString("id"),
                            title = tObj.optString("title"),
                            artist = tObj.optString("artist", "Unknown Artist"),
                            album = tObj.optString("album"),
                            filePath = tObj.optString("filePath"),
                            durationMs = tObj.optLong("durationMs"),
                            artworkUri = tObj.optString("artworkUri"),
                            folderPath = tObj.optString("folderPath"),
                            size = tObj.optLong("size"),
                            mimeType = tObj.optString("mimeType", "audio/*"),
                            dateAddedMs = tObj.optLong("dateAddedMs")
                        )
                    )
                }
                list.add(AudioPlaylist(id = id, name = name, tracks = tracks, dateCreatedMs = dateCreated))
            }
            _playlists.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun savePlaylists() {
        try {
            val jsonArray = JSONArray()
            _playlists.value.forEach { playlist ->
                val obj = JSONObject().apply {
                    put("id", playlist.id)
                    put("name", playlist.name)
                    put("dateCreatedMs", playlist.dateCreatedMs)

                    val tracksArray = JSONArray()
                    playlist.tracks.forEach { track ->
                        val tObj = JSONObject().apply {
                            put("id", track.id)
                            put("title", track.title)
                            put("artist", track.artist)
                            put("album", track.album)
                            put("filePath", track.filePath)
                            put("durationMs", track.durationMs)
                            put("artworkUri", track.artworkUri)
                            put("folderPath", track.folderPath)
                            put("size", track.size)
                            put("mimeType", track.mimeType)
                            put("dateAddedMs", track.dateAddedMs)
                        }
                        tracksArray.put(tObj)
                    }
                    put("tracks", tracksArray)
                }
                jsonArray.put(obj)
            }
            prefs.edit().putString("playlists_json", jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun createPlaylist(name: String, tracks: List<AudioTrackItem> = emptyList()): AudioPlaylist {
        val newPlaylist = AudioPlaylist(
            id = UUID.randomUUID().toString(),
            name = name,
            tracks = tracks
        )
        _playlists.value = _playlists.value + newPlaylist
        savePlaylists()
        return newPlaylist
    }

    fun deletePlaylist(playlistId: String) {
        _playlists.value = _playlists.value.filter { it.id != playlistId }
        savePlaylists()
    }

    fun addTrackToPlaylist(playlistId: String, track: AudioTrackItem) {
        _playlists.value = _playlists.value.map { playlist ->
            if (playlist.id == playlistId) {
                if (!playlist.tracks.any { it.id == track.id }) {
                    playlist.copy(tracks = playlist.tracks + track)
                } else playlist
            } else playlist
        }
        savePlaylists()
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        _playlists.value = _playlists.value.map { playlist ->
            if (playlist.id == playlistId) {
                playlist.copy(tracks = playlist.tracks.filter { it.id != trackId })
            } else playlist
        }
        savePlaylists()
    }
}
