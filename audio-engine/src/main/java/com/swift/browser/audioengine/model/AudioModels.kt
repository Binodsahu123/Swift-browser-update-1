package com.swift.browser.audioengine.model

data class AudioTrackItem(
    val id: String = "",
    val title: String = "",
    val artist: String? = "Unknown Artist",
    val album: String? = null,
    val filePath: String = "",
    val durationMs: Long = 0L,
    val artworkUri: String? = null,
    val folderPath: String = "",
    val size: Long = 0L,
    val mimeType: String = "audio/*",
    val dateAddedMs: Long = 0L,
    val isFavorite: Boolean = false
)

enum class SortOption {
    DATE_NEW_TO_OLD,
    DATE_OLD_TO_NEW,
    NAME_A_TO_Z,
    NAME_Z_TO_A,
    SIZE_LARGE_TO_SMALL,
    SIZE_SMALL_TO_LARGE,
    LENGTH_LONG_TO_SHORT,
    LENGTH_SHORT_TO_LONG
}

data class AudioPlaylist(
    val id: String = "",
    val name: String = "",
    val tracks: List<AudioTrackItem> = emptyList(),
    val dateCreatedMs: Long = System.currentTimeMillis()
)

data class AudioAlbum(
    val name: String,
    val artist: String? = null,
    val trackCount: Int = 0,
    val artworkUri: String? = null
)

data class AudioArtist(
    val name: String,
    val trackCount: Int = 0,
    val albumCount: Int = 0
)

data class AudioFolder(
    val name: String,
    val path: String,
    val trackCount: Int = 0
)

enum class AudioState {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    STOPPED,
    ERROR
}

enum class PlaybackSource {
    LOCAL,
    ONLINE,
    NONE
}

data class AudioQueueState(
    val tracks: List<AudioTrackItem> = emptyList(),
    val currentIndex: Int = -1,
    val isShuffle: Boolean = false,
    val repeatMode: Int = 0 // 0: None, 1: Repeat All, 2: Repeat One
) {
    val currentTrack: AudioTrackItem?
        get() = if (currentIndex in tracks.indices) tracks[currentIndex] else null
}

data class AudioSessionState(
    val audioSessionId: Int = 0,
    val hasFocus: Boolean = false,
    val volume: Float = 1.0f,
    val playbackSpeed: Float = 1.0f
)

data class AudioError(
    val errorCode: Int,
    val message: String,
    val cause: Throwable? = null
)
