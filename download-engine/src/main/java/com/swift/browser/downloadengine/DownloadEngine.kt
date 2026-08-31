package com.swift.browser.downloadengine

import android.content.Context
import kotlinx.coroutines.flow.Flow

interface DownloadEngine {
    fun getDownloadsFlow(): Flow<List<DownloadItem>>
    fun getDownloadsByCategory(category: String): Flow<List<DownloadItem>>
    suspend fun startDownload(
        url: String,
        fileName: String,
        mimeType: String,
        threads: Int = 4,
        isPrivate: Boolean = false,
        privateSessionId: String? = null
    ): Long = startDownload(url, fileName, mimeType, threads)
    suspend fun startDownload(url: String, fileName: String, mimeType: String, threads: Int): Long = 0L
    suspend fun pauseDownload(id: Long)
    suspend fun resumeDownload(id: Long)
    suspend fun cancelDownload(id: Long)
    suspend fun deleteDownload(id: Long)
    suspend fun renameDownload(id: Long, newName: String)
    fun setConfig(config: DownloadConfig)
    fun getConfig(): DownloadConfig
    suspend fun insertOrUpdateDownload(item: DownloadItem)
    suspend fun cleanupPrivateSession(sessionId: String? = null, redactOnly: Boolean = false) {}
}

class DownloadWorkers {
    fun enqueueDownloadCheck() {
        // Compatibility helper
    }
}
