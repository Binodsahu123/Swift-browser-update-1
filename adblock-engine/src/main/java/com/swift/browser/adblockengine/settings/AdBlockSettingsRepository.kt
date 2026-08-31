package com.swift.browser.adblockengine.settings

import android.content.Context
import com.swift.browser.adblockengine.core.AdBlockEngine
import com.swift.browser.adblockengine.core.AdBlockPolicy

/**
 * Handles operations and bindings for the settings interface.
 */
class AdBlockSettingsRepository(private val context: Context) {

    fun isAdBlockEnabled(): Boolean {
        return AdBlockPolicy.isEasyListEnabled
    }

    fun setAdBlockEnabled(enabled: Boolean) {
        AdBlockEngine.setEnabled(context, enabled)
    }

    fun isTrackerBlockingEnabled(): Boolean {
        return AdBlockPolicy.isEasyPrivacyEnabled
    }

    fun setTrackerBlockingEnabled(enabled: Boolean) {
        AdBlockEngine.updatePolicy(
            context,
            easyList = AdBlockPolicy.isEasyListEnabled,
            easyPrivacy = enabled,
            autoUpdate = AdBlockPolicy.autoUpdateEnabled,
            wifiOnly = AdBlockPolicy.wifiOnlyUpdate
        )
    }

    fun isAutoUpdateEnabled(): Boolean {
        return AdBlockPolicy.autoUpdateEnabled
    }

    fun setAutoUpdateEnabled(enabled: Boolean) {
        AdBlockEngine.updatePolicy(
            context,
            easyList = AdBlockPolicy.isEasyListEnabled,
            easyPrivacy = AdBlockPolicy.isEasyPrivacyEnabled,
            autoUpdate = enabled,
            wifiOnly = AdBlockPolicy.wifiOnlyUpdate
        )
    }
}
