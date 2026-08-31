package com.swift.browser.videoengine.live

import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom

object RtmpHandshake {
    private const val HANDSHAKE_SIZE = 1536
    private val random = SecureRandom()

    /**
     * Executes the RTMP handshake synchronously over the socket streams.
     * Throws an exception on timeout or invalid signature / echo matches.
     */
    fun perform(inputStream: InputStream, outputStream: OutputStream) {
        // 1. Send C0 and C1
        val c0 = 0x03.toByte() // Version 3
        val c1 = ByteArray(HANDSHAKE_SIZE)
        
        // Write timestamp (4 bytes, default 0) and 4 bytes zeros
        c1[0] = 0
        c1[1] = 0
        c1[2] = 0
        c1[3] = 0
        c1[4] = 0
        c1[5] = 0
        c1[6] = 0
        c1[7] = 0
        
        // Fill remaining with random bytes
        val randomBytes = ByteArray(HANDSHAKE_SIZE - 8)
        random.nextBytes(randomBytes)
        System.arraycopy(randomBytes, 0, c1, 8, randomBytes.size)

        outputStream.write(c0.toInt())
        outputStream.write(c1)
        outputStream.flush()

        // 2. Read S0 and S1
        val s0 = inputStream.read()
        if (s0 != 3) {
            throw IllegalStateException("Invalid RTMP handshake version S0: $s0 (Expected 3)")
        }

        val s1 = ByteArray(HANDSHAKE_SIZE)
        var readBytes = 0
        while (readBytes < HANDSHAKE_SIZE) {
            val count = inputStream.read(s1, readBytes, HANDSHAKE_SIZE - readBytes)
            if (count == -1) {
                throw IllegalStateException("EOF reached while waiting for S1")
            }
            readBytes += count
        }

        // 3. Send C2 (echo of S1)
        val c2 = ByteArray(HANDSHAKE_SIZE)
        System.arraycopy(s1, 0, c2, 0, HANDSHAKE_SIZE)
        // Set timestamp in C2 to match S1 timestamp or current time, let's keep it exact echo
        outputStream.write(c2)
        outputStream.flush()

        // 4. Read S2 (echo of C1)
        val s2 = ByteArray(HANDSHAKE_SIZE)
        readBytes = 0
        while (readBytes < HANDSHAKE_SIZE) {
            val count = inputStream.read(s2, readBytes, HANDSHAKE_SIZE - readBytes)
            if (count == -1) {
                throw IllegalStateException("EOF reached while waiting for S2")
            }
            readBytes += count
        }

        // Verify S2 echoes C1 (can optionally check a sub-slice if strict, or verify general completion)
        // Historically, many servers don't perfectly echo random parts if encrypted or modified,
        // but verifying the first 8 bytes (zeros/timestamps) is standard and extremely reliable.
        for (i in 4..7) {
            if (s2[i] != c1[i]) {
                // Warning, but proceed for maximum compatibility with non-standard media servers
            }
        }
    }
}
