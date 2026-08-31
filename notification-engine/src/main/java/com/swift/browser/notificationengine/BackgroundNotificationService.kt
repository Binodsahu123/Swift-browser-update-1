package com.swift.browser.notificationengine

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object BackgroundNotificationService {
    private const val TAG = "BackgroundNotificationS"
    private const val WORK_NAME = "swift_website_notifications_sync"

    /**
     * Initializes the background notification engine and registers periodic worker updates.
     */
    fun startEngine(context: Context) {
        Log.i(TAG, "Initializing background notification sync service engine")
        try {
            // Ensure channel creation is fresh
            NotificationChannelManager.createNotificationChannels(context)

            // Setup a recurring 15-minute update (minimum allowed by Android OS Workmanager specifications)
            val request = PeriodicWorkRequestBuilder<NotificationWorker>(
                15, TimeUnit.MINUTES, // Periodic Interval
                5, TimeUnit.MINUTES // Flex Interval
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // Retain existing job schedule so we don't restart counters.
                request
            )
            Log.d(TAG, "Successfully registered unique background periodic work worker sync job")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start notification engine: ${e.message}")
        }
    }

    /**
     * Forces an immediate update work execute for manual developer panel / force update actions.
     */
    fun forceImmediateSync(context: Context) {
        Log.i(TAG, "Triggering immediate notifications update sync manually")
        try {
            val request = androidx.work.OneTimeWorkRequestBuilder<NotificationWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch immediate sync: ${e.message}")
        }
    }

    /**
     * Stop background sync and unregister worker.
     */
    fun stopEngine(context: Context) {
        Log.w(TAG, "Halting background notification updates completely")
        try {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        } catch (e: Exception) {
            Log.e(TAG, "Cancellation failed: ${e.message}")
        }
    }

    /**
     * Fetches current state of background sync workers.
     */
    fun getEngineStatus(context: Context, callback: (String) -> Unit) {
        try {
            val list = WorkManager.getInstance(context).getWorkInfosForUniqueWork(WORK_NAME).get()
            if (list.isNullOrEmpty()) {
                callback("INACTIVE")
                return
            }
            val info = list.first()
            callback(info.state.name)
        } catch (e: Exception) {
            callback("UNKNOWN")
        }
    }
}
