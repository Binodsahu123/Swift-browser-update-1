package com.swift.browser.videoengine.live

import java.io.ByteArrayOutputStream

object VideoTag {

    /**
     * Generates an FLV Video Sequence Header Tag (AVCPacketType = 0).
     */
    fun createSequenceHeader(avcConfigRecord: ByteArray, timestampMs: Long): RtmpPacket {
        val payload = ByteArray(5 + avcConfigRecord.size)
        
        // FrameType (1 = Keyframe), CodecID (7 = AVC) -> 0x17
        payload[0] = 0x17.toByte()
        // AVCPacketType (0 = AVC sequence header)
        payload[1] = 0.toByte()
        // CompositionTime (3 bytes: 0)
        payload[2] = 0.toByte()
        payload[3] = 0.toByte()
        payload[4] = 0.toByte()
        
        System.arraycopy(avcConfigRecord, 0, payload, 5, avcConfigRecord.size)
        
        return RtmpPacket(
            type = 9, // Video tag type
            timestamp = timestampMs,
            payload = payload,
            isKeyframe = true
        )
    }

    /**
     * Generates a standard AVC Video Tag containing length-prefixed NALUs (AVCPacketType = 1).
     */
    fun createVideoTag(nalus: List<ByteArray>, timestampMs: Long, isKeyframe: Boolean): RtmpPacket {
        var payloadSize = 5
        for (nalu in nalus) {
            payloadSize += 4 + nalu.size
        }
        
        val payload = ByteArray(payloadSize)
        
        // FrameType (1 = Keyframe, 2 = Interframe), CodecID (7 = AVC)
        payload[0] = if (isKeyframe) 0x17.toByte() else 0x27.toByte()
        // AVCPacketType (1 = AVC NALU)
        payload[1] = 1.toByte()
        // CompositionTime (3 bytes: 0)
        payload[2] = 0.toByte()
        payload[3] = 0.toByte()
        payload[4] = 0.toByte()
        
        var offset = 5
        for (nalu in nalus) {
            val len = nalu.size
            payload[offset] = ((len shr 24) and 0xFF).toByte()
            payload[offset + 1] = ((len shr 16) and 0xFF).toByte()
            payload[offset + 2] = ((len shr 8) and 0xFF).toByte()
            payload[offset + 3] = (len and 0xFF).toByte()
            offset += 4
            System.arraycopy(nalu, 0, payload, offset, len)
            offset += len
        }
        
        return RtmpPacket(
            type = 9, // Video tag type
            timestamp = timestampMs,
            payload = payload,
            isKeyframe = isKeyframe
        )
    }
}
