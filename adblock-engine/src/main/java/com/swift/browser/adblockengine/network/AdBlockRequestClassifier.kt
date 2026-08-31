package com.swift.browser.adblockengine.network

import java.util.Locale

/**
 * Classifies a request URL to evaluate resource filters appropriately.
 */
object AdBlockRequestClassifier {
    fun classify(url: String?): String {
        if (url == null) return "other"
        
        val urlLower = url.lowercase(Locale.ROOT)
        return when {
            urlLower.endsWith(".js") || urlLower.contains("js?") || urlLower.contains("/js/") -> "script"
            urlLower.endsWith(".css") || urlLower.contains("css?") -> "stylesheet"
            urlLower.endsWith(".png") || urlLower.endsWith(".jpg") || urlLower.endsWith(".jpeg") || urlLower.endsWith(".gif") || urlLower.endsWith(".webp") || urlLower.endsWith(".svg") -> "image"
            urlLower.endsWith(".mp4") || urlLower.endsWith(".mp3") || urlLower.endsWith(".webm") || urlLower.endsWith(".ogg") || urlLower.endsWith(".m4a") -> "media"
            urlLower.contains("iframe") || urlLower.contains("/embed/") -> "subdocument"
            else -> "other"
        }
    }
}
