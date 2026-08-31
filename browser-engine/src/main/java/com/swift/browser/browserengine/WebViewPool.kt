package com.swift.browser.browserengine

import android.app.ActivityManager
import android.content.Context
import android.webkit.WebView
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.collections.ArrayDeque

object SwiftWebViewPool {
    private const val TAG = "SwiftWebViewPool"
    private val pool = ArrayDeque<WebView>()
    private var maxPoolSize = 3
    private var webViewFactory: ((Context) -> WebView)? = null

    fun setFactory(factory: (Context) -> WebView) {
        synchronized(pool) {
            this.webViewFactory = factory
        }
    }

    fun init(context: Context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val ramGB = memInfo.totalMem / (1024L * 1024L * 1024L)
        maxPoolSize = when {
            ramGB >= 6 -> 5
            ramGB >= 4 -> 4
            else -> 2
        }
    }

    fun obtain(context: Context): WebView {
        synchronized(pool) {
            val recycled = pool.removeFirstOrNull()
            if (recycled != null) {
                recycled.clearHistory()
                return recycled
            }
        }
        val factory = webViewFactory
        return if (factory != null) {
            factory(context)
        } else {
            WebView(context)
        }
    }

    fun recycle(webView: WebView) {
        webView.stopLoading()
        webView.loadUrl("about:blank")
        synchronized(pool) {
            if (pool.size < maxPoolSize) {
                pool.addLast(webView)
            } else {
                webView.destroy()
            }
        }
    }

    fun size(): Int = synchronized(pool) { pool.size }

    fun clear() {
        synchronized(pool) {
            pool.forEach { it.destroy() }
            pool.clear()
        }
    }
}

object SwiftWebViewWarmup {
    fun warmUp(context: Context) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                SwiftWebViewPool.init(context)
                val currentSize = SwiftWebViewPool.size()
                if (currentSize < 2) {
                    val countToPrewarm = 2 - currentSize
                    repeat(countToPrewarm) {
                        val factory = SwiftWebViewPool.obtain(context)
                        SwiftWebViewPool.recycle(factory)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SwiftWebViewWarmup", "WebView pre-warming failed: ${e.message}")
            }
        }, 1500)
    }
}

class WebViewPool(

    private val context: Context,
    private val maxSize: Int = calculateMaxPool(context)
) {
    private val idlePool: Queue<WebView> = ConcurrentLinkedQueue()
    private val maxIdleCount = maxSize

    companion object {
        fun calculateMaxPool(context: Context): Int {
            return try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val info = ActivityManager.MemoryInfo()
                am.getMemoryInfo(info)
                val ramGB = info.totalMem / (1024L * 1024L * 1024L)
                when {
                    ramGB >= 6 -> 5
                    ramGB >= 4 -> 4
                    ramGB >= 3 -> 3
                    else -> 2
                }
            } catch (e: Exception) {
                3
            }
        }
    }

    fun warmUp(count: Int) {
        val limit = count.coerceAtMost(maxIdleCount)
        for (i in 0 until limit) {
            if (idlePool.size < maxIdleCount) {
                try {
                    val webView = createNewEmptyWebView()
                    idlePool.offer(webView)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun acquireWebView(): WebView {
        val cached = idlePool.poll()
        if (cached != null) {
            return cached
        }
        return createNewEmptyWebView()
    }

    fun releaseWebView(webView: WebView) {
        try {
            webView.apply {
                stopLoading()
                loadUrl("about:blank")
                clearHistory()
                removeAllViews()
            }
            if (idlePool.size < maxIdleCount) {
                idlePool.offer(webView)
            } else {
                webView.destroy()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun acquire(applySettings: (WebView) -> Unit = {}): WebView {
        val wv = acquireWebView()
        applySettings(wv)
        return wv
    }

    fun recycle(webView: WebView) {
        releaseWebView(webView)
    }

    fun preWarm(count: Int = 2, applySettings: (WebView) -> Unit = {}) {
        warmUp(count)
    }

    fun clear() {
        while (idlePool.isNotEmpty()) {
            idlePool.poll()?.destroy()
        }
    }

    private fun createNewEmptyWebView(): WebView {
        return WebView(context.applicationContext).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
        }
    }
}

