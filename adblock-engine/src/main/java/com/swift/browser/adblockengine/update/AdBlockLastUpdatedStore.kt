package com.swift.browser.adblockengine.update

import android.content.Context
import com.swift.browser.adblockengine.storage.AdBlockPreferenceStore

/**
 * Stores the physical timestamp when lists were last successfully updated.
 */
object AdBlockLastUpdatedStore {
    fun getLastUpdated(context: Context): Long {
        return AdBlockPreferenceStore.getLong(context, "last_filter_update_time", 0L)
    }

    fun setLastUpdated(context: Context, timestamp: Long) {
        AdBlockPreferenceStore.saveLong(context, "last_filter_update_time", timestamp)
    }
}
