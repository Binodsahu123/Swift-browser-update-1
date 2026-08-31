package com.swift.browser.videoengine.live

class RtmpsClient(
    rtmpsUrlStr: String,
    timeoutMs: Int = 10000
) : RtmpClient(rtmpsUrlStr, timeoutMs) {
    init {
        if (!rtmpsUrlStr.startsWith("rtmps://", ignoreCase = true)) {
            throw IllegalArgumentException("RtmpsClient requires an rtmps:// URL schema.")
        }
    }
}
