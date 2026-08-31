package com.swift.browser.permissionengine

import android.app.Application
import android.content.Context

object PermissionEngineProvider {
    @Volatile
    private var instance: PermissionEngine? = null

    fun initialize(context: Context): PermissionEngine {
        return instance ?: synchronized(this) {
            instance ?: PermissionEngineImpl(context.applicationContext).also { instance = it }
        }
    }

    fun get(context: Context? = null): PermissionEngine {
        return instance ?: synchronized(this) {
            instance ?: if (context != null) {
                PermissionEngineImpl(context.applicationContext).also { instance = it }
            } else {
                throw IllegalStateException("PermissionEngine is not initialized yet and no context provided")
            }
        }
    }

    internal fun getImpl(context: Context? = null): PermissionEngineImpl {
        return get(context) as PermissionEngineImpl
    }
}
