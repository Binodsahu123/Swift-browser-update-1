package com.swift.browser.videoengine.live

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

class MicrophoneAudioSource(private val context: Context) : AudioSource {
    companion object {
        private const val TAG = "MicrophoneAudioSource"
    }

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val isRecording = AtomicBoolean(false)
    private var listener: ((ByteArray, Int) -> Unit)? = null
    private var activeConfig: LiveStreamConfig? = null

    override fun setAudioDataListener(listener: (ByteArray, Int) -> Unit) {
        this.listener = listener
    }

    @SuppressLint("MissingPermission")
    override fun startCapture(config: LiveStreamConfig) {
        synchronized(this) {
            if (isRecording.get()) {
                Log.w(TAG, "Audio capture is already running")
                return
            }
            this.activeConfig = config

            // Guard access check via permission checks
            val hasPermission = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                Log.e(TAG, "Cannot start capture: RECORD_AUDIO permission not granted!")
                return
            }

            try {
                val sampleRate = config.audioSampleRate
                val channelConfig = if (config.audioChannels == 1) {
                    AudioFormat.CHANNEL_IN_MONO
                } else {
                    AudioFormat.CHANNEL_IN_STEREO
                }
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

                if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                    Log.e(TAG, "Invalid buffer size for AudioRecord: $minBufferSize")
                    return
                }

                val bufferSize = maxOf(minBufferSize, 4096 * config.audioChannels)
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord initialization failed")
                    audioRecord?.release()
                    audioRecord = null
                    return
                }

                audioRecord?.startRecording()
                isRecording.set(true)

                recordingThread = Thread({
                    val buffer = ByteArray(2048)
                    while (isRecording.get()) {
                        val record = audioRecord ?: break
                        val readBytes = record.read(buffer, 0, buffer.size)
                        if (readBytes > 0) {
                            listener?.invoke(buffer, readBytes)
                        } else if (readBytes < 0) {
                            Log.e(TAG, "Error reading from AudioRecord: $readBytes")
                        }
                    }
                }, "MicrophoneAudioCaptureThread").apply { start() }

                Log.i(TAG, "Microphone capture started successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Error starting microphone capture: ${e.message}", e)
                stopCapture()
            }
        }
    }

    override fun stopCapture() {
        synchronized(this) {
            Log.i(TAG, "Stopping Microphone capture")
            isRecording.set(false)
            try {
                recordingThread?.join(1000)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                recordingThread = null
            }

            try {
                audioRecord?.stop()
                audioRecord?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping/releasing AudioRecord: ${e.message}")
            } finally {
                audioRecord = null
            }
        }
    }

    override fun release() {
        stopCapture()
        listener = null
    }
}
