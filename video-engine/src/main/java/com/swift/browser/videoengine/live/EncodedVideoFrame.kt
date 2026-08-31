package com.swift.browser.videoengine.live

import java.nio.ByteBuffer

data class EncodedVideoFrame(
    val data: ByteBuffer,
    val ptsUs: Long,
    val isKeyFrame: Boolean,
    val codecConfig: ByteBuffer? = null
)
