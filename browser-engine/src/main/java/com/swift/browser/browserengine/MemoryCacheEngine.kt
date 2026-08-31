package com.swift.browser.browserengine

import android.content.Context
import android.webkit.WebView
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class MemoryReport(
    val activeWebViewsCount: Int,
    val potentialLeakedWebViewsCount: Int,
    val totalHeapMemoryBytes: Long,
    val usedHeapMemoryBytes: Long,
    val freeHeapMemoryBytes: Long,
    val activeCoroutinesEstCount: Int,
    val trackedContextsCount: Int,
    val isCriticalState: Boolean,
    val diagnosticsIssues: List<String>
)

object WebViewReferenceCollector {
    private const val TAG = "WebViewReference"
    private val webViewRefs = Collections.synchronizedList(mutableListOf<WeakReference<WebView>>())

    fun register(webView: WebView) {
        val exists = synchronized(webViewRefs) {
            webViewRefs.any { it.get() === webView }
        }
        if (!exists) {
            webViewRefs.add(WeakReference(webView))
            android.util.Log.d(TAG, "Registered living WebView reference. Total: ${webViewRefs.size}")
        }
    }

    fun getActiveAndLeakedCounts(): Pair<Int, Int> {
        cleanUpRefs()
        var activeCount = 0
        var leakedCount = 0
        
        synchronized(webViewRefs) {
            webViewRefs.forEach { ref ->
                val webView = ref.get()
                if (webView != null) {
                    activeCount++
                    if (!webView.isAttachedToWindow && webView.parent != null) {
                        leakedCount++
                    }
                }
            }
        }
        return Pair(activeCount, leakedCount)
    }

    private fun cleanUpRefs() {
        synchronized(webViewRefs) {
            val iterator = webViewRefs.iterator()
            while (iterator.hasNext()) {
                val ref = iterator.next()
                if (ref.get() == null) {
                    iterator.remove()
                }
            }
        }
    }
}

object LeakProneComponentTracker {
    private val trackedContextRefs = Collections.synchronizedList(mutableListOf<WeakReference<Context>>())

    fun trackContext(context: Context) {
        val exists = synchronized(trackedContextRefs) {
            trackedContextRefs.any { it.get() === context }
        }
        if (!exists) {
            trackedContextRefs.add(WeakReference(context))
        }
    }

    fun getTrackedContextsCount(): Int {
        cleanUpRefs()
        return synchronized(trackedContextRefs) { trackedContextRefs.size }
    }

    private fun cleanUpRefs() {
        synchronized(trackedContextRefs) {
            val iterator = trackedContextRefs.iterator()
            while (iterator.hasNext()) {
                val ref = iterator.next()
                if (ref.get() == null) {
                    iterator.remove()
                }
            }
        }
    }
}

object MemoryLeakDetector {
    private const val TAG = "MemoryLeakDetector"
    private val activeCoroutineCount = AtomicInteger(0)

    fun registerCoroutineStart() {
        activeCoroutineCount.incrementAndGet()
    }

    fun registerCoroutineFinished() {
        activeCoroutineCount.decrementAndGet()
    }

    fun getActiveCoroutinesCount(): Int {
        val count = activeCoroutineCount.get()
        return if (count >= 0) count else 0
    }

    fun generateMemoryReport(): MemoryReport {
        val (activeWebViews, leakedWebViews) = WebViewReferenceCollector.getActiveAndLeakedCounts()
        val trackedContexts = LeakProneComponentTracker.getTrackedContextsCount()
        
        val rt = Runtime.getRuntime()
        val totalHeap = rt.totalMemory()
        val freeHeap = rt.freeMemory()
        val usedHeap = totalHeap - freeHeap
        val maxMemory = rt.maxMemory()

        val isCritical = usedHeap.toDouble() / maxMemory.toDouble() > 0.85

        val issues = mutableListOf<String>()
        if (leakedWebViews > 0) {
            issues.add("Detected $leakedWebViews WebViews in dirty/unreleased intermediate states.")
        }
        if (isCritical) {
            issues.add("Java VM Heap usage exceeds 85%. Triggering immediate garbage collector sweep.")
        }
        if (trackedContexts > 15) {
            issues.add("High amount of living Context references ($trackedContexts). Check for activity reference leaks.")
        }
        val estCoroutines = getActiveCoroutinesCount()
        if (estCoroutines > 120) {
            issues.add("Spike in active Dispatcher/Coroutine streams ($estCoroutines). Check for non-terminating active jobs.")
        }

        return MemoryReport(
            activeWebViewsCount = activeWebViews,
            potentialLeakedWebViewsCount = leakedWebViews,
            totalHeapMemoryBytes = totalHeap,
            usedHeapMemoryBytes = usedHeap,
            freeHeapMemoryBytes = freeHeap,
            activeCoroutinesEstCount = estCoroutines,
            trackedContextsCount = trackedContexts,
            isCriticalState = isCritical,
            diagnosticsIssues = issues
        )
    }

    fun runSystemGC() {
        System.gc()
        System.runFinalization()
        android.util.Log.i(TAG, "Completed high-priority Garbage Collection sweep.")
    }
}

object AdaptiveLoadingEngine {
    fun getPreloadConcurrencyLimit(): Int {
        val report = MemoryLeakDetector.generateMemoryReport()
        val rt = Runtime.getRuntime()
        val usedRatio = report.usedHeapMemoryBytes.toDouble() / rt.maxMemory().toDouble()
        
        return when {
            usedRatio > 0.85 -> 0
            usedRatio > 0.70 -> 1
            usedRatio > 0.50 -> 2
            else -> 4
        }
    }

    fun shouldRunSystemGC(): Boolean {
        val report = MemoryLeakDetector.generateMemoryReport()
        return report.isCriticalState
    }
}

class MemoryCacheEngine<K : Any, V : Any>(private val maxCapacity: Int = 100) {
    private val cache = ConcurrentHashMap<K, V>()
    private val accessOrder = mutableListOf<K>()

    @Synchronized
    fun put(key: K, value: V) {
        if (cache.containsKey(key)) {
            accessOrder.remove(key)
        } else if (cache.size >= maxCapacity) {
            val oldestKey = accessOrder.removeAt(0)
            cache.remove(oldestKey)
        }
        cache[key] = value
        accessOrder.add(key)
    }

    @Synchronized
    fun get(key: K): V? {
        if (!cache.containsKey(key)) return null
        accessOrder.remove(key)
        accessOrder.add(key)
        return cache[key]
    }

    @Synchronized
    fun remove(key: K): V? {
        accessOrder.remove(key)
        return cache.remove(key)
    }

    @Synchronized
    fun clear() {
        cache.clear()
        accessOrder.clear()
    }

    fun size(): Int = cache.size
}

