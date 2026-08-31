package com.swift.browser.adblockengine.network

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object AdBlockEmptyResponseBuilder {
    private val transparentPixel = byteArrayOf(
        0x89.toByte(), 0x50.toByte(), 0x4e.toByte(), 0x47.toByte(),
        0x0d.toByte(), 0x0a.toByte(), 0x1a.toByte(), 0x0a.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0d.toByte(),
        0x49.toByte(), 0x48.toByte(), 0x44.toByte(), 0x52.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(),
        0x08.toByte(), 0x06.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x1f.toByte(), 0x15.toByte(), 0xc4.toByte(),
        0x89.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x0a.toByte(), 0x49.toByte(), 0x44.toByte(), 0x41.toByte(),
        0x54.toByte(), 0x78.toByte(), 0x9c.toByte(), 0x63.toByte(),
        0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x05.toByte(), 0x00.toByte(), 0x01.toByte(), 0x0d.toByte(),
        0x0a.toByte(), 0x2d.toByte(), 0xb4.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x49.toByte(),
        0x45.toByte(), 0x4e.toByte(), 0x44.toByte(), 0xae.toByte(),
        0x42.toByte(), 0x60.toByte(), 0x82.toByte()
    )

    fun create(resourceType: String?, url: String): WebResourceResponse? {
        val typeLower = resourceType?.lowercase() ?: ""
        val urlLower = url.lowercase()
        
        if (typeLower == "image" || urlLower.endsWith(".png") || urlLower.endsWith(".jpg") || urlLower.endsWith(".gif") || urlLower.endsWith(".webp") || urlLower.endsWith(".ico")) {
            return WebResourceResponse("image/png", "UTF-8", ByteArrayInputStream(transparentPixel))
        }
        
        if (typeLower == "script" || urlLower.endsWith(".js")) {
            return WebResourceResponse("application/javascript", "UTF-8", ByteArrayInputStream("/* blocked by adblock */".toByteArray()))
        }
        
        if (typeLower == "stylesheet" || urlLower.endsWith(".css")) {
            return WebResourceResponse("text/css", "UTF-8", ByteArrayInputStream("/* blocked by adblock */".toByteArray()))
        }
        
        if (typeLower == "media" || urlLower.endsWith(".mp4") || urlLower.endsWith(".mp3")) {
            return WebResourceResponse("video/mp4", "UTF-8", null) // Might prefer returning null or a real empty stream? Better to return null stream if possible, or an empty byte stream.
        }

        if (typeLower == "document" || typeLower == "main_frame") {
            // Document - do not blank accidentally. Return null to let the browser handle it if possible, or an empty HTML.
            // Often if we block the main frame we might want to return an empty response but the prompt says:
            // "document -> do not blank accidentally. If WebView has no clean resource-level cancellation mechanism for a specific type, prefer returning null over breaking the page."
            return null
        }
        
        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
    }
}
