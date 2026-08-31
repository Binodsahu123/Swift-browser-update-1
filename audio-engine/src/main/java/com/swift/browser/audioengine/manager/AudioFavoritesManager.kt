package com.swift.browser.audioengine.manager

import android.content.Context
import android.content.SharedPreferences
import com.swift.browser.audioengine.model.AudioTrackItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioFavoritesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("swift_audio_favorites", Context.MODE_PRIVATE)

    private val _favorites = MutableStateFlow<List<AudioTrackItem>>(emptyList())
    val favorites: StateFlow<List<AudioTrackItem>> = _favorites.asStateFlow()

    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    init {
        loadFavorites()
    }

    private fun loadFavorites() {
        val saved = prefs.getStringSet("fav_ids", emptySet()) ?: emptySet()
        _favoriteIds.value = saved
    }

    fun toggleFavorite(track: AudioTrackItem) {
        val current = _favoriteIds.value.toMutableSet()
        if (current.contains(track.id)) {
            current.remove(track.id)
            _favorites.value = _favorites.value.filter { it.id != track.id }
        } else {
            current.add(track.id)
            if (!_favorites.value.any { it.id == track.id }) {
                _favorites.value = _favorites.value + track.copy(isFavorite = true)
            }
        }
        _favoriteIds.value = current
        prefs.edit().putStringSet("fav_ids", current).apply()
    }

    fun isFavorite(trackId: String): Boolean {
        return _favoriteIds.value.contains(trackId)
    }

    fun updateFavoritesWithLibrary(library: List<AudioTrackItem>) {
        val favIds = _favoriteIds.value
        val favList = library.filter { favIds.contains(it.id) }.map { it.copy(isFavorite = true) }
        _favorites.value = favList
    }
}
