package com.swift.browser.privatemode

import android.content.Context

/**
 * Singleton provider for accessing PrivateModeEngine.
 */
object PrivateModeEngineProvider {
    @Volatile
    private var instance: PrivateModeEngineApi? = null

    val api: PrivateModeEngineApi
        get() = instance ?: throw IllegalStateException("PrivateModeEngineProvider not initialized with Context yet")

    fun getEngine(context: Context): PrivateModeEngineApi {
        return instance ?: synchronized(this) {
            instance ?: PrivateModeEngineImpl.getInstance(context).also { instance = it }
        }
    }

    fun resetForTesting() {
        synchronized(this) {
            instance = null
        }
    }
}

