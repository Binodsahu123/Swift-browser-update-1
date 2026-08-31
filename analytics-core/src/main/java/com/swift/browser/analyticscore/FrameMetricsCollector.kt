package com.swift.browser.analyticscore

import android.os.Handler
import android.os.Looper
import android.view.Choreographer

/**
 * Real Android Frame and Jank Metrics Collector.
 * Uses Choreographer frame callbacks to compute actual measured frame rates
 * and jank occurrences instead of fabricated constants.
 */
object FrameMetricsCollector {
    private const val ONE_SECOND_NANOS = 1_000_000_000L
    private const val JANK_THRESHOLD_NANOS = 33_333_333L // > 33ms (~ <30fps)

    private var isSampling = false
    private var lastFrameTimeNanos = 0L
    private var frameCountInInterval = 0
    private var intervalStartNanos = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isSampling) return

            if (lastFrameTimeNanos > 0L) {
                val frameDurationNanos = frameTimeNanos - lastFrameTimeNanos
                val isJank = frameDurationNanos > JANK_THRESHOLD_NANOS
                val frameDurationMs = frameDurationNanos / 1_000_000L

                frameCountInInterval++

                val elapsedInterval = frameTimeNanos - intervalStartNanos
                if (elapsedInterval >= ONE_SECOND_NANOS) {
                    val measuredFps = ((frameCountInInterval * ONE_SECOND_NANOS) / elapsedInterval).toInt()
                    AnalyticsCore.performanceAnalytics.recordFrameMetric(
                        frameDurationMs = frameDurationMs,
                        measuredFps = measuredFps,
                        isJank = isJank
                    )
                    frameCountInInterval = 0
                    intervalStartNanos = frameTimeNanos
                } else if (isJank) {
                    AnalyticsCore.performanceAnalytics.recordFrameMetric(
                        frameDurationMs = frameDurationMs,
                        measuredFps = null,
                        isJank = true
                    )
                }
            } else {
                intervalStartNanos = frameTimeNanos
            }

            lastFrameTimeNanos = frameTimeNanos
            if (isSampling) {
                try {
                    Choreographer.getInstance().postFrameCallback(this)
                } catch (_: Exception) {}
            }
        }
    }

    fun startSampling() {
        if (isSampling) return
        isSampling = true
        lastFrameTimeNanos = 0L
        frameCountInInterval = 0
        intervalStartNanos = 0L

        Handler(Looper.getMainLooper()).post {
            try {
                Choreographer.getInstance().postFrameCallback(frameCallback)
            } catch (_: Exception) {}
        }
    }

    fun stopSampling() {
        isSampling = false
        Handler(Looper.getMainLooper()).post {
            try {
                Choreographer.getInstance().removeFrameCallback(frameCallback)
            } catch (_: Exception) {}
        }
    }
}
