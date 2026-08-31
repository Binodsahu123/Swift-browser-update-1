package com.swift.browser.tabengine.engine

import android.graphics.Bitmap
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TabSnapshotEngine(private val scope: CoroutineScope) {
    // Memory-bounded caches instead of count-bounded. We use KB.
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSizeHighRes = maxMemory / 8 // 1/8th of max memory
    private val cacheSizeThumbnail = maxMemory / 16 // 1/16th of max memory

    private val highResCache = object : LruCache<String, Bitmap>(cacheSizeHighRes) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    private val thumbnailCache = object : LruCache<String, Bitmap>(cacheSizeThumbnail) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun saveSnapshot(tabId: String, bitmap: Bitmap) {
        scope.launch(Dispatchers.Default) {
            try {
                highResCache.put(tabId, bitmap)
                val thumbWidth = (bitmap.width / 4).coerceAtLeast(1)
                val thumbHeight = (bitmap.height / 4).coerceAtLeast(1)
                val thumbnail = Bitmap.createScaledBitmap(bitmap, thumbWidth, thumbHeight, true)
                thumbnailCache.put(tabId, thumbnail)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getHighResSnapshot(tabId: String): Bitmap? = highResCache.get(tabId)
    
    fun getThumbnail(tabId: String): Bitmap? = thumbnailCache.get(tabId) ?: highResCache.get(tabId)
    
    fun clearSnapshot(tabId: String) {
        highResCache.remove(tabId)
        thumbnailCache.remove(tabId)
    }
    
    fun evictAll() {
        highResCache.evictAll()
        thumbnailCache.evictAll()
    }
}
