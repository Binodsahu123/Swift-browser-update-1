package com.swift.browser.videoengine.playlist

import android.content.Context
import com.swift.browser.videoengine.model.VideoItem
import com.swift.browser.videoengine.model.VideoPlaylist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class VideoPlaylistManager(private val context: Context? = null) {
    private val prefs = context?.applicationContext?.getSharedPreferences("video_playlist_prefs", Context.MODE_PRIVATE)

    private val _playlists = MutableStateFlow<List<VideoPlaylist>>(emptyList())
    val playlists: StateFlow<List<VideoPlaylist>> = _playlists.asStateFlow()

    init {
        loadPlaylists()
    }

    private fun loadPlaylists() {
        if (prefs == null) return
        val jsonStr = prefs.getString("playlists_json", null) ?: return
        try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<VideoPlaylist>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id", UUID.randomUUID().toString())
                val name = obj.optString("name", "")
                val itemsArray = obj.optJSONArray("items") ?: JSONArray()
                val items = mutableListOf<VideoItem>()
                for (j in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(j)
                    items.add(
                        VideoItem(
                            id = itemObj.optString("id"),
                            title = itemObj.optString("title"),
                            path = itemObj.optString("path"),
                            size = itemObj.optLong("size"),
                            sizeFormatted = itemObj.optString("sizeFormatted"),
                            mimeType = itemObj.optString("mimeType", "video/*"),
                            folder = itemObj.optString("folder"),
                            dateAdded = itemObj.optLong("dateAdded"),
                            duration = itemObj.optLong("duration"),
                            durationFormatted = itemObj.optString("durationFormatted"),
                            thumbnailUri = itemObj.optString("thumbnailUri", null)
                        )
                    )
                }
                list.add(VideoPlaylist(id = id, name = name, items = items))
            }
            _playlists.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun savePlaylists() {
        if (prefs == null) return
        try {
            val jsonArray = JSONArray()
            for (pl in _playlists.value) {
                val plObj = JSONObject()
                plObj.put("id", pl.id)
                plObj.put("name", pl.name)
                val itemsArray = JSONArray()
                for (item in pl.items) {
                    val itemObj = JSONObject()
                    itemObj.put("id", item.id)
                    itemObj.put("title", item.title)
                    itemObj.put("path", item.path)
                    itemObj.put("size", item.size)
                    itemObj.put("sizeFormatted", item.sizeFormatted)
                    itemObj.put("mimeType", item.mimeType)
                    itemObj.put("folder", item.folder)
                    itemObj.put("dateAdded", item.dateAdded)
                    itemObj.put("duration", item.duration ?: 0L)
                    itemObj.put("durationFormatted", item.durationFormatted)
                    itemObj.put("thumbnailUri", item.thumbnailUri ?: "")
                    itemsArray.put(itemObj)
                }
                plObj.put("items", itemsArray)
                jsonArray.put(plObj)
            }
            prefs.edit().putString("playlists_json", jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun createPlaylist(name: String): VideoPlaylist {
        val newPlaylist = VideoPlaylist(
            id = UUID.randomUUID().toString(),
            name = name,
            items = emptyList()
        )
        _playlists.value = _playlists.value + newPlaylist
        savePlaylists()
        return newPlaylist
    }

    fun renamePlaylist(id: String, newName: String) {
        _playlists.value = _playlists.value.map { pl ->
            if (pl.id == id) pl.copy(name = newName) else pl
        }
        savePlaylists()
    }

    fun deletePlaylist(id: String) {
        _playlists.value = _playlists.value.filter { it.id != id }
        savePlaylists()
    }

    fun addToPlaylist(playlistId: String, video: VideoItem) {
        _playlists.value = _playlists.value.map { pl ->
            if (pl.id == playlistId) {
                if (pl.items.none { it.id == video.id }) {
                    pl.copy(items = pl.items + video)
                } else pl
            } else pl
        }
        savePlaylists()
    }

    fun removeFromPlaylist(playlistId: String, videoId: String) {
        _playlists.value = _playlists.value.map { pl ->
            if (pl.id == playlistId) {
                pl.copy(items = pl.items.filter { it.id != videoId })
            } else pl
        }
        savePlaylists()
    }

    fun removeVideoFromAllPlaylists(videoId: String) {
        _playlists.value = _playlists.value.map { pl ->
            pl.copy(items = pl.items.filter { it.id != videoId })
        }
        savePlaylists()
    }
}
