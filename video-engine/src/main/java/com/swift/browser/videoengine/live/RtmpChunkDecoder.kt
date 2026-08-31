package com.swift.browser.videoengine.live

import java.io.ByteArrayOutputStream
import java.io.InputStream

class RtmpChunkDecoder(initialChunkSize: Int = 128) {
    var chunkSize = initialChunkSize
    var totalBytesRead = 0L

    private fun readByte(inputStream: InputStream): Int {
        val b = inputStream.read()
        if (b != -1) totalBytesRead++
        return b
    }

    private fun readBytes(inputStream: InputStream, buffer: ByteArray, offset: Int, length: Int): Int {
        val n = inputStream.read(buffer, offset, length)
        if (n > 0) totalBytesRead += n
        return n
    }

    // Store state per Chunk Stream ID (CSID)
    private class ChannelState(
        var lastType: Int = 0,
        var lastLength: Int = 0,
        var lastMessageStreamId: Int = 0,
        var lastTimestamp: Long = 0L,
        var lastTimestampDelta: Long = 0L,
        var hasExtendedTimestamp: Boolean = false,
        var currentPayload: ByteArrayOutputStream = ByteArrayOutputStream()
    )

    private val channels = mutableMapOf<Int, ChannelState>()

    /**
     * Reads a single complete RTMP message from the input stream.
     * Blocks or reads until one full message is reconstructed.
     */
    fun decodeMessage(inputStream: InputStream): RtmpMessage {
        while (true) {
            // 1. Read Basic Header
            val firstByte = readByte(inputStream)
            if (firstByte == -1) throw IllegalStateException("EOF reading basic header")

            val fmt = (firstByte shr 6) and 0x03
            val csidBits = firstByte and 0x3F
            val csid = when (csidBits) {
                0 -> {
                    val secondByte = readByte(inputStream)
                    if (secondByte == -1) throw IllegalStateException("EOF reading basic header CSID extension")
                    secondByte + 64
                }
                1 -> {
                    val low = readByte(inputStream)
                    val high = readByte(inputStream)
                    if (low == -1 || high == -1) throw IllegalStateException("EOF reading basic header 3-byte CSID")
                    (high shl 8) or low or 64
                }
                else -> csidBits
            }

            val state = channels.getOrPut(csid) { ChannelState() }

            // 2. Read Message Header
            var rawTimestamp = 0L
            if (fmt == 0) {
                // Fmt 0: 11 bytes
                val ts1 = readByte(inputStream)
                val ts2 = readByte(inputStream)
                val ts3 = readByte(inputStream)
                val len1 = readByte(inputStream)
                val len2 = readByte(inputStream)
                val len3 = readByte(inputStream)
                val typeId = readByte(inputStream)
                val msid1 = readByte(inputStream)
                val msid2 = readByte(inputStream)
                val msid3 = readByte(inputStream)
                val msid4 = readByte(inputStream)

                if (msid4 == -1) throw IllegalStateException("EOF reading Fmt 0 message header")

                rawTimestamp = ((ts1 shl 16) or (ts2 shl 8) or ts3).toLong()
                state.lastLength = (len1 shl 16) or (len2 shl 8) or len3
                state.lastType = typeId
                state.lastMessageStreamId = msid1 or (msid2 shl 8) or (msid3 shl 16) or (msid4 shl 24)
                state.hasExtendedTimestamp = (rawTimestamp == 0xFFFFFFL)
                state.lastTimestamp = if (state.hasExtendedTimestamp) 0L else rawTimestamp
                state.lastTimestampDelta = 0L
            } else if (fmt == 1) {
                // Fmt 1: 7 bytes
                val ts1 = readByte(inputStream)
                val ts2 = readByte(inputStream)
                val ts3 = readByte(inputStream)
                val len1 = readByte(inputStream)
                val len2 = readByte(inputStream)
                val len3 = readByte(inputStream)
                val typeId = readByte(inputStream)

                if (typeId == -1) throw IllegalStateException("EOF reading Fmt 1 message header")

                rawTimestamp = ((ts1 shl 16) or (ts2 shl 8) or ts3).toLong()
                state.lastLength = (len1 shl 16) or (len2 shl 8) or len3
                state.lastType = typeId
                state.hasExtendedTimestamp = (rawTimestamp == 0xFFFFFFL)
                state.lastTimestampDelta = if (state.hasExtendedTimestamp) 0L else rawTimestamp
                state.lastTimestamp += state.lastTimestampDelta
            } else if (fmt == 2) {
                // Fmt 2: 3 bytes
                val ts1 = readByte(inputStream)
                val ts2 = readByte(inputStream)
                val ts3 = readByte(inputStream)

                if (ts3 == -1) throw IllegalStateException("EOF reading Fmt 2 message header")

                rawTimestamp = ((ts1 shl 16) or (ts2 shl 8) or ts3).toLong()
                state.hasExtendedTimestamp = (rawTimestamp == 0xFFFFFFL)
                state.lastTimestampDelta = if (state.hasExtendedTimestamp) 0L else rawTimestamp
                state.lastTimestamp += state.lastTimestampDelta
            } else {
                // Fmt 3: 0 bytes. Reuse previous headers, and if last was extended timestamp, we reuse delta
                if (state.lastTimestampDelta > 0L) {
                    state.lastTimestamp += state.lastTimestampDelta
                }
            }

            // 3. Extended Timestamp (4 bytes)
            if (state.hasExtendedTimestamp) {
                val et1 = readByte(inputStream)
                val et2 = readByte(inputStream)
                val et3 = readByte(inputStream)
                val et4 = readByte(inputStream)
                if (et4 == -1) throw IllegalStateException("EOF reading extended timestamp")
                val extendedTs = ((et1.toLong() and 0xFFL) shl 24) or
                        ((et2.toLong() and 0xFFL) shl 16) or
                        ((et3.toLong() and 0xFFL) shl 8) or
                        (et4.toLong() and 0xFFL)

                if (fmt == 0) {
                    state.lastTimestamp = extendedTs
                } else {
                    state.lastTimestampDelta = extendedTs
                    // Re-calculate lastTimestamp
                    state.lastTimestamp += state.lastTimestampDelta
                }
            }

            // 4. Read Payload chunk
            val currentSize = state.currentPayload.size()
            val remainingToRead = state.lastLength - currentSize
            val sliceSize = minOf(chunkSize, remainingToRead)

            val sliceBytes = ByteArray(sliceSize)
            var bytesRead = 0
            while (bytesRead < sliceSize) {
                val n = readBytes(inputStream, sliceBytes, bytesRead, sliceSize - bytesRead)
                if (n == -1) throw IllegalStateException("EOF reading payload slice")
                bytesRead += n
            }

            state.currentPayload.write(sliceBytes)

            // 5. If we have a fully-reassembled RTMP message, return it!
            if (state.currentPayload.size() == state.lastLength) {
                val payloadBytes = state.currentPayload.toByteArray()
                state.currentPayload.reset()
                return RtmpMessage(
                    type = state.lastType,
                    chunkStreamId = csid,
                    messageStreamId = state.lastMessageStreamId,
                    timestamp = state.lastTimestamp,
                    payload = payloadBytes
                )
            }
        }
    }
}
