package com.swift.browser.adblockengine.update

import android.content.Context
import com.swift.browser.adblockengine.filters.FilterUpdateScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles explicit background updates on demand.
 */
object AdBlockUpdateService {
    private val scope = CoroutineScope(Dispatchers.Default)

    fun startUpdate(context: Context, manual: Boolean) {
        scope.launch {
            FilterUpdateScheduler.triggerUpdate(context, manual)
        }
    }
}
