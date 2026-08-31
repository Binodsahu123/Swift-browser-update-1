package com.swift.browser.audioengine.manager

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.swift.browser.audioengine.model.AudioTrackItem
import com.swift.browser.nativemediaengine.NativeMediaEngine
import org.json.JSONObject

object AudioMetadataManager {
    private const val TAG = "AudioMetadataManager"

    fun extractMetadata(context: Context, filePath: String): AudioTrackItem {
        // Try NativeMediaEngine first for performance
        try {
            val jsonStr = NativeMediaEngine.parseMediaMetadata(filePath)
            val json = JSONObject(jsonStr)
            if (json.optString("status") == "success") {
                val fileName = json.optString("fileName", filePath.substringAfterLast("/"))
                val duration = json.optLong("durationMs", 0L)
                return AudioTrackItem(
                    id = filePath,
                    title = fileName.substringBeforeLast("."),
                    filePath = filePath,
                    durationMs = duration,
                    size = json.optLong("fileSize", 0L)
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Native metadata extraction fallback to MediaMetadataRetriever", e)
        }

        // Fallback to MediaMetadataRetriever
        val retriever = MediaMetadataRetriever()
        return try {
            val uri = if (filePath.startsWith("content://") || filePath.startsWith("http://") || filePath.startsWith("https://")) {
                Uri.parse(filePath)
            } else {
                Uri.fromFile(java.io.File(filePath))
            }
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: filePath.substringAfterLast("/").substringBeforeLast(".")
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L

            AudioTrackItem(
                id = filePath,
                title = title,
                artist = artist,
                album = album,
                filePath = filePath,
                durationMs = durationMs
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse metadata for $filePath", e)
            AudioTrackItem(
                id = filePath,
                title = filePath.substringAfterLast("/").substringBeforeLast("."),
                filePath = filePath
            )
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }
}
