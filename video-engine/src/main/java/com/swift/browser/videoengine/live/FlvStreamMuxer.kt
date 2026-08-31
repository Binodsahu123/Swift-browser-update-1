package com.swift.browser.videoengine.live

import android.media.MediaCodec
import android.util.Log
import java.nio.ByteBuffer

class FlvStreamMuxer(
    private val maxQueueSize: Int = 100,
    private val dropPolicy: FlvMuxer.DropPolicy = FlvMuxer.DropPolicy.DROP_NON_KEYFRAMES
) : StreamMuxer {
    companion object {
        private const val TAG = "FlvStreamMuxer"
    }

    private val flvMuxer = FlvMuxer(maxQueueSize, dropPolicy)
    private var listener: ((ByteArray, Int, Long) -> Unit)? = null
    private var activeConfig: LiveStreamConfig? = null
    private var isMuxing = false

    override fun configure(config: LiveStreamConfig) {
        synchronized(this) {
            this.activeConfig = config
            flvMuxer.setAudioConfig(config.audioSampleRate, config.audioChannels)
        }
    }

    override fun start() {
        synchronized(this) {
            val config = activeConfig ?: throw IllegalStateException("Muxer not configured")
            val hasAudio = config.audioChannels > 0
            flvMuxer.startMuxing(hasAudio, true, config.width, config.height)
            isMuxing = true
            Log.i(TAG, "FlvStreamMuxer started")
        }
    }

    override fun stop() {
        synchronized(this) {
            isMuxing = false
            Log.i(TAG, "FlvStreamMuxer stopped")
        }
    }

    override fun writeVideoSample(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (!isMuxing) return
        val data = ByteArray(info.size)
        val originalPosition = buffer.position()
        buffer.get(data)
        buffer.position(originalPosition) // Restore position

        val isKeyframe = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
        flvMuxer.feedVideoFrame(data, info.presentationTimeUs, isKeyframe)
    }

    override fun writeAudioSample(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (!isMuxing) return
        val data = ByteArray(info.size)
        val originalPosition = buffer.position()
        buffer.get(data)
        buffer.position(originalPosition) // Restore position

        flvMuxer.feedAudioFrame(data, info.presentationTimeUs)
    }

    override fun setMuxedOutputListener(listener: (ByteArray, Int, Long) -> Unit) {
        this.listener = listener
        flvMuxer.setPacketListener { packet ->
            val tagBytes = flvMuxer.serializeToFlvTag(packet)
            listener(tagBytes, tagBytes.size, packet.timestamp * 1000L) // Convert ms timestamp back to Us for the listener
        }
    }

    override fun setRtmpPacketListener(listener: (RtmpPacket) -> Unit) {
        flvMuxer.setPacketListener(listener)
    }

    override fun release() {
        stop()
        listener = null
    }

    fun getDroppedFrames(): Long = flvMuxer.getDroppedFrames()
    fun getQueueSize(): Int = flvMuxer.getQueueSize()
}
