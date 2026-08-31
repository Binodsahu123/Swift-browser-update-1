package com.swift.browser.cookieengine

import android.content.Context

object CookieEngineApi {
    private var instance: CookieEngine? = null
    
    fun getInstance(context: Context): CookieEngine {
        if (instance == null) {
            instance = CookieEngineImpl(context.applicationContext)
        }
        return instance!!
    }
}
