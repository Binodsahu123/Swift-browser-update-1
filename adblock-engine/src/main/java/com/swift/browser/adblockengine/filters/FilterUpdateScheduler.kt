package com.swift.browser.adblockengine.filters

import android.content.Context
import android.util.Log
import com.swift.browser.adblockengine.core.AdBlockDiagnostics
import com.swift.browser.adblockengine.core.AdBlockPolicy
import com.swift.browser.adblockengine.storage.AdBlockPreferenceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Schedules, stores, and performs manual or timed updates of EasyList and EasyPrivacy files.
 */
object FilterUpdateScheduler {
    private const val TAG = "FilterUpdateScheduler"
    
    var lastUpdateTime: Long = 0L
        private set

    fun init(context: Context) {
        lastUpdateTime = AdBlockPreferenceStore.getLong(context, "last_filter_update_time", 0L)
    }

    suspend fun triggerUpdate(
        context: Context,
        manual: Boolean,
        onComplete: ((Boolean) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting filter list update. Manual request: $manual")
        AdBlockDiagnostics.logEvent("Update Triggered", "Starting update. Manual=$manual")

        // Wi-Fi check
        if (!manual && AdBlockPolicy.wifiOnlyUpdate) {
            val isWifi = isWifiConnected(context)
            if (!isWifi) {
                Log.i(TAG, "Sync aborted because Wifi-Only check active and offline.")
                AdBlockDiagnostics.logEvent("Update Aborted", "Skipped sync: Wi-Fi only check failed.")
                onComplete?.invoke(false)
                return@withContext
            }
        }

        var updateSucceeded = false
        try {
            // Download EasyList
            val easyListRules = FilterListDownloader.download(EasyListSource.URL)
            if (easyListRules.isNotEmpty()) {
                FilterListCache.saveList(context, EasyListSource.NAME, easyListRules)
                updateSucceeded = true
            }

            // Download EasyPrivacy
            val easyPrivacyRules = FilterListDownloader.download(EasyPrivacySource.URL)
            if (easyPrivacyRules.isNotEmpty()) {
                FilterListCache.saveList(context, EasyPrivacySource.NAME, easyPrivacyRules)
                updateSucceeded = true
            }

            if (updateSucceeded) {
                lastUpdateTime = System.currentTimeMillis()
                AdBlockPreferenceStore.saveLong(context, "last_filter_update_time", lastUpdateTime)
                
                // Signal update
                FilterListManager.rebuildActiveRules(context)
                AdBlockDiagnostics.logEvent("Update Succeeded", "Lists synced. Timestamp: $lastUpdateTime")
            } else {
                AdBlockDiagnostics.logEvent("Update Failed", "Downloaded blocks empty. Keeping cached rules.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}")
            AdBlockDiagnostics.logEvent("Update Error", "Sync failed: ${e.message}")
        }

        onComplete?.invoke(updateSucceeded)
    }

    private fun isWifiConnected(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val activeNetwork = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            true // safe fallback
        }
    }
}
