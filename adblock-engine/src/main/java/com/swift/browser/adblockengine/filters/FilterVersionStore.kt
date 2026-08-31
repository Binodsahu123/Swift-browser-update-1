package com.swift.browser.adblockengine.filters

import android.content.Context
import com.swift.browser.adblockengine.storage.AdBlockPreferenceStore

/**
 * Stores local versioning hashes and status parameters of each filter list.
 */
object FilterVersionStore {
    fun getVersion(context: Context, listName: String): String {
        return AdBlockPreferenceStore.getString(context, "version_$listName", "v1.0.0") ?: "v1.0.0"
    }

    fun setVersion(context: Context, listName: String, version: String) {
        AdBlockPreferenceStore.saveString(context, "version_$listName", version)
    }
}
