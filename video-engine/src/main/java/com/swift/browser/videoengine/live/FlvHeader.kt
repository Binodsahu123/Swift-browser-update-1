package com.swift.browser.videoengine.live

import java.io.ByteArrayOutputStream

object FlvHeader {
    private val SIGNATURE = byteArrayOf('F'.toByte(), 'L'.toByte(), 'V'.toByte())
    private const val VERSION: Byte = 1

    fun generate(hasAudio: Boolean, hasVideo: Boolean): ByteArray {
        val bos = ByteArrayOutputStream()
        
        // 1. Signature
        bos.write(SIGNATURE)
        
        // 2. Version
        bos.write(VERSION.toInt())
        
        // 3. Flags
        var flags = 0
        if (hasAudio) flags = flags or 0x04
        if (hasVideo) flags = flags or 0x01
        bos.write(flags)
        
        // 4. DataOffset (Length of FLV header, usually 9)
        bos.write(0)
        bos.write(0)
        bos.write(0)
        bos.write(9)
        
        return bos.toByteArray()
    }
}
