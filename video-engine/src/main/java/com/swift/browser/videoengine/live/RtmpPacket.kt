package com.swift.browser.videoengine.live

import java.nio.ByteBuffer

data class RtmpPacket(
    val type: Int, // 8 = Audio, 9 = Video, 18 = Metadata/Script
    val timestamp: Long, // 32-bit timestamp in milliseconds
    val payload: ByteArray,
    val isKeyframe: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RtmpPacket

        if (type != other.type) return false
        if (timestamp != other.timestamp) return false
        if (!payload.contentEquals(other.payload)) return false
        if (isKeyframe != other.isKeyframe) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + isKeyframe.hashCode()
        return result
    }
}
