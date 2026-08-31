package com.swift.browser.videoengine.live

object AudioTag {

    /**
     * Generates an FLV Audio Sequence Header Tag (AACPacketType = 0).
     */
    fun createSequenceHeader(aacConfigRecord: ByteArray, timestampMs: Long, channels: Int): RtmpPacket {
        val payload = ByteArray(2 + aacConfigRecord.size)
        payload[0] = getSoundHeader(channels)
        payload[1] = 0 // AACPacketType = 0 (Sequence Header)
        
        System.arraycopy(aacConfigRecord, 0, payload, 2, aacConfigRecord.size)
        
        return RtmpPacket(
            type = 8, // Audio tag type
            timestamp = timestampMs,
            payload = payload,
            isKeyframe = false
        )
    }

    /**
     * Generates a standard Audio Tag containing AAC raw audio frame data (AACPacketType = 1).
     */
    fun createAudioTag(rawAacFrame: ByteArray, timestampMs: Long, channels: Int): RtmpPacket {
        val payload = ByteArray(2 + rawAacFrame.size)
        payload[0] = getSoundHeader(channels)
        payload[1] = 1 // AACPacketType = 1 (Raw AAC audio frame)
        
        System.arraycopy(rawAacFrame, 0, payload, 2, rawAacFrame.size)
        
        return RtmpPacket(
            type = 8, // Audio tag type
            timestamp = timestampMs,
            payload = payload,
            isKeyframe = false
        )
    }

    private fun getSoundHeader(channels: Int): Byte {
        val soundFormat = 10 // AAC
        val soundRate = 3 // 44 kHz
        val soundSize = 1 // 16-bit
        val soundType = if (channels == 1) 0 else 1 // Mono (0) vs Stereo (1)
        return ((soundFormat shl 4) or (soundRate shl 2) or (soundSize shl 1) or soundType).toByte()
    }
}
