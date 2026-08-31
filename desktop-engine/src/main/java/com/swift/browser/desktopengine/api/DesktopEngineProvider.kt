package com.swift.browser.desktopengine.api

import android.content.Context
import com.swift.browser.desktopengine.internal.DesktopEngineImpl

object DesktopEngineProvider {
    @Volatile
    private var instance: DesktopEngineApi? = null

    val api: DesktopEngineApi
        get() {
            return instance ?: synchronized(this) {
                instance ?: DesktopEngineImpl().also { instance = it }
            }
        }

    fun init(context: Context) {
        api.initialize(context)
    }
}
