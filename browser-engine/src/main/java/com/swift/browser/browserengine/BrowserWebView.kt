package com.swift.browser.browserengine

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import kotlin.math.abs

fun interface BrowserWebViewScrollListener {
    fun onWebViewScroll(diffY: Int, isAtTop: Boolean)
}

interface BrowserWebViewNavigationListener {
    fun canGoBack(): Boolean
    fun goBack()
    fun canGoForward(): Boolean
    fun goForward()
}

open class BrowserWebView @JvmOverloads constructor(
    context: Context,
    var tabId: String = "",
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    var scrollListener: BrowserWebViewScrollListener? = null
    var navigationListener: BrowserWebViewNavigationListener? = null
    var speechBridge: WebSpeechRecognitionBridge? = null

    var isPrivate: Boolean = false
    var privateSessionId: String? = null
    var profileName: String? = null

    private var startX = 0f
    private var startY = 0f
    private val swipeThreshold = 150f
    private val yThreshold = 100f
    private var lastSizeChangedTime = 0L
    private var isUserTouching = false

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        lastSizeChangedTime = System.currentTimeMillis()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (visibility == View.GONE || visibility == View.INVISIBLE) {
            super.onWindowVisibilityChanged(View.VISIBLE)
        } else {
            super.onWindowVisibilityChanged(visibility)
        }
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        // Ignore any scroll events generated during or immediately after layout resizing (cool-down of 800ms)
        if (System.currentTimeMillis() - lastSizeChangedTime < 800) {
            return
        }
        val currentY = t
        val diff = currentY - oldt

        if (currentY <= 25) {
            scrollListener?.onWebViewScroll(diff, true)
            return
        }
        if (abs(diff) > 1) {
            scrollListener?.onWebViewScroll(diff, false)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isUserTouching = true
                startX = event.x
                startY = event.y
                // Only mark as focusable, DO NOT aggressively request focus to avoid breaking iframe/input handling.
                isFocusable = true
                isFocusableInTouchMode = true
            }
            MotionEvent.ACTION_MOVE -> {
                isUserTouching = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isUserTouching = false
                val diffX = event.x - startX
                val diffY = event.y - startY
                if (abs(diffX) > abs(diffY) && abs(diffX) > swipeThreshold && abs(diffY) < yThreshold) {
                    if (diffX > 0) {
                        val nav = navigationListener
                        if (nav != null && nav.canGoBack()) {
                            nav.goBack()
                            return true
                        } else if (canGoBack()) {
                            safeGoBack()
                            return true
                        }
                    } else {
                        val nav = navigationListener
                        if (nav != null && nav.canGoForward()) {
                            nav.goForward()
                            return true
                        } else if (canGoForward()) {
                            goForward()
                            return true
                        }
                    }
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun goBack() {
        if (canGoBack()) {
            super.goBack()
        }
    }

    fun safeGoBack() {
        if (canGoBack()) {
            super.goBack()
        }
    }

    override fun destroy() {
        try {
            if (tabId.isNotBlank()) {
                com.swift.browser.browserengine.webrtc.WebRtcRuntimeManager.onWebViewDestroyed(tabId)
                com.swift.browser.browserengine.webrtc.WebRtcRecoveryCoordinator.cancelRecoveryForTab(tabId)
                com.swift.browser.browserengine.webrtc.WebMediaDeviceManager.unregisterWebView(tabId)
                com.swift.browser.browserengine.screencapture.ScreenCaptureManager.onWebViewDestroyed(tabId)
            }
            speechBridge?.destroy()
            speechBridge = null
        } catch (e: Exception) {
            // Ignore destroy errors
        }
        super.destroy()
    }
}
