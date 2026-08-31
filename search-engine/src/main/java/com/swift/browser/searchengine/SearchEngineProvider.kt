package com.swift.browser.searchengine

object SearchEngineProvider {
    @Volatile
    private var instance: SearchEngine? = null

    val api: SearchEngine
        get() = getEngine()

    fun getEngine(): SearchEngine {
        return instance ?: synchronized(this) {
            instance ?: SearchEngineImpl().also { instance = it }
        }
    }
}
