package com.swift.browser.videoengine.live

object AacConfigRecord {

    private val SAMPLING_FREQUENCY_MAP = mapOf(
        96000 to 0,
        88200 to 1,
        64000 to 2,
        48000 to 3,
        44100 to 4,
        32000 to 5,
        24000 to 6,
        22050 to 7,
        16000 to 8,
        12000 to 9,
        11025 to 10,
        8000  to 11,
        7350  to 12
    )

    /**
     * Generates a 2-byte AudioSpecificConfig payload for AAC-LC.
     */
    fun generate(sampleRate: Int, channels: Int, profile: Int = 2): ByteArray {
        val freqIndex = SAMPLING_FREQUENCY_MAP[sampleRate] ?: 4 // Default 44100 Hz
        val channelConfig = when (channels) {
            1 -> 1
            2 -> 2
            3 -> 3
            4 -> 4
            5 -> 5
            6 -> 6
            8 -> 7
            else -> 2 // Default stereo
        }

        // Profile is usually 2 (AAC-LC)
        // 5 bits: profile
        // 4 bits: frequency index
        // 4 bits: channel config
        // 3 bits: padding
        val configValue = ((profile and 0x1F) shl 11) or
                ((freqIndex and 0x0F) shl 7) or
                ((channelConfig and 0x0F) shl 3)

        return byteArrayOf(
            ((configValue shr 8) and 0xFF).toByte(),
            (configValue and 0xFF).toByte()
        )
    }
}
