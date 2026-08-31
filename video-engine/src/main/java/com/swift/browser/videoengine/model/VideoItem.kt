package com.swift.browser.videoengine.model

enum class VideoType {
    LOCAL, STREAM, DEMO
}

data class VideoItem(
    val id: String,
    val title: String,
    val path: String,
    val size: Long = 0L,
    val sizeFormatted: String = "",
    val mimeType: String = "video/mp4",
    val folder: String = "Videos",
    val dateAdded: Long = System.currentTimeMillis(),
    val duration: Long? = null, // in milliseconds
    val durationFormatted: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    var isFavorite: Boolean = false,
    val thumbnailUri: String? = null,
    val playCount: Int = 0,
    val resolution: String? = null,
    val codec: String? = null,
    val fps: Int? = null,
    val bitrate: String? = null
)

data class MediaFolder(
    val name: String,
    val itemCount: Int,
    val previewPath: String? = null
)

data class VideoPlaylist(
    val id: String,
    val name: String,
    val items: List<VideoItem> = emptyList()
)

data class VideoMetaData(
    val duration: String,
    val resolution: String,
    val size: String,
    val date: String,
    val playCount: Int,
    val isRecentlyPlayed: Boolean,
    val isDownloaded: Boolean,
    val codec: String,
    val fps: Int,
    val bitrate: String
)
