package com.swift.browser.videoengine.live

class RtmpMessage(
    val type: Int,
    val chunkStreamId: Int,
    val messageStreamId: Int,
    val timestamp: Long,
    val payload: ByteArray
) {
    override fun toString(): String {
        return "RtmpMessage(type=$type, csid=$chunkStreamId, msid=$messageStreamId, ts=$timestamp, size=${payload.size})"
    }
}
