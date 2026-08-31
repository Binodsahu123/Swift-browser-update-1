package com.swift.browser

import android.app.Application
import android.content.Context
import android.content.Intent
import java.io.File
import java.util.Date
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BrowserApplication : Application() {
    private val applicationScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        com.swift.browser.browserengine.StartupCoordinator.instance.onApplicationStarted()
        
        // Initialize StartupTracker and Analytics Core
        com.swift.browser.analyticscore.StartupTracker.init(this)
        com.swift.browser.analyticscore.AnalyticsCore.startupAnalytics.markAppLaunchStart()
        com.swift.browser.analyticscore.AnalyticsCore.startSession()
        com.swift.browser.analyticscore.AnalyticsCore.recordStartupPhase("APPLICATION_ON_CREATE", com.swift.browser.analyticscore.StartupType.COLD)
        com.swift.browser.analyticscore.AnalyticsCore.crashAnalytics.attachUncaughtExceptionHandler()

        // Initialize Browser Engine core lifecycle
        try {
            com.swift.browser.analyticscore.StartupTracker.recordStage(
                stage = "BROWSER_ENGINE_START",
                className = "BrowserEngine",
                methodName = "initialize",
                success = true
            )
            com.swift.browser.browserengine.BrowserEngine.initialize(this)
        } catch (t: Throwable) {
            com.swift.browser.analyticscore.StartupTracker.recordStage(
                stage = "BROWSER_ENGINE_START",
                className = "BrowserEngine",
                methodName = "initialize",
                success = false,
                error = t
            )
            com.swift.browser.browserengine.StartupCoordinator.instance.markFailed(t)
        }

        try {
            com.swift.browser.analyticscore.StartupTracker.recordStage(
                stage = "PERMISSION_ENGINE_START",
                className = "PermissionEngineProvider",
                methodName = "initialize",
                success = true
            )
            com.swift.browser.permissionengine.PermissionEngineProvider.initialize(this)
        } catch (t: Throwable) {
            com.swift.browser.analyticscore.StartupTracker.recordStage(
                stage = "PERMISSION_ENGINE_START",
                className = "PermissionEngineProvider",
                methodName = "initialize",
                success = false,
                error = t
            )
        }

        // Run non-critical initializations asynchronously on background thread using applicationScope
        applicationScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val cacheDir = cacheDir
                val webViewCacheDirs = listOf(
                    "WebView/Default/HTTP Cache/Code Cache/js",
                    "WebView/Default/HTTP Cache/Code Cache/wasm",
                    "WebView/Default/HTTP Cache/index-dir",
                    "WebView/Default/GPUCache",
                    "WebView/Default/Service Worker/CacheStorage",
                    "WebView/Default/Service Worker/ScriptCache"
                )
                for (dirRelPath in webViewCacheDirs) {
                    val dir = File(cacheDir, dirRelPath)
                    if (!dir.exists()) {
                        dir.mkdirs()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                com.swift.browser.bookmarkengine.api.BookmarkEngineProvider.api.init(this@BrowserApplication)
                com.swift.browser.vpnengine.api.VpnEngineProvider.api.init(this@BrowserApplication)
                com.swift.browser.browserengine.SwiftDeveloperEngine.initFromPrefs(this@BrowserApplication)
                com.swift.browser.developertoolsengine.ConsoleEngine.instance.initializePersistence(filesDir)
                com.swift.browser.notificationengine.api.NotificationEngineProvider.api.init(this@BrowserApplication)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            throwable.printStackTrace()
            saveErrorLog(throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun saveErrorLog(throwable: Throwable) {
        try {
            com.swift.browser.analyticscore.AnalyticsCore.crashAnalytics.recordCrash(throwable, "Fatal application crash", isFatal = true)
            val file = File(filesDir, "crash_log.txt")
            file.writeText("${Date()}\n${throwable.stackTraceToString()}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
