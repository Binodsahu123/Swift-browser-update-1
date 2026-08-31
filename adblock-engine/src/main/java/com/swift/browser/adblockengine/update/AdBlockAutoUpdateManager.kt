package com.swift.browser.adblockengine.update

import android.content.Context
import com.swift.browser.adblockengine.filters.FilterUpdateScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles automatic background updates and checks when the application starts.
 */
object AdBlockAutoUpdateManager {
    private val scope = CoroutineScope(Dispatchers.Default)

    fun checkAndRunUpdateOnStart(context: Context) {
        val lastUpdate = AdBlockLastUpdatedStore.getLastUpdated(context)
        val elapsed = System.currentTimeMillis() - lastUpdate
        
        // Update automatically every 24 hours
        if (elapsed > 24 * 60 * 60 * 1000L) {
            scope.launch {
                FilterUpdateScheduler.triggerUpdate(context, manual = false)
            }
        }
    }
}
