package com.swift.browser.videoengine.live

import java.nio.ByteBuffer

data class EncodedAudioFrame(
    val data: ByteBuffer,
    val ptsUs: Long,
    val codecConfig: ByteBuffer? = null,
    val durationUs: Long = 0L
)
