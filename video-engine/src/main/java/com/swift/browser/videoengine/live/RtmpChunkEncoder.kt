package com.swift.browser.videoengine.live

import java.io.ByteArrayOutputStream

class RtmpChunkEncoder(initialChunkSize: Int = 128) {
    var chunkSize = initialChunkSize

    fun encode(message: RtmpMessage): ByteArray {
        val bos = ByteArrayOutputStream()
        val payload = message.payload
        val totalLength = payload.size
        var bytesSent = 0

        // Determine if extended timestamp is required
        val isExtended = message.timestamp >= 0xFFFFFFL
        val tsField = if (isExtended) 0xFFFFFF else message.timestamp.toInt()

        while (bytesSent < totalLength) {
            val isFirstChunk = bytesSent == 0
            val fmt = if (isFirstChunk) 0 else 3
            val csid = message.chunkStreamId

            // 1. Basic Header (1 byte for CSID < 64)
            val basicHeader = (fmt shl 6) or (csid and 0x3F)
            bos.write(basicHeader)

            // 2. Message Header (only for Fmt 0)
            if (isFirstChunk) {
                // Timestamp (3 bytes)
                bos.write((tsField shr 16) and 0xFF)
                bos.write((tsField shr 8) and 0xFF)
                bos.write(tsField and 0xFF)

                // Message Length (3 bytes)
                bos.write((totalLength shr 16) and 0xFF)
                bos.write((totalLength shr 8) and 0xFF)
                bos.write(totalLength and 0xFF)

                // Message Type ID (1 byte)
                bos.write(message.type and 0xFF)

                // Message Stream ID (4 bytes, Little Endian!)
                val msid = message.messageStreamId
                bos.write(msid and 0xFF)
                bos.write((msid shr 8) and 0xFF)
                bos.write((msid shr 16) and 0xFF)
                bos.write((msid shr 24) and 0xFF)
            }

            // 3. Extended Timestamp (4 bytes, Big Endian)
            if (isExtended) {
                val ts = message.timestamp
                bos.write(((ts shr 24) and 0xFF).toInt())
                bos.write(((ts shr 16) and 0xFF).toInt())
                bos.write(((ts shr 8) and 0xFF).toInt())
                bos.write((ts and 0xFF).toInt())
            }

            // 4. Payload slice
            val sliceSize = minOf(chunkSize, totalLength - bytesSent)
            bos.write(payload, bytesSent, sliceSize)
            bytesSent += sliceSize
        }

        return bos.toByteArray()
    }
}
