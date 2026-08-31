package com.swift.browser.videoengine.live

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer

sealed class Amf0Value {
    data class Number(val value: Double) : Amf0Value()
    data class Boolean(val value: kotlin.Boolean) : Amf0Value()
    data class String(val value: kotlin.String) : Amf0Value()
    data class Object(val properties: Map<kotlin.String, Amf0Value>) : Amf0Value()
    object Null : Amf0Value()
    data class EcmaArray(val properties: Map<kotlin.String, Amf0Value>) : Amf0Value()
}

object Amf0 {
    private const val TYPE_NUMBER = 0x00
    private const val TYPE_BOOLEAN = 0x01
    private const val TYPE_STRING = 0x02
    private const val TYPE_OBJECT = 0x03
    private const val TYPE_NULL = 0x05
    private const val TYPE_ECMA_ARRAY = 0x08
    private const val TYPE_OBJECT_END = 0x09

    fun serialize(value: Amf0Value): ByteArray {
        val bos = ByteArrayOutputStream()
        serialize(bos, value)
        return bos.toByteArray()
    }

    fun serialize(bos: ByteArrayOutputStream, value: Amf0Value) {
        when (value) {
            is Amf0Value.Number -> {
                bos.write(TYPE_NUMBER)
                val buffer = ByteBuffer.allocate(8)
                buffer.putDouble(value.value)
                bos.write(buffer.array())
            }
            is Amf0Value.Boolean -> {
                bos.write(TYPE_BOOLEAN)
                bos.write(if (value.value) 1 else 0)
            }
            is Amf0Value.String -> {
                bos.write(TYPE_STRING)
                val bytes = value.value.toByteArray(Charsets.UTF_8)
                bos.write((bytes.size shr 8) and 0xFF)
                bos.write(bytes.size and 0xFF)
                bos.write(bytes)
            }
            is Amf0Value.Null -> {
                bos.write(TYPE_NULL)
            }
            is Amf0Value.Object -> {
                bos.write(TYPE_OBJECT)
                for ((k, v) in value.properties) {
                    writeStringRaw(bos, k)
                    serialize(bos, v)
                }
                // End marker: 0x00 0x00 0x09
                bos.write(0)
                bos.write(0)
                bos.write(TYPE_OBJECT_END)
            }
            is Amf0Value.EcmaArray -> {
                bos.write(TYPE_ECMA_ARRAY)
                val size = value.properties.size
                bos.write((size shr 24) and 0xFF)
                bos.write((size shr 16) and 0xFF)
                bos.write((size shr 8) and 0xFF)
                bos.write(size and 0xFF)
                for ((k, v) in value.properties) {
                    writeStringRaw(bos, k)
                    serialize(bos, v)
                }
                bos.write(0)
                bos.write(0)
                bos.write(TYPE_OBJECT_END)
            }
        }
    }

    private fun writeStringRaw(bos: ByteArrayOutputStream, str: String) {
        val bytes = str.toByteArray(Charsets.UTF_8)
        bos.write((bytes.size shr 8) and 0xFF)
        bos.write(bytes.size and 0xFF)
        bos.write(bytes)
    }

    fun deserialize(inputStream: InputStream): Amf0Value {
        val type = inputStream.read()
        if (type == -1) throw IllegalStateException("Unexpected EOF when reading AMF0 type")
        return deserialize(inputStream, type)
    }

    private fun deserialize(inputStream: InputStream, type: Int): Amf0Value {
        return when (type) {
            TYPE_NUMBER -> {
                val buf = readExact(inputStream, 8)
                val doubleVal = ByteBuffer.wrap(buf).double
                Amf0Value.Number(doubleVal)
            }
            TYPE_BOOLEAN -> {
                val b = inputStream.read()
                if (b == -1) throw IllegalStateException("EOF reading boolean")
                Amf0Value.Boolean(b != 0)
            }
            TYPE_STRING -> {
                val lenHigh = inputStream.read()
                val lenLow = inputStream.read()
                if (lenHigh == -1 || lenLow == -1) throw IllegalStateException("EOF reading string length")
                val len = (lenHigh shl 8) or lenLow
                val bytes = readExact(inputStream, len)
                Amf0Value.String(String(bytes, Charsets.UTF_8))
            }
            TYPE_NULL -> {
                Amf0Value.Null
            }
            TYPE_OBJECT -> {
                val props = mutableMapOf<String, Amf0Value>()
                while (true) {
                    val lenHigh = inputStream.read()
                    val lenLow = inputStream.read()
                    if (lenHigh == -1 || lenLow == -1) throw IllegalStateException("EOF reading object property key")
                    val len = (lenHigh shl 8) or lenLow
                    if (len == 0) {
                        val endMarker = inputStream.read()
                        if (endMarker == TYPE_OBJECT_END) {
                            break
                        } else {
                            throw IllegalStateException("Expected object end marker 9, got $endMarker")
                        }
                    }
                    val keyBytes = readExact(inputStream, len)
                    val key = String(keyBytes, Charsets.UTF_8)
                    props[key] = deserialize(inputStream)
                }
                Amf0Value.Object(props)
            }
            TYPE_ECMA_ARRAY -> {
                // Read 4 bytes count, then properties
                val countBuf = readExact(inputStream, 4)
                val props = mutableMapOf<String, Amf0Value>()
                while (true) {
                    val lenHigh = inputStream.read()
                    val lenLow = inputStream.read()
                    if (lenHigh == -1 || lenLow == -1) throw IllegalStateException("EOF reading ECMA array key")
                    val len = (lenHigh shl 8) or lenLow
                    if (len == 0) {
                        val endMarker = inputStream.read()
                        if (endMarker == TYPE_OBJECT_END) {
                            break
                        } else {
                            throw IllegalStateException("Expected object end marker 9, got $endMarker")
                        }
                    }
                    val keyBytes = readExact(inputStream, len)
                    val key = String(keyBytes, Charsets.UTF_8)
                    props[key] = deserialize(inputStream)
                }
                Amf0Value.EcmaArray(props)
            }
            else -> throw IllegalArgumentException("Unsupported AMF0 type: $type")
        }
    }

    private fun readExact(inputStream: InputStream, count: Int): ByteArray {
        val buf = ByteArray(count)
        var total = 0
        while (total < count) {
            val n = inputStream.read(buf, total, count - total)
            if (n == -1) throw IllegalStateException("EOF reached when expecting $count bytes")
            total += n
        }
        return buf
    }
}
