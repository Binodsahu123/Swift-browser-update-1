package com.swift.browser.browserengine

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "StartupManager"

object SwiftStartupManager {
    private var isInitialized = false

    fun initialize(application: Application, scope: CoroutineScope, onFinished: () -> Unit) {
        if (isInitialized) return
        isInitialized = true

        Log.i(TAG, "Initializing SwiftStartupManager - Deferring heavy engines for fast Cold-Start")

        scope.launch(Dispatchers.IO) {
            try {
                com.swift.browser.adblockengine.AdProtectionEngineApi.getInstance(application).initialize(application)
            } catch (e: Exception) {
                Log.e(TAG, "AdProtectionEngineApi init failed: ${e.message}")
            }

            withContext(Dispatchers.Main) {
                onFinished()
                SwiftWebViewWarmup.warmUp(application)
            }
        }
    }
}

class StartupManager {

    private val delayedTasks = mutableListOf<() -> Unit>()
    private var isPriorityLoaded = false

    fun loadPriorityComponents(
        loadBrowserUI: () -> Unit,
        loadAddressBar: () -> Unit,
        loadTabManager: () -> Unit
    ) {
        if (isPriorityLoaded) return
        Log.d(TAG, "Executing Priority 1 startup sequence instantly...")
        try {
            loadBrowserUI()
            loadAddressBar()
            loadTabManager()
        } catch (e: Exception) {
            Log.e(TAG, "Error executing Priority 1 startup tasks", e)
        }
        isPriorityLoaded = true
    }

    fun registerDelayedTask(priorityLevel: Int, task: () -> Unit) {
        Log.d(TAG, "Registered Priority $priorityLevel delayed task")
        synchronized(delayedTasks) {
            delayedTasks.add(task)
        }
    }

    fun executeDelayedComponentsAsync() {
        // Run on background daemon thread to avoid main thread latency
        Thread {
            try {
                // Postpone secondary engine tasks until UI interaction has finalized (approx 1.5s)
                Thread.sleep(1500)
                Log.d(TAG, "Executing delayed startup components (Priority 2 and 3 engines)...")
                val tasksToRun = synchronized(delayedTasks) {
                    val copy = ArrayList(delayedTasks)
                    delayedTasks.clear()
                    copy
                }
                tasksToRun.forEach { task ->
                    try {
                        task()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error executing delayed task", e)
                    }
                }
            } catch (e: InterruptedException) {
                Log.e(TAG, "Delayed startup sequence interrupted", e)
            }
        }.start()
    }
}
