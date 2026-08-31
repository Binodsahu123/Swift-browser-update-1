package com.swift.browser.adblockengine.settings

import android.content.Context

/**
 * Presentation-layer state holder managing settings profiles and event dispatches.
 */
class AdBlockSettingsViewModel(context: Context) {
    private val repository = AdBlockSettingsRepository(context)

    var adBlockEnabled: Boolean
        get() = repository.isAdBlockEnabled()
        set(value) {
            repository.setAdBlockEnabled(value)
        }

    var trackerBlockingEnabled: Boolean
        get() = repository.isTrackerBlockingEnabled()
        set(value) {
            repository.setTrackerBlockingEnabled(value)
        }

    var autoUpdateEnabled: Boolean
        get() = repository.isAutoUpdateEnabled()
        set(value) {
            repository.setAutoUpdateEnabled(value)
        }
}
