package com.swift.browser.tabengine.api

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import com.swift.browser.tabengine.engine.TabEngine

object TabEngineProvider {
    @Volatile
    private var instance: TabEngineApi? = null

    fun getEngine(context: Context, scope: CoroutineScope): TabEngineApi {
        return instance ?: synchronized(this) {
            instance ?: TabEngine(context, scope).also { instance = it }
        }
    }
}
