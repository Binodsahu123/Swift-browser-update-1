package com.swift.browser.translateengine

import android.content.Context
import kotlinx.coroutines.*

class TranslateWorker(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Periodically prune old cached translations to save storage space.
     */
    fun startBackgroundPruneTask(olderThanMs: Long) {
        scope.launch {
            try {
                val repository = TranslationRepository(context)
                repository.pruneCache(olderThanMs)
            } catch (e: Exception) {
                LogTranslate.e("TranslateWorker", "Cache pruning background task failed", e)
            }
        }
    }
}

private object LogTranslate {
    fun e(tag: String, msg: String, tr: Throwable? = null) {
        android.util.Log.e(tag, msg, tr)
    }
}
