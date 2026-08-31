package com.swift.browser.browserengine.webrtc

data class WebMediaRuntimeSession(
    val sessionId: String,
    val url: String,
    val origin: String,
    val webViewVersion: String,
    val chromiumVersion: String,
    val isDesktopMode: Boolean,
    val isSecureContext: Boolean,
    val mediaCapabilities: WebMediaCapabilityMatrix,
    val deviceAvailability: Map<String, Boolean>,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toDiagnosticJson(): String {
        val json = org.json.JSONObject()
        json.put("sessionId", sessionId)
        json.put("url", url)
        json.put("origin", origin)
        json.put("webViewVersion", webViewVersion)
        json.put("chromiumVersion", chromiumVersion)
        json.put("isDesktopMode", isDesktopMode)
        json.put("isSecureContext", isSecureContext)
        
        val capsObj = org.json.JSONObject()
        mediaCapabilities.toMap().forEach { (k, v) ->
            capsObj.put(k, v)
        }
        json.put("capabilities", capsObj)

        val devicesObj = org.json.JSONObject()
        deviceAvailability.forEach { (k, v) ->
            devicesObj.put(k, v)
        }
        json.put("deviceAvailability", devicesObj)
        json.put("timestamp", timestamp)
        return json.toString()
    }
}
