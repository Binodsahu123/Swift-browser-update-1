package com.swift.browser.adblockengine.update

import android.content.Context
import com.swift.browser.adblockengine.filters.FilterUpdateScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Service-like scheduled dispatcher that triggers updates at regular periodic intervals.
 */
object AdBlockRefreshWorker {
    private val scope = CoroutineScope(Dispatchers.Default)

    fun enqueuePeriodicRefresh(context: Context) {
        scope.launch {
            try {
                FilterUpdateScheduler.triggerUpdate(context, manual = false)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
