package com.swift.browser.audioengine.manager

import com.swift.browser.audioengine.model.AudioTrackItem
import com.swift.browser.audioengine.model.SortOption

class AudioSortManager {
    fun sortTracks(tracks: List<AudioTrackItem>, sortOption: SortOption): List<AudioTrackItem> {
        return when (sortOption) {
            SortOption.DATE_NEW_TO_OLD -> tracks.sortedByDescending { it.dateAddedMs }
            SortOption.DATE_OLD_TO_NEW -> tracks.sortedBy { it.dateAddedMs }
            SortOption.NAME_A_TO_Z -> tracks.sortedBy { it.title.lowercase() }
            SortOption.NAME_Z_TO_A -> tracks.sortedByDescending { it.title.lowercase() }
            SortOption.SIZE_LARGE_TO_SMALL -> tracks.sortedByDescending { it.size }
            SortOption.SIZE_SMALL_TO_LARGE -> tracks.sortedBy { it.size }
            SortOption.LENGTH_LONG_TO_SHORT -> tracks.sortedByDescending { it.durationMs }
            SortOption.LENGTH_SHORT_TO_LONG -> tracks.sortedBy { it.durationMs }
        }
    }
}
