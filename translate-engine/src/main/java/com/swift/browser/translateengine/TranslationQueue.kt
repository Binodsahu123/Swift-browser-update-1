package com.swift.browser.translateengine

import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue

class TranslationQueue {
    private val queue = ConcurrentLinkedQueue<Deferred<Unit>>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun enqueueJob(action: suspend () -> Unit) {
        val job = scope.async {
            try {
                action()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        queue.offer(job)
        pruneQueue()
    }

    private fun pruneQueue() {
        val iterator = queue.iterator()
        while (iterator.hasNext()) {
            val job = iterator.next()
            if (job.isCompleted) {
                iterator.remove()
            }
        }
    }

    fun activeJobsCount(): Int {
        pruneQueue()
        return queue.size
    }

    fun cancelAll() {
        queue.forEach { it.cancel() }
        queue.clear()
    }
}
