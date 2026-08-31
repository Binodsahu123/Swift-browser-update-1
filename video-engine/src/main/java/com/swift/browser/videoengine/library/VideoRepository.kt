package com.swift.browser.videoengine.library

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.swift.browser.videoengine.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object VideoScanner {
    private const val TAG = "VideoScanner"

    fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.getDefault(), "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0) return "0:00"
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / (1000 * 60)) % 60
        val hours = (durationMs / (1000 * 60 * 60))
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }
    }

    fun scanVideosFlow(context: Context): Flow<List<VideoItem>> = flow {
        val list = mutableListOf<VideoItem>()
        emit(emptyList())
        delay(50)

        try {
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }

            if (hasPermission) {
                val contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                val projection = arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.DATA,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.MIME_TYPE,
                    MediaStore.Video.Media.DATE_ADDED,
                    MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.WIDTH,
                    MediaStore.Video.Media.HEIGHT
                )

                val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

                context.contentResolver.query(
                    contentUri,
                    projection,
                    null,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                    val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                    val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                    val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)

                    var batchCount = 0
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol).toString()
                        val name = cursor.getString(nameCol) ?: "Video_$id"
                        val dataPath = cursor.getString(dataCol) ?: ""
                        val size = cursor.getLong(sizeCol)
                        val mime = cursor.getString(mimeCol) ?: "video/mp4"
                        val dateAdded = cursor.getLong(dateCol) * 1000
                        val duration = cursor.getLong(durCol)
                        val w = if (widthCol >= 0) cursor.getInt(widthCol) else null
                        val h = if (heightCol >= 0) cursor.getInt(heightCol) else null

                        val file = File(dataPath)
                        val folderName = if (file.parentFile != null) file.parentFile!!.name else "Camera"

                        val item = VideoItem(
                            id = "video_$id",
                            title = name,
                            path = dataPath,
                            size = size,
                            sizeFormatted = formatFileSize(size),
                            mimeType = mime,
                            folder = folderName,
                            dateAdded = dateAdded,
                            duration = duration,
                            durationFormatted = formatDuration(duration),
                            width = w,
                            height = h,
                            thumbnailUri = dataPath
                        )
                        list.add(item)
                        batchCount++

                        if (batchCount % 3 == 0) {
                            emit(list.toList())
                            delay(30)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning MediaStore for videos: ${e.message}", e)
        }

        emit(list)
    }.flowOn(Dispatchers.IO)
}

class VideoRepository(private val context: Context) {

    fun scanVideosFlow(): Flow<List<VideoItem>> {
        return VideoScanner.scanVideosFlow(context)
    }

    suspend fun renameVideo(item: VideoItem, newTitle: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(item.path)
            if (file.exists()) {
                val newFile = File(file.parent, "$newTitle.${file.extension}")
                if (file.renameTo(newFile)) {
                    val contentResolver: ContentResolver = context.contentResolver
                    val rawId = item.id.replace("video_", "").toLongOrNull() ?: 0L
                    if (rawId > 0L) {
                        val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, rawId)
                        val values = android.content.ContentValues().apply {
                            put(MediaStore.Video.Media.DISPLAY_NAME, newFile.name)
                            put(MediaStore.Video.Media.DATA, newFile.absolutePath)
                        }
                        contentResolver.update(uri, values, null, null)
                    }
                    return@withContext true
                }
            }
            return@withContext false
        } catch (e: Exception) {
            Log.e("VideoRepository", "Error renaming video", e)
            return@withContext false
        }
    }

    suspend fun deleteVideo(item: VideoItem): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(item.path)
            if (file.exists()) {
                file.delete()
            }
            val contentResolver: ContentResolver = context.contentResolver
            val rawId = item.id.replace("video_", "").toLongOrNull() ?: 0L
            if (rawId > 0L) {
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, rawId)
                contentResolver.delete(uri, null, null)
            }
            return@withContext true
        } catch (e: Exception) {
            Log.e("VideoRepository", "Error deleting video", e)
            return@withContext false
        }
    }
}
