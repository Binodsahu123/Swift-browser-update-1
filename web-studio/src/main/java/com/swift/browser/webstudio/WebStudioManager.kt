package com.swift.browser.webstudio

import android.content.Context
import com.swift.browser.webstudio.api.WebStudioEngineApi
import com.swift.browser.webstudio.engine.WebStudioEngine

object WebStudioManager {
    private var instance: WebStudioEngineApi? = null

    fun getEngine(context: Context): WebStudioEngineApi {
        if (instance == null) {
            instance = WebStudioEngine(context.applicationContext)
        }
        return instance!!
    }
}
