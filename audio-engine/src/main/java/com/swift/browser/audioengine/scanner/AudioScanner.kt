package com.swift.browser.audioengine.scanner

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import com.swift.browser.audioengine.model.AudioTrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AudioScanner {
    private const val TAG = "AudioScanner"

    suspend fun scanAudio(context: Context): List<AudioTrackItem> = withContext(Dispatchers.IO) {
        val audioList = mutableListOf<AudioTrackItem>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED
        )

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )

            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val mimeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val title = c.getString(titleCol) ?: "Unknown Track"
                    val artist = c.getString(artistCol) ?: "Unknown Artist"
                    val album = c.getString(albumCol) ?: "Unknown Album"
                    val duration = c.getLong(durationCol)
                    val path = c.getString(dataCol) ?: ""
                    val size = c.getLong(sizeCol)
                    val mime = c.getString(mimeCol) ?: "audio/*"
                    val dateAddedSec = c.getLong(dateCol)
                    val artworkUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                    ).toString()

                    val folder = if (path.contains("/")) path.substringBeforeLast("/").substringAfterLast("/") else "Music"

                    audioList.add(
                        AudioTrackItem(
                            id = id.toString(),
                            title = title,
                            artist = artist,
                            album = album,
                            filePath = path,
                            durationMs = duration,
                            artworkUri = artworkUri,
                            folderPath = folder,
                            size = size,
                            mimeType = mime,
                            dateAddedMs = dateAddedSec * 1000L
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning MediaStore audio", e)
        }

        audioList
    }
}
