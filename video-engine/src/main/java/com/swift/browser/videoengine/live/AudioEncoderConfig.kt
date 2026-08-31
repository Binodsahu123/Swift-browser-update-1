package com.swift.browser.videoengine.live

import android.media.MediaCodecInfo

data class AudioEncoderConfig(
    val sampleRate: Int = 44100,
    val channels: Int = 2,
    val bitrate: Int = 128000,
    val profile: Int = MediaCodecInfo.CodecProfileLevel.AACObjectLC,
    val frameDurationMs: Int = 20
)
