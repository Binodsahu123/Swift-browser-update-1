package com.swift.browser.videoengine.live

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class AacAudioEncoder : AudioEncoder {

    companion object {
        private const val TAG = "AacAudioEncoder"
        private const val MIME_TYPE = "audio/mp4a-latm"

        fun reportSupportedAudioCodecs(): String {
            val sb = StringBuilder()
            try {
                val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
                val codecs = list.codecInfos
                val aacEncoders = codecs.filter { info ->
                    info.isEncoder && info.supportedTypes.contains(MIME_TYPE)
                }

                sb.append("=== SUPPORTED AAC AUDIO ENCODERS ===\n")
                if (aacEncoders.isEmpty()) {
                    sb.append("No native AAC encoders found on this device.\n")
                } else {
                    for (info in aacEncoders) {
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
                            val audioCaps = caps.audioCapabilities
                            if (audioCaps != null) {
                                sb.append("  Supported Bitrate Range: [${audioCaps.bitrateRange.lower} - ${audioCaps.bitrateRange.upper}] bps\n")
                                sb.append("  Supported Sample Rates: ${audioCaps.supportedSampleRates?.joinToString { "$it Hz" }}\n")
                            }
                            val profiles = caps.profileLevels
                            if (profiles != null && profiles.isNotEmpty()) {
                                sb.append("  Supported Profiles: ${profiles.joinToString { "Profile ${it.profile}" }}\n")
                            }
                        } catch (e: Exception) {
                            sb.append("  Error reading capabilities: ${e.message}\n")
                        }
                        sb.append("\n")
                    }
                }
            } catch (t: Throwable) {
                sb.append("Error querying audio media codec capabilities: ${t.message}\n")
            }
            val result = sb.toString()
            Log.i(TAG, result)
            return result
        }
    }

    private var mediaCodec: MediaCodec? = null
    private var activeConfig: AudioEncoderConfig? = null
    private var codecName: String = "Unknown"
    
    private val isRunning = AtomicBoolean(false)
    private val isConfigured = AtomicBoolean(false)

    // Listeners
    private var rawListener: ((ByteBuffer, MediaCodec.BufferInfo) -> Unit)? = null
    private var frameListener: ((EncodedAudioFrame) -> Unit)? = null

    // Codec Config Cache
    private var codecConfigBuffer: ByteBuffer? = null

    // Handler thread for async callbacks
    private var callbackThread: HandlerThread? = null
    private var callbackHandler: Handler? = null

    // Asynchronous queue management
    private val inputLock = Any()
    private val inputQueue = ConcurrentLinkedQueue<PendingInput>()
    private val availableInputIndices = ConcurrentLinkedQueue<Int>()

    // Latency tracking
    private val ptsToEnqueueTimeMap = ConcurrentHashMap<Long, Long>()

    // Diagnostics Counters (thread-safe)
    private val samplesReceived = AtomicLong(0)
    private val samplesEncoded = AtomicLong(0)
    private val framesEncoded = AtomicLong(0)
    private val bytesEncoded = AtomicLong(0)
    private val droppedSamples = AtomicLong(0)
    private val encoderErrors = AtomicLong(0)
    private val totalLatencyMs = AtomicLong(0)
    private val ptsErrors = AtomicLong(0)
    private val lastOutputPtsUs = AtomicLong(-1L)

    private class PendingInput(
        val data: ByteArray,
        val presentationTimeUs: Long,
        val isEos: Boolean = false
    )

    fun setEncodedFrameListener(listener: (EncodedAudioFrame) -> Unit) {
        this.frameListener = listener
    }

    override fun setEncodedAudioListener(listener: (ByteBuffer, MediaCodec.BufferInfo) -> Unit) {
        this.rawListener = listener
    }

    override fun configure(config: LiveStreamConfig) {
        val audioConfig = AudioEncoderConfig(
            sampleRate = config.audioSampleRate,
            channels = config.audioChannels,
            bitrate = config.audioBitrate
        )
        configure(audioConfig)
    }

    fun configure(config: AudioEncoderConfig) {
        synchronized(this) {
            if (isRunning.get()) {
                Log.w(TAG, "Cannot configure while encoder is running. Stopping first.")
                stop()
            }

            Log.i(TAG, "Configuring AAC Audio Encoder with: $config")
            
            // Query for matching encoder
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val encoderInfo = list.codecInfos.firstOrNull { info ->
                info.isEncoder && info.supportedTypes.contains(MIME_TYPE)
            }

            if (encoderInfo == null) {
                val err = "No AAC audio encoder found on this device."
                Log.e(TAG, err)
                throw IllegalStateException(err)
            }

            this.codecName = encoderInfo.name
            this.activeConfig = config
            this.isConfigured.set(true)
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

                val format = MediaFormat.createAudioFormat(MIME_TYPE, config.sampleRate, config.channels).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, config.profile)
                    setInteger(MediaFormat.KEY_BIT_RATE, config.bitrate)
                    setInteger(MediaFormat.KEY_SAMPLE_RATE, config.sampleRate)
                    setInteger(MediaFormat.KEY_CHANNEL_COUNT, config.channels)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 10 * config.channels)
                }

                Log.d(TAG, "Configuring MediaCodec with MediaFormat: $format")

                // Start async callback thread
                callbackThread = HandlerThread("AacEncoderCallbackThread").apply { start() }
                callbackHandler = Handler(callbackThread!!.looper)

                inputQueue.clear()
                availableInputIndices.clear()
                ptsToEnqueueTimeMap.clear()

                mediaCodec?.setCallback(object : MediaCodec.Callback() {
                    override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                        if (!isRunning.get()) return
                        synchronized(inputLock) {
                            val pending = inputQueue.poll()
                            if (pending != null) {
                                try {
                                    if (pending.isEos) {
                                        codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                        Log.i(TAG, "Signalled End of Stream to audio codec")
                                    } else {
                                        val inputBuffer = codec.getInputBuffer(index)
                                        if (inputBuffer != null) {
                                            inputBuffer.clear()
                                            val bytesToCopy = minOf(pending.data.size, inputBuffer.remaining())
                                            inputBuffer.put(pending.data, 0, bytesToCopy)
                                            codec.queueInputBuffer(index, 0, bytesToCopy, pending.presentationTimeUs, 0)
                                            val ch = activeConfig?.channels ?: 2
                                            samplesEncoded.addAndGet(bytesToCopy.toLong() / (2 * maxOf(1, ch)))
                                        } else {
                                            droppedSamples.addAndGet(pending.data.size.toLong() / 2)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error processing queued pending input buffer: ${e.message}", e)
                                    encoderErrors.incrementAndGet()
                                }
                            } else {
                                availableInputIndices.add(index)
                            }
                        }
                    }

                    override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                        if (!isRunning.get()) return
                        try {
                            val outputBuffer = codec.getOutputBuffer(index)
                            if (outputBuffer != null) {
                                if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                    Log.i(TAG, "Received Codec Config buffer of size ${info.size}")
                                    val configData = ByteBuffer.allocate(info.size)
                                    outputBuffer.position(info.offset)
                                    outputBuffer.limit(info.offset + info.size)
                                    configData.put(outputBuffer)
                                    configData.flip()
                                    codecConfigBuffer = configData
                                } else {
                                    // Handle precise latency tracking
                                    val enqueueTimeNs = ptsToEnqueueTimeMap.remove(info.presentationTimeUs)
                                    if (enqueueTimeNs != null) {
                                        val latencyNs = System.nanoTime() - enqueueTimeNs
                                        totalLatencyMs.addAndGet(latencyNs / 1000000L)
                                    }

                                    // Track bytes encoded and frames encoded
                                    bytesEncoded.addAndGet(info.size.toLong())
                                    framesEncoded.incrementAndGet()

                                    // Enforce monotonic PTS
                                    var currentPtsUs = info.presentationTimeUs
                                    val lastPts = lastOutputPtsUs.get()
                                    if (lastPts != -1L && currentPtsUs <= lastPts) {
                                        ptsErrors.incrementAndGet()
                                        currentPtsUs = lastPts + 1000L // Ensure monotonic increment
                                        info.presentationTimeUs = currentPtsUs
                                    }
                                    lastOutputPtsUs.set(currentPtsUs)

                                    // Extract data buffer
                                    outputBuffer.position(info.offset)
                                    outputBuffer.limit(info.offset + info.size)
                                    
                                    val dataCopy = ByteBuffer.allocate(info.size)
                                    dataCopy.put(outputBuffer)
                                    dataCopy.flip()

                                    // Emit via listener interfaces
                                    rawListener?.invoke(dataCopy.duplicate(), info)

                                    val durationUs = if (config.sampleRate > 0) {
                                        (1024L * 1000000L) / config.sampleRate
                                    } else 0L

                                    val frame = EncodedAudioFrame(
                                        data = dataCopy,
                                        ptsUs = currentPtsUs,
                                        codecConfig = codecConfigBuffer,
                                        durationUs = durationUs
                                    )
                                    frameListener?.invoke(frame)
                                }
                            }
                            codec.releaseOutputBuffer(index, false)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error reading output buffer $index: ${e.message}", e)
                            encoderErrors.incrementAndGet()
                        }
                    }

                    override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                        Log.e(TAG, "MediaCodec onError: isTransient=${e.isTransient}, isRecoverable=${e.isRecoverable}", e)
                        encoderErrors.incrementAndGet()
                    }

                    override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                        Log.i(TAG, "MediaCodec output format changed: $format")
                        val csd = format.getByteBuffer("csd-0")
                        if (csd != null) {
                            val configData = ByteBuffer.allocate(csd.remaining())
                            configData.put(csd)
                            configData.flip()
                            codecConfigBuffer = configData
                        }
                    }
                }, callbackHandler)

                mediaCodec?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                mediaCodec?.start()

                isRunning.set(true)
                Log.i(TAG, "AAC Audio Encoder started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start AAC Audio Encoder: ${e.message}", e)
                throw e
            }
        }
    }

    override fun queueInputBuffer(data: ByteArray, size: Int, presentationTimeUs: Long) {
        if (!isRunning.get()) return
        val ch = activeConfig?.channels ?: 2
        samplesReceived.addAndGet(size.toLong() / (2 * maxOf(1, ch))) // 16-bit PCM (2 bytes per sample * channels)

        synchronized(inputLock) {
            ptsToEnqueueTimeMap[presentationTimeUs] = System.nanoTime()

            val index = availableInputIndices.poll()
            if (index != null) {
                try {
                    val codec = mediaCodec ?: return
                    val inputBuffer = codec.getInputBuffer(index)
                    if (inputBuffer != null) {
                        inputBuffer.clear()
                        val bytesToCopy = minOf(size, inputBuffer.remaining())
                        inputBuffer.put(data, 0, bytesToCopy)
                        codec.queueInputBuffer(index, 0, bytesToCopy, presentationTimeUs, 0)
                        samplesEncoded.addAndGet(bytesToCopy.toLong() / (2 * maxOf(1, ch)))
                    } else {
                        droppedSamples.addAndGet(size.toLong() / (2 * maxOf(1, ch)))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error queueing input buffer: ${e.message}", e)
                    encoderErrors.incrementAndGet()
                }
            } else {
                inputQueue.add(PendingInput(data.copyOf(size), presentationTimeUs))
                // Manage overflow safety
                if (inputQueue.size > 100) {
                    val oldest = inputQueue.poll()
                    if (oldest != null) {
                        droppedSamples.addAndGet(oldest.data.size.toLong() / 2)
                    }
                }
            }
        }
    }

    fun signalEndOfStream() {
        if (!isRunning.get()) return
        Log.i(TAG, "Signalling End of Stream requested")
        synchronized(inputLock) {
            val index = availableInputIndices.poll()
            if (index != null) {
                try {
                    mediaCodec?.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                } catch (e: Exception) {
                    Log.e(TAG, "Error queueing EOS input buffer: ${e.message}")
                    encoderErrors.incrementAndGet()
                }
            } else {
                inputQueue.add(PendingInput(ByteArray(0), 0, isEos = true))
            }
        }
    }

    override fun stop() {
        synchronized(this) {
            if (!isRunning.get()) return
            Log.i(TAG, "Stopping AAC Audio Encoder")
            isRunning.set(false)

            try {
                mediaCodec?.stop()
                mediaCodec?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping MediaCodec: ${e.message}")
            } finally {
                mediaCodec = null
            }

            callbackThread?.quitSafely()
            callbackThread = null
            callbackHandler = null

            synchronized(inputLock) {
                inputQueue.clear()
                availableInputIndices.clear()
            }
        }
    }

    override fun release() {
        stop()
        rawListener = null
        frameListener = null
    }

    // Diagnostics API
    fun getSamplesReceived(): Long = samplesReceived.get()
    fun getSamplesEncoded(): Long = samplesEncoded.get()
    fun getFramesEncoded(): Long = framesEncoded.get()
    fun getBytesEncoded(): Long = bytesEncoded.get()
    fun getAudioBitrate(): Int = activeConfig?.bitrate ?: 0
    fun getSampleRate(): Int = activeConfig?.sampleRate ?: 0
    fun getChannels(): Int = activeConfig?.channels ?: 0
    fun getDroppedSamples(): Long = droppedSamples.get()
    fun getEncoderErrors(): Long = encoderErrors.get()
    fun getPtsErrors(): Long = ptsErrors.get()
    fun getAverageLatencyMs(): Long {
        val totalEncoded = framesEncoded.get()
        return if (totalEncoded > 0) totalLatencyMs.get() / totalEncoded else 0
    }
}
