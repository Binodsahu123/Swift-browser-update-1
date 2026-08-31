package com.swift.browser.videoengine.live

import android.view.Surface
import java.nio.ByteBuffer
import android.media.MediaCodec

enum class LiveVideoSourceType {
    CAMERA,
    SCREEN
}

interface LiveVideoSource {
    fun start(surface: Surface)
    fun stop()
    fun isRunning(): Boolean
    val width: Int
    val height: Int
    val fps: Int
    val rotation: Int
    val sourceType: LiveVideoSourceType
}

interface VideoSource : LiveVideoSource {
    fun startCapture(config: LiveStreamConfig)
    fun stopCapture()
    fun setOutputSurface(surface: Surface?)
    fun release()
}

interface AudioSource {
    fun startCapture(config: LiveStreamConfig)
    fun stopCapture()
    fun setAudioDataListener(listener: (ByteArray, Int) -> Unit)
    fun release()
}

interface VideoEncoder {
    fun configure(config: LiveStreamConfig)
    fun start()
    fun stop()
    fun getInputSurface(): Surface?
    fun setEncodedVideoListener(listener: (ByteBuffer, MediaCodec.BufferInfo) -> Unit)
    fun release()
}

interface AudioEncoder {
    fun configure(config: LiveStreamConfig)
    fun start()
    fun stop()
    fun queueInputBuffer(data: ByteArray, size: Int, presentationTimeUs: Long)
    fun setEncodedAudioListener(listener: (ByteBuffer, MediaCodec.BufferInfo) -> Unit)
    fun release()
}

interface StreamMuxer {
    fun configure(config: LiveStreamConfig)
    fun start()
    fun stop()
    fun writeVideoSample(buffer: ByteBuffer, info: MediaCodec.BufferInfo)
    fun writeAudioSample(buffer: ByteBuffer, info: MediaCodec.BufferInfo)
    fun setMuxedOutputListener(listener: (ByteArray, Int, Long) -> Unit)
    fun setRtmpPacketListener(listener: (RtmpPacket) -> Unit)
    fun release()
}

interface RtmpTransport {
    fun connect(url: String, key: String): Boolean
    fun sendMuxedData(data: ByteArray, length: Int, timestampUs: Long): Boolean
    fun sendRtmpPacket(packet: RtmpPacket): Boolean
    fun disconnect()
    fun getStats(): LiveStreamStats
    fun release()
}
