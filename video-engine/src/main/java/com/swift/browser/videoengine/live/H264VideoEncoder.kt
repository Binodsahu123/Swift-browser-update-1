package com.swift.browser.videoengine.live

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class H264VideoEncoder : VideoEncoder {

    companion object {
        private const val TAG = "H264VideoEncoder"
        private const val MIME_TYPE = "video/avc"

        fun reportSupportedCodecConfigurations(): String {
            val sb = StringBuilder()
            try {
                val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
                val codecs = list.codecInfos
                val avcEncoders = codecs.filter { info ->
                    info.isEncoder && info.supportedTypes.contains(MIME_TYPE)
                }

                sb.append("=== SUPPORTED H.264 / AVC ENCODERS ===\n")
                if (avcEncoders.isEmpty()) {
                    sb.append("No native H.264 / AVC video encoders found on this device.\n")
                } else {
                    for (info in avcEncoders) {
                        sb.append("Codec Name: ${info.name}\n")
                        val isHw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            info.isHardwareAccelerated
                        } else {
                            val name = info.name.lowercase()
                            !name.startsWith("omx.google.") && !name.startsWith("c2.android.")
                        }
                        sb.append("  Hardware Accelerated: $isHw\n")
                        
                        try {
                            val caps = info.getCapabilitiesForType(MIME_TYPE)
                            val videoCaps = caps.videoCapabilities
                            if (videoCaps != null) {
                                sb.append("  Supported Widths: [${videoCaps.supportedWidths.lower} - ${videoCaps.supportedWidths.upper}]\n")
                                sb.append("  Supported Heights: [${videoCaps.supportedHeights.lower} - ${videoCaps.supportedHeights.upper}]\n")
                                sb.append("  Supported Bitrates: [${videoCaps.bitrateRange.lower} - ${videoCaps.bitrateRange.upper}] bps\n")
                                sb.append("  Supported Framerates: [${videoCaps.supportedFrameRates.lower} - ${videoCaps.supportedFrameRates.upper}] fps\n")
                            }
                            
                            val colorFormats = caps.colorFormats
                            sb.append("  Supported Color Formats: ${colorFormats.joinToString { "0x${Integer.toHexString(it)} ($it)" }}\n")
                            
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                sb.append("  Max Supported Instances: ${caps.maxSupportedInstances}\n")
                            }
                        } catch (e: Exception) {
                            sb.append("  Error reading capabilities: ${e.message}\n")
                        }
                        sb.append("\n")
                    }
                }
            } catch (t: Throwable) {
                sb.append("Error querying media codec capabilities: ${t.message}\n")
            }
            val result = sb.toString()
            Log.i(TAG, result)
            return result
        }
    }

    private var mediaCodec: MediaCodec? = null
    private var inputSurface: Surface? = null
    
    private var activeConfig: VideoEncoderConfig? = null
    private var codecName: String = "Unknown"
    private val isRunning = AtomicBoolean(false)
    private val isConfigured = AtomicBoolean(false)

    // Listeners
    private var rawListener: ((ByteBuffer, MediaCodec.BufferInfo) -> Unit)? = null
    private var frameListener: ((EncodedVideoFrame) -> Unit)? = null

    // SPS/PPS cache
    private var spsPpsBuffer: ByteBuffer? = null

    // Handler thread for async callbacks
    private var callbackThread: HandlerThread? = null
    private var callbackHandler: Handler? = null

    // Diagnostic Counters (thread-safe)
    private val framesEncoded = AtomicLong(0)
    private val framesDropped = AtomicLong(0)
    private val totalEncodeDurationMs = AtomicLong(0)
    private val codecErrors = ConcurrentLinkedQueue<String>()

    fun setEncodedFrameListener(listener: (EncodedVideoFrame) -> Unit) {
        this.frameListener = listener
    }

    override fun setEncodedVideoListener(listener: (ByteBuffer, MediaCodec.BufferInfo) -> Unit) {
        this.rawListener = listener
    }

    override fun getInputSurface(): Surface? {
        return inputSurface
    }

    override fun configure(config: LiveStreamConfig) {
        val encoderConfig = VideoEncoderConfig(
            width = config.width,
            height = config.height,
            fps = config.fps,
            bitrate = config.videoBitrate,
            iFrameInterval = config.keyframeInterval
        )
        configure(encoderConfig)
    }

    fun configure(config: VideoEncoderConfig) {
        synchronized(this) {
            if (isRunning.get()) {
                Log.w(TAG, "Cannot configure while encoder is running. Stopping first.")
                stop()
            }

            Log.i(TAG, "Configuring H.264 Video Encoder with: $config")
            
            // 1. Select the best matching H.264 Codec Info with graceful fallback
            val codecInfo = selectCodecWithFallback(config)
            if (codecInfo == null) {
                val err = "No H.264/AVC hardware encoder found on this device."
                Log.e(TAG, err)
                codecErrors.add(err)
                throw IllegalStateException(err)
            }
            
            this.codecName = codecInfo.name
            Log.i(TAG, "Selected H.264 codec: $codecName")

            // 2. Adjust configurations based on codec capabilities (graceful fallback)
            val adjustedConfig = adjustConfigForCapabilities(codecInfo, config)
            this.activeConfig = adjustedConfig
            isConfigured.set(true)
        }
    }

    override fun start() {
        synchronized(this) {
            if (!isConfigured.get() || activeConfig == null) {
                throw IllegalStateException("Encoder is not configured. Call configure() before start().")
            }
            if (isRunning.get()) {
                Log.w(TAG, "Encoder is already running.")
                return
            }

            try {
                val config = activeConfig!!
                mediaCodec = MediaCodec.createByCodecName(codecName)

                val format = MediaFormat.createVideoFormat(MIME_TYPE, config.width, config.height).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, config.colorFormat)
                    setInteger(MediaFormat.KEY_BIT_RATE, config.bitrate)
                    setInteger(MediaFormat.KEY_FRAME_RATE, config.fps)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, config.iFrameInterval)
                    
                    // Apply profile and level if specified and supported
                    if (config.profile != null) {
                        setInteger(MediaFormat.KEY_PROFILE, config.profile)
                    }
                    if (config.level != null) {
                        setInteger(MediaFormat.KEY_LEVEL, config.level)
                    }
                }

                Log.d(TAG, "Configuring MediaCodec with MediaFormat: $format")

                // Start callback thread for async encoding
                callbackThread = HandlerThread("H264EncoderCallbackThread").apply { start() }
                callbackHandler = Handler(callbackThread!!.looper)

                mediaCodec?.setCallback(object : MediaCodec.Callback() {
                    override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                        // Using Input Surface, input buffers are handled by the system
                    }

                    override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                        if (!isRunning.get()) return
                        handleOutputBuffer(codec, index, info)
                    }

                    override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                        Log.e(TAG, "MediaCodec onError: isTransient=${e.isTransient}, isRecoverable=${e.isRecoverable}", e)
                        val errorMsg = "Codec Error: ${e.message} (Transient=${e.isTransient}, Recoverable=${e.isRecoverable})"
                        codecErrors.add(errorMsg)
                        if (!e.isTransient) {
                            reinitializeEncoder()
                        }
                    }

                    override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                        Log.i(TAG, "MediaCodec onOutputFormatChanged: $format")
                        extractSpsPpsFromFormat(format)
                    }
                }, callbackHandler)

                mediaCodec?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

                // Retrieve Input Surface if using COLOR_FormatSurface
                if (config.colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) {
                    inputSurface = mediaCodec?.createInputSurface()
                    Log.i(TAG, "Created codec input surface: $inputSurface")
                }

                mediaCodec?.start()
                isRunning.set(true)
                Log.i(TAG, "H.264 Video Encoder started successfully.")

            } catch (t: Throwable) {
                Log.e(TAG, "Error starting H.264 encoder", t)
                codecErrors.add("Start failed: ${t.message}")
                release()
                throw RuntimeException("Failed to start encoder: ${t.message}", t)
            }
        }
    }

    override fun stop() {
        synchronized(this) {
            if (!isRunning.get()) return
            Log.i(TAG, "Stopping H.264 Video Encoder")
            isRunning.set(false)

            try {
                mediaCodec?.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Exception stopping mediaCodec: ${e.message}")
            } finally {
                mediaCodec?.release()
                mediaCodec = null
            }

            try {
                inputSurface?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Exception releasing inputSurface: ${e.message}")
            } finally {
                inputSurface = null
            }

            callbackThread?.quitSafely()
            callbackThread = null
            callbackHandler = null

            Log.i(TAG, "H.264 Video Encoder stopped.")
        }
    }

    override fun release() {
        stop()
        isConfigured.set(false)
        activeConfig = null
        spsPpsBuffer = null
    }

    fun flush() {
        synchronized(this) {
            if (!isRunning.get()) return
            try {
                mediaCodec?.flush()
                Log.i(TAG, "Encoder flushed successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Error flushing encoder", e)
            }
        }
    }

    fun requestSyncFrame() {
        synchronized(this) {
            if (!isRunning.get()) return
            try {
                val params = Bundle().apply {
                    putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
                }
                mediaCodec?.setParameters(params)
                Log.d(TAG, "Requested IDR (Sync) key frame from MediaCodec")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to request key frame: ${e.message}")
            }
        }
    }

    private fun handleOutputBuffer(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
        val outputBuffer = codec.getOutputBuffer(index) ?: return
        
        try {
            val startTimeNs = System.nanoTime()

            // Handle Codec Config (SPS/PPS NAL units)
            if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                Log.d(TAG, "BUFFER_FLAG_CODEC_CONFIG received (${info.size} bytes)")
                val configData = ByteArray(info.size)
                outputBuffer.position(info.offset)
                outputBuffer.limit(info.offset + info.size)
                outputBuffer.get(configData)
                spsPpsBuffer = ByteBuffer.allocateDirect(info.size).apply {
                    put(configData)
                    flip()
                }
                // Also trigger raw listener for downstream muxers
                rawListener?.invoke(spsPpsBuffer!!.duplicate(), info)
                codec.releaseOutputBuffer(index, false)
                return
            }

            val isKeyFrame = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0

            // Copy output buffer payload safely
            val frameData = ByteArray(info.size)
            outputBuffer.position(info.offset)
            outputBuffer.limit(info.offset + info.size)
            outputBuffer.get(frameData)

            val frameBuffer = ByteBuffer.allocateDirect(info.size).apply {
                put(frameData)
                flip()
            }

            // Expose the encoded buffer to raw observers
            rawListener?.invoke(frameBuffer.duplicate(), info)

            // Emit structured EncodedVideoFrame
            val encodedFrame = EncodedVideoFrame(
                data = frameBuffer,
                ptsUs = info.presentationTimeUs,
                isKeyFrame = isKeyFrame,
                codecConfig = if (isKeyFrame) spsPpsBuffer?.duplicate() else null
            )
            frameListener?.invoke(encodedFrame)

            // Update diagnostics
            framesEncoded.incrementAndGet()
            val latencyUs = System.nanoTime() / 1000L - info.presentationTimeUs
            if (latencyUs > 0) {
                totalEncodeDurationMs.addAndGet(latencyUs / 1000L)
            }

            codec.releaseOutputBuffer(index, false)

        } catch (t: Throwable) {
            Log.e(TAG, "Error handling output buffer for index $index", t)
            framesDropped.incrementAndGet()
            try {
                codec.releaseOutputBuffer(index, false)
            } catch (_: Exception) {}
        }
    }

    private fun extractSpsPpsFromFormat(format: MediaFormat) {
        try {
            val sps = format.getByteBuffer("csd-0")
            val pps = format.getByteBuffer("csd-1")
            if (sps != null && pps != null) {
                val totalSize = sps.remaining() + pps.remaining()
                val merged = ByteBuffer.allocateDirect(totalSize)
                merged.put(sps)
                merged.put(pps)
                merged.flip()
                spsPpsBuffer = merged
                Log.i(TAG, "Successfully extracted SPS/PPS from output format change")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not extract SPS/PPS directly from MediaFormat: ${e.message}")
        }
    }

    private fun reinitializeEncoder() {
        Log.w(TAG, "Reinitializing encoder because of fatal or non-transient error...")
        synchronized(this) {
            val config = activeConfig ?: return
            try {
                stop()
                configure(config)
                start()
                Log.i(TAG, "Encoder successfully reinitialized and restarted.")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to reinitialize video encoder", t)
                codecErrors.add("Reinitialization failed: ${t.message}")
            }
        }
    }

    // --- GRACEFUL FALLBACK CAPABILITIES UTILS ---

    private fun selectCodecWithFallback(config: VideoEncoderConfig): MediaCodecInfo? {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val codecs = list.codecInfos

        // Priority 1: Hardware AVC encoder
        val hwCodec = codecs.firstOrNull { info ->
            info.isEncoder && 
            info.supportedTypes.contains(MIME_TYPE) && 
            isHardwareAccelerated(info)
        }
        if (hwCodec != null) return hwCodec

        // Priority 2: Any AVC encoder (including software fallbacks)
        val anyCodec = codecs.firstOrNull { info ->
            info.isEncoder && 
            info.supportedTypes.contains(MIME_TYPE)
        }
        return anyCodec
    }

    private fun isHardwareAccelerated(info: MediaCodecInfo): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.isHardwareAccelerated
        } else {
            val name = info.name.lowercase()
            !name.startsWith("omx.google.") && !name.startsWith("c2.android.")
        }
    }

    private fun adjustConfigForCapabilities(info: MediaCodecInfo, config: VideoEncoderConfig): VideoEncoderConfig {
        try {
            val capabilities = info.getCapabilitiesForType(MIME_TYPE)
            val videoCapabilities = capabilities.videoCapabilities ?: return config

            var width = config.width
            var height = config.height
            var fps = config.fps

            // Check size support
            if (!videoCapabilities.isSizeSupported(width, height)) {
                Log.w(TAG, "Requested resolution ${width}x${height} is not supported by $codecName.")
                
                // Fallback to closest matching aspect ratio sizes
                val widths = videoCapabilities.supportedWidths
                val heights = videoCapabilities.supportedHeights
                
                width = clamp(width, widths.lower, widths.upper)
                height = clamp(height, heights.lower, heights.upper)

                // Align to 16 pixels (standard H.264 macroblock alignment requirement)
                width = (width / 16) * 16
                height = (height / 16) * 16

                if (width == 0) width = 640
                if (height == 0) height = 360

                Log.i(TAG, "Fallback adjusted resolution: ${width}x${height}")
            }

            // Check FPS/Framerate capabilities for selected resolution
            val fpsRange = videoCapabilities.getSupportedFrameRatesFor(width, height)
            if (fps.toDouble() !in fpsRange) {
                val origFps = fps
                fps = clamp(fps, fpsRange.lower.toInt(), fpsRange.upper.toInt())
                Log.i(TAG, "Requested FPS $origFps was adjusted to $fps based on resolution capabilities.")
            }

            // Check color format support
            var colorFormat = config.colorFormat
            val supportedFormats = capabilities.colorFormats
            if (colorFormat !in supportedFormats) {
                colorFormat = when {
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface in supportedFormats -> 
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible in supportedFormats -> 
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
                    supportedFormats.isNotEmpty() -> 
                        supportedFormats[0]
                    else -> 
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
                }
                Log.i(TAG, "Fallback adjusted color format: $colorFormat")
            }

            return config.copy(
                width = width,
                height = height,
                fps = fps,
                colorFormat = colorFormat
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error checking capabilities, using requested config directly", e)
            return config
        }
    }

    private fun clamp(value: Int, min: Int, max: Int): Int {
        return Math.max(min, Math.min(max, value))
    }

    // --- DIAGNOSTICS ENDPOINT ---

    fun getDiagnostics(): EncoderDiagnostics {
        val totalEncoded = framesEncoded.get()
        val avgLatency = if (totalEncoded > 0) {
            totalEncodeDurationMs.get().toDouble() / totalEncoded
        } else {
            0.0
        }

        return EncoderDiagnostics(
            codecName = codecName,
            resolution = activeConfig?.let { "${it.width}x${it.height}" } ?: "Unconfigured",
            fps = activeConfig?.fps ?: 0,
            bitrate = activeConfig?.bitrate ?: 0,
            framesEncoded = totalEncoded,
            framesDropped = framesDropped.get(),
            averageEncodeLatencyMs = avgLatency,
            codecErrors = codecErrors.toList()
        )
    }

    data class EncoderDiagnostics(
        val codecName: String,
        val resolution: String,
        val fps: Int,
        val bitrate: Int,
        val framesEncoded: Long,
        val framesDropped: Long,
        val averageEncodeLatencyMs: Double,
        val codecErrors: List<String>
    )
}
