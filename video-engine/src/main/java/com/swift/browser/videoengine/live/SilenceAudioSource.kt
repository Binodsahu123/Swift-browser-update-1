package com.swift.browser.videoengine.live

class SilenceAudioSource : AudioSource {
    override fun setAudioDataListener(listener: (ByteArray, Int) -> Unit) {}
    override fun startCapture(config: LiveStreamConfig) {}
    override fun stopCapture() {}
    override fun release() {}
}
