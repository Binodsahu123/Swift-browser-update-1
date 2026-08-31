package com.swift.browser.downloadengine

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.util.Log

class SwiftDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val itemId = inputData.getLong("DOWNLOAD_ITEM_ID", -1L)
        if (itemId == -1L) return Result.failure()

        Log.i("SwiftDownloadWorker", "WorkManager background thread processing download ID: $itemId")
        
        try {
            val db = DownloadDatabase.getDatabase(applicationContext)
            val dao = db.downloadDao()
            val item = dao.getDownloadById(itemId) ?: return Result.failure()

            if (item.status == "COMPLETED") {
                return Result.success()
            }

            // Execute the standard task orchestration via DownloadQueueManager
            val config = DownloadConfig()
            DownloadQueueManager.startDownloadTask(applicationContext, item, config) { updated ->
                dao.updateDownload(updated)
            }
            
            Log.i("SwiftDownloadWorker", "WorkManager task successfully dispatched to Queue Manager.")
            return Result.success()
        } catch (e: Exception) {
            Log.e("SwiftDownloadWorker", "Background download task execution failed", e)
            return Result.retry()
        }
    }
}

object DownloadWorker {
    private const val TAG = "DownloadWorker"

    /**
     * Periodically or reactively checks for halted tasks and triggers safe download resumes.
     */
    fun checkAndResumeDownloads(context: Context, engine: DownloadEngine) {
        val config = engine.getConfig()
        if (config.autoResume) {
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    val list = engine.getDownloadsFlow().first()
                    list.forEach { item ->
                        if (item.status == "PENDING" || item.status == "RUNNING") {
                            Log.i(TAG, "DownloadWorker: Restoring interrupted item: ${item.title} (${item.id})")
                            enqueueDownload(context, item.id)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "DownloadWorker failed to evaluate resume targets", e)
                }
            }
        }
    }

    /**
     * Registers a background task request in WorkManager to process downloading robustly.
     */
    fun enqueueDownload(context: Context, itemId: Long) {
        try {
            val data = Data.Builder()
                .putLong("DOWNLOAD_ITEM_ID", itemId)
                .build()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SwiftDownloadWorker>()
                .setInputData(data)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "SwiftDownload_$itemId",
                ExistingWorkPolicy.REPLACE,
                request
            )
            Log.i(TAG, "Successfully enqueued unique background work for download ID: $itemId")
        } catch (e: Throwable) {
            Log.e(TAG, "WorkManager enqueue failed: ${e.message}")
        }
    }
}

