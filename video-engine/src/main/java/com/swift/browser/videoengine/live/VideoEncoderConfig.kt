package com.swift.browser.videoengine.live

data class VideoEncoderConfig(
    val width: Int = 1280,
    val height: Int = 720,
    val fps: Int = 30,
    val bitrate: Int = 2500_000, // in bps
    val iFrameInterval: Int = 2, // in seconds
    val colorFormat: Int = android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
    val profile: Int? = null,
    val level: Int? = null
)
