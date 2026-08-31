package com.swift.browser.videoengine.live

import java.io.ByteArrayOutputStream

object AvcConfigRecord {

    /**
     * Splits an Annex-B formatted byte stream (separated by 0x000001 or 0x00000001 start codes)
     * into separate NAL units.
     */
    fun splitAnnexB(data: ByteArray): List<ByteArray> {
        val result = mutableListOf<ByteArray>()
        var i = 0
        var lastStart = -1
        
        while (i <= data.size - 3) {
            if (i <= data.size - 4 &&
                data[i] == 0.toByte() && data[i + 1] == 0.toByte() &&
                data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte()
            ) {
                if (lastStart != -1) {
                    val end = i
                    // Trim trailing zeros from previous NAL if any
                    var actualEnd = end
                    while (actualEnd > lastStart && data[actualEnd - 1] == 0.toByte()) {
                        actualEnd--
                    }
                    if (actualEnd > lastStart) {
                        result.add(data.copyOfRange(lastStart, actualEnd))
                    }
                }
                i += 4
                lastStart = i
            } else if (data[i] == 0.toByte() && data[i + 1] == 0.toByte() && data[i + 2] == 1.toByte()) {
                if (lastStart != -1) {
                    val end = i
                    var actualEnd = end
                    while (actualEnd > lastStart && data[actualEnd - 1] == 0.toByte()) {
                        actualEnd--
                    }
                    if (actualEnd > lastStart) {
                        result.add(data.copyOfRange(lastStart, actualEnd))
                    }
                }
                i += 3
                lastStart = i
            } else {
                i++
            }
        }
        
        if (lastStart != -1 && lastStart < data.size) {
            result.add(data.copyOfRange(lastStart, data.size))
        }
        
        return result
    }

    /**
     * Generates the 5-byte or more AVCDecoderConfigurationRecord.
     * Expects split SPS and PPS packets.
     */
    fun generate(sps: ByteArray, pps: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        
        // 1. configurationVersion (1)
        bos.write(1)
        
        // 2. AVCProfileIndication (SPS[1])
        if (sps.size > 1) {
            bos.write(sps[1].toInt())
        } else {
            bos.write(66) // Default to Baseline profile
        }
        
        // 3. profile_compatibility (SPS[2])
        if (sps.size > 2) {
            bos.write(sps[2].toInt())
        } else {
            bos.write(0)
        }
        
        // 4. AVCLevelIndication (SPS[3])
        if (sps.size > 3) {
            bos.write(sps[3].toInt())
        } else {
            bos.write(31) // Level 3.1
        }
        
        // 5. lengthSizeMinusOne (0xFF -> 4-byte NALU lengths)
        bos.write(0xFF)
        
        // 6. numOfSequenceParameterSets (0xE1 -> 1 SPS, upper 3 bits are reserved '111')
        bos.write(0xE1)
        
        // 7. SPS length (2 bytes)
        bos.write((sps.size shr 8) and 0xFF)
        bos.write(sps.size and 0xFF)
        
        // 8. SPS data
        bos.write(sps)
        
        // 9. numOfPictureParameterSets (1)
        bos.write(1)
        
        // 10. PPS length (2 bytes)
        bos.write((pps.size shr 8) and 0xFF)
        bos.write(pps.size and 0xFF)
        
        // 11. PPS data
        bos.write(pps)
        
        return bos.toByteArray()
    }
}
