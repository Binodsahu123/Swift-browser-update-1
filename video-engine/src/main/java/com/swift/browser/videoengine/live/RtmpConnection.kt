package com.swift.browser.videoengine.live

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

class RtmpConnection(
    private val host: String,
    private val port: Int,
    private val timeoutMs: Int = 10000,
    private val isSecure: Boolean = false
) {
    private var socket: Socket? = null
    private var isConnected = false
    
    private var chunkEncoder = RtmpChunkEncoder()
    private var chunkDecoder = RtmpChunkDecoder()

    private var bufferedInput: BufferedInputStream? = null
    private var bufferedOutput: BufferedOutputStream? = null

    @Synchronized
    fun connect() {
        if (isConnected) return

        val rawSocket = Socket()
        // Enable timeouts
        rawSocket.connect(InetSocketAddress(host, port), timeoutMs)
        rawSocket.soTimeout = timeoutMs
        rawSocket.tcpNoDelay = true

        val s = if (isSecure) {
            val sslSocketFactory = javax.net.ssl.SSLSocketFactory.getDefault() as javax.net.ssl.SSLSocketFactory
            val sslSocket = sslSocketFactory.createSocket(rawSocket, host, port, true) as javax.net.ssl.SSLSocket
            
            // Set up strict hostname verification on the TLS layer
            val sslParams = sslSocket.sslParameters
            sslParams.endpointIdentificationAlgorithm = "HTTPS"
            sslSocket.sslParameters = sslParams
            
            // Handshake will fail if cert is invalid or host doesn't match
            sslSocket.startHandshake()
            
            // Explicit manual verification for additional fail-safe security
            val session = sslSocket.session
            if (!javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier().verify(host, session)) {
                throw javax.net.ssl.SSLPeerUnverifiedException("Hostname verification failed for $host")
            }
            sslSocket
        } else {
            rawSocket
        }

        socket = s

        val outStream = BufferedOutputStream(s.getOutputStream(), 16384)
        val inStream = BufferedInputStream(s.getInputStream(), 16384)

        // Perform Handshake
        RtmpHandshake.perform(inStream, outStream)

        bufferedOutput = outStream
        bufferedInput = inStream
        isConnected = true
    }

    /**
     * Reads a single full RTMP message from the stream.
     * Thread-safe for a single reader thread.
     */
    fun readMessage(): RtmpMessage {
        val input = bufferedInput ?: throw IOException("Not connected")
        return chunkDecoder.decodeMessage(input)
    }

    /**
     * Writes a full RTMP message to the stream.
     * Synchronized to prevent interleaving of chunks from multiple writer threads.
     */
    @Synchronized
    fun writeMessage(message: RtmpMessage) {
        val output = bufferedOutput ?: throw IOException("Not connected")
        val bytes = chunkEncoder.encode(message)
        output.write(bytes)
        output.flush()
    }

    fun getTotalBytesRead(): Long = chunkDecoder.totalBytesRead

    /**
     * Updates the local active chunk size for outgoing messages.
     * Handled by sending a Set Chunk Size command (Type 1).
     */
    @Synchronized
    fun setWriteChunkSize(newSize: Int) {
        chunkEncoder.chunkSize = newSize
        
        val payload = ByteArray(4)
        payload[0] = ((newSize shr 24) and 0xFF).toByte()
        payload[1] = ((newSize shr 16) and 0xFF).toByte()
        payload[2] = ((newSize shr 8) and 0xFF).toByte()
        payload[3] = (newSize and 0xFF).toByte()

        val msg = RtmpMessage(
            type = 1, // Set Chunk Size
            chunkStreamId = 2,
            messageStreamId = 0,
            timestamp = 0L,
            payload = payload
        )
        writeMessage(msg)
    }

    /**
     * Updates the decoder's chunk size when receiving Set Chunk Size from the peer.
     */
    fun setReadChunkSize(newSize: Int) {
        chunkDecoder.chunkSize = newSize
    }

    @Synchronized
    fun close() {
        isConnected = false
        try {
            bufferedInput?.close()
        } catch (_: Exception) {}
        try {
            bufferedOutput?.close()
        } catch (_: Exception) {}
        try {
            socket?.close()
        } catch (_: Exception) {}
        
        bufferedInput = null
        bufferedOutput = null
        socket = null
    }

    fun isConnected(): Boolean {
        val s = socket
        return isConnected && s != null && !s.isClosed && s.isConnected
    }
}
