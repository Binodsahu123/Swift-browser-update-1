package com.swift.browser.videoengine.live

data class LiveStreamConfig(
    val streamUrl: String,
    val streamKey: String,
    val width: Int = 1280,
    val height: Int = 720,
    val fps: Int = 30,
    val videoBitrate: Int = 2500_000, // in bps
    val audioBitrate: Int = 128_000, // in bps
    val keyframeInterval: Int = 2, // seconds
    val audioSampleRate: Int = 44100, // Hz
    val audioChannels: Int = 2, // Stereo
    val rotation: Int = 0,
    val orientation: Int = 1 // 1 for portrait, 2 for landscape
) {
    override fun toString(): String {
        return "LiveStreamConfig(streamUrl='$streamUrl', streamKey='***MASKED***', width=$width, height=$height, fps=$fps, videoBitrate=$videoBitrate, audioBitrate=$audioBitrate, keyframeInterval=$keyframeInterval, audioSampleRate=$audioSampleRate, audioChannels=$audioChannels, rotation=$rotation, orientation=$orientation)"
    }
}
