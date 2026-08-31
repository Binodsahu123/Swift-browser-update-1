package com.swift.browser.newsengine.api

import android.content.Context
import com.swift.browser.newsengine.NewsEngineImpl
import kotlinx.coroutines.CoroutineScope

object NewsEngineProvider {
    @Volatile
    private var instance: NewsEngineApi? = null

    fun getEngine(context: Context, scope: CoroutineScope): NewsEngineApi {
        return instance ?: synchronized(this) {
            instance ?: NewsEngineImpl(context.applicationContext, scope).also { instance = it }
        }
    }
}
