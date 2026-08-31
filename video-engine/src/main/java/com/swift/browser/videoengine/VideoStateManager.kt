package com.swift.browser.videoengine

import android.webkit.WebView
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

data class SwiftVideoState(val position: Float, val isPlaying: Boolean)

object VideoStateManager {
    private const val TAG = "VideoStateManager"
    private val videoPositions = ConcurrentHashMap<String, Double>()
    private val videoPlayingState = ConcurrentHashMap<String, Boolean>()
    
    private val pendingVideoStates = ConcurrentHashMap<String, SwiftVideoState>()
    private val pendingVideoSeekTime = ConcurrentHashMap<String, Float>()

    fun isAnyVideoPlaying(): Boolean {
        return pendingVideoStates.values.any { it.isPlaying } || videoPlayingState.values.any { it }
    }

    fun saveVideoState(tabId: String, position: Float, isPlaying: Boolean) {
        pendingVideoStates[tabId] = SwiftVideoState(position, isPlaying)
        pendingVideoSeekTime[tabId] = position
        videoPositions[tabId] = position.toDouble()
        videoPlayingState[tabId] = isPlaying
    }

    fun saveVideoSeekTime(tabId: String, position: Float) {
        pendingVideoSeekTime[tabId] = position
        videoPositions[tabId] = position.toDouble()
    }

    fun getPendingState(tabId: String): SwiftVideoState? = pendingVideoStates[tabId]
    fun getPendingSeekTime(tabId: String): Float? = pendingVideoSeekTime[tabId]

    fun clearPendingState(tabId: String) {
        pendingVideoStates.remove(tabId)
        pendingVideoSeekTime.remove(tabId)
    }

    fun captureActiveVideoState(tabId: String, webView: WebView?) {
        if (webView == null || tabId.isBlank()) return
        webView.evaluateJavascript("""
            (function() {
                var v = document.querySelector('video');
                if (!v) return null;
                return JSON.stringify({
                    time: v.currentTime,
                    playing: !v.paused
                });
            })();
        """.trimIndent()) { resultJson ->
            if (!resultJson.isNullOrBlank() && resultJson != "null" && resultJson != "undefined") {
                val cleanJson = if (resultJson.startsWith("\"") && resultJson.endsWith("\"")) {
                    resultJson.replace("\\\"", "\"").removeSurrounding("\"")
                } else {
                    resultJson
                }
                try {
                    val obj = JSONObject(cleanJson)
                    val time = obj.optDouble("time", -1.0)
                    val playing = obj.optBoolean("playing", false)
                    if (time > 0.1) {
                        saveVideoState(tabId, time.toFloat(), playing)
                        Log.i(TAG, "Captured video state for tab $tabId: time=$time, playing=$playing")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing captured video json", e)
                }
            }
        }
    }

    fun resumeActiveVideosIfPending(tabId: String, webView: WebView?) {
        if (webView == null || tabId.isBlank()) return
        val pendingState = pendingVideoStates[tabId]
        if (pendingState != null) {
            webView.evaluateJavascript("""
                (function() {
                    var v = document.querySelector('video');
                    if (v) {
                        v.currentTime = ${pendingState.position};
                        if (${pendingState.isPlaying}) {
                            v.play().catch(function(e) { console.log("Video auto play failed:", e); });
                        } else {
                            v.pause();
                        }
                    }
                })();
            """.trimIndent(), null)
            pendingVideoStates.remove(tabId)
        }
    }

    fun captureState(tabId: String, webView: WebView?) {
        captureActiveVideoState(tabId, webView)
    }

    fun restoreState(tabId: String, webView: WebView?) {
        resumeActiveVideosIfPending(tabId, webView)
    }

    fun restorePendingState(tabId: String, webView: WebView?) {
        if (webView == null || tabId.isBlank()) return
        val pendingState = pendingVideoStates[tabId]
        if (pendingState != null && pendingState.position > 0.1f) {
            webView.evaluateJavascript("""
                (function() {
                    var targetTime = ${pendingState.position};
                    var targetPlaying = ${pendingState.isPlaying};
                    var attempts = 0;
                    function seekVideo() {
                        var video = document.querySelector('video');
                        if (video) {
                            if (video.readyState >= 1) {
                                video.currentTime = targetTime;
                                if (targetPlaying) {
                                    video.play().catch(function(e) { console.log("Auto-play failed:", e); });
                                } else {
                                    video.pause();
                                }
                            } else {
                                video.addEventListener('loadedmetadata', function() {
                                    video.currentTime = targetTime;
                                    if (targetPlaying) {
                                        video.play().catch(function(e) { console.log("Auto-play on loadedmetadata failed:", e); });
                                    } else {
                                        video.pause();
                                    }
                                }, { once: true });
                            }
                        } else if (attempts < 65) {
                            attempts++;
                            setTimeout(seekVideo, 120);
                        }
                    }
                    seekVideo();
                })();
            """.trimIndent(), null)
            pendingVideoStates.remove(tabId)
            pendingVideoSeekTime.remove(tabId)
        } else {
            val pendingSeek = pendingVideoSeekTime[tabId]
            if (pendingSeek != null && pendingSeek > 0.1f) {
                webView.evaluateJavascript("""
                    (function() {
                        var targetTime = $pendingSeek;
                        var attempts = 0;
                        function seekVideo() {
                            var video = document.querySelector('video');
                            if (video) {
                                if (video.readyState >= 1) {
                                    video.currentTime = targetTime;
                                    video.play().catch(function(e) { console.log("Auto-play failed:", e); });
                                } else {
                                    video.addEventListener('loadedmetadata', function() {
                                        video.currentTime = targetTime;
                                        video.play().catch(function(e) { console.log("Auto-play on loadedmetadata failed:", e); });
                                    }, { once: true });
                                }
                            } else if (attempts < 60) {
                                attempts++;
                                setTimeout(seekVideo, 150);
                            }
                        }
                        seekVideo();
                    })();
                """.trimIndent(), null)
                pendingVideoSeekTime.remove(tabId)
            }
        }
    }
}

