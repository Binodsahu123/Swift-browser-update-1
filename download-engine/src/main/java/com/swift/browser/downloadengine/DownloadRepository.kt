package com.swift.browser.downloadengine

import android.content.Context
import kotlinx.coroutines.flow.Flow

class DownloadRepository(private val context: Context) {
    private val db = DownloadDatabase.getDatabase(context)
    private val dao = db.downloadDao()

    fun getAllDownloadsFlow(): Flow<List<DownloadItem>> {
        return dao.getAllDownloadsFlow()
    }

    fun getNonPrivateDownloadsFlow(): Flow<List<DownloadItem>> {
        return dao.getNonPrivateDownloadsFlow()
    }

    suspend fun getPrivateDownloads(sessionId: String? = null): List<DownloadItem> {
        return dao.getPrivateDownloads(sessionId)
    }

    suspend fun cleanupPrivateMetadata(sessionId: String? = null, redactOnly: Boolean = false) {
        if (redactOnly) {
            dao.redactPrivateMetadata(sessionId)
        } else {
            dao.deletePrivateDownloads(sessionId)
        }
    }

    fun getDownloadsByCategoryFlow(category: String): Flow<List<DownloadItem>> {
        return dao.getDownloadsByCategoryFlow(category)
    }

    suspend fun getDownloadById(id: Long): DownloadItem? {
        return dao.getDownloadById(id)
    }

    suspend fun getScheduledDownloads(): List<DownloadItem> {
        return dao.getScheduledDownloads()
    }

    suspend fun getRunningDownloads(): List<DownloadItem> {
        return dao.getRunningDownloads()
    }

    suspend fun getPendingDownloadsSorted(): List<DownloadItem> {
        return dao.getPendingDownloadsSorted()
    }

    suspend fun insertOrUpdateDownload(item: DownloadItem) {
        dao.insertDownload(item)
    }

    suspend fun updateProgress(id: Long, status: String, progress: Int, downloaded: Long, speed: String) {
        dao.updateProgress(id, status, progress, downloaded, speed)
    }

    suspend fun deleteDownload(id: Long) {
        dao.deleteDownload(id)
    }

    suspend fun clearAll() {
        dao.deleteAll()
    }
}
