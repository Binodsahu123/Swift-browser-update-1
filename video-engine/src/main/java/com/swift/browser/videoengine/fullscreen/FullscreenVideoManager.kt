package com.swift.browser.videoengine.fullscreen

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class FullscreenVideoManager(private val activity: Activity) {
    private val orientationManager = VideoOrientationManager(activity)

    fun enterFullscreen() {
        val window = activity.window
        val decorView = window.decorView
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            WindowInsetsControllerCompat(window, decorView).let { controller ->
                controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        orientationManager.lockToLandscape()
    }

    fun exitFullscreen() {
        val window = activity.window
        val decorView = window.decorView
        WindowCompat.setDecorFitsSystemWindows(window, true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
            )
        } else {
            WindowInsetsControllerCompat(window, decorView).let { controller ->
                controller.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            }
        }

        orientationManager.restoreOrientation()
    }
}

class FullscreenVideoController(
    private val mainContainer: ViewGroup,
    private val fullscreenContainer: ViewGroup
) {
    private var customView: View? = null
    private var customViewCallback: android.webkit.WebChromeClient.CustomViewCallback? = null

    fun onShowCustomView(view: View, callback: android.webkit.WebChromeClient.CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }
        customView = view
        customViewCallback = callback

        mainContainer.visibility = View.GONE
        fullscreenContainer.visibility = View.VISIBLE
        fullscreenContainer.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun onHideCustomView() {
        val view = customView ?: return
        customView = null

        fullscreenContainer.removeView(view)
        fullscreenContainer.visibility = View.GONE
        mainContainer.visibility = View.VISIBLE

        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
    }

    fun isFullscreen(): Boolean = customView != null
}

object OrientationStatePreserver {
    private const val TAG = "OrientationState"
    private var lockedOrientation: Int? = null

    fun lockOrientation(activity: Activity, orientation: Int) {
        activity.requestedOrientation = orientation
        lockedOrientation = orientation
        Log.d(TAG, "Locked activity orientation to $orientation")
    }

    fun unlockOrientation(activity: Activity) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        lockedOrientation = null
        Log.d(TAG, "Unlocked activity orientation")
    }
}

class VideoOrientationManager(private val activity: Activity) {
    private var originalOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    fun lockToLandscape() {
        try {
            originalOrientation = activity.requestedOrientation
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } catch (e: Exception) {
            Log.e("VideoOrientation", "Lock to landscape failed", e)
        }
    }

    fun restoreOrientation() {
        try {
            activity.requestedOrientation = originalOrientation
        } catch (e: Exception) {
            Log.e("VideoOrientation", "Restore orientation failed", e)
        }
    }
}
