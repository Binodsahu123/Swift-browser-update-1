package com.swift.browser.videoengine.live

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class RtmpCommandSession {
    private var transactionId = 1.0
    private val pendingTransactions = mutableMapOf<Double, (Amf0Value?, Amf0Value?) -> Unit>()

    fun nextTransactionId(): Double {
        transactionId += 1.0
        return transactionId
    }

    /**
     * Serializes a "connect" command with an application tcUrl and optional options.
     */
    fun buildConnect(tcUrl: String, appName: String): RtmpMessage {
        val bos = ByteArrayOutputStream()
        
        // Command Name
        Amf0.serialize(bos, Amf0Value.String("connect"))
        // Transaction ID (always 1.0 for connect)
        Amf0.serialize(bos, Amf0Value.Number(1.0))
        
        // Command Object
        val commandObject = mapOf(
            "app" to Amf0Value.String(appName),
            "tcUrl" to Amf0Value.String(tcUrl),
            "fpad" to Amf0Value.Boolean(false),
            "capabilities" to Amf0Value.Number(15.0),
            "audioCodecs" to Amf0Value.Number(3191.0),
            "videoCodecs" to Amf0Value.Number(252.0),
            "videoFunction" to Amf0Value.Number(1.0)
        )
        Amf0.serialize(bos, Amf0Value.Object(commandObject))

        return RtmpMessage(
            type = 20, // AMF0 Command
            chunkStreamId = 3,
            messageStreamId = 0,
            timestamp = 0L,
            payload = bos.toByteArray()
        )
    }

    /**
     * Serializes a "createStream" command.
     */
    fun buildCreateStream(tid: Double): RtmpMessage {
        val bos = ByteArrayOutputStream()
        Amf0.serialize(bos, Amf0Value.String("createStream"))
        Amf0.serialize(bos, Amf0Value.Number(tid))
        Amf0.serialize(bos, Amf0Value.Null) // Command Object can be Null

        return RtmpMessage(
            type = 20, // AMF0 Command
            chunkStreamId = 3,
            messageStreamId = 0,
            timestamp = 0L,
            payload = bos.toByteArray()
        )
    }

    /**
     * Serializes a "publish" command.
     */
    fun buildPublish(streamKey: String, streamId: Int, tid: Double): RtmpMessage {
        val bos = ByteArrayOutputStream()
        Amf0.serialize(bos, Amf0Value.String("publish"))
        Amf0.serialize(bos, Amf0Value.Number(tid))
        Amf0.serialize(bos, Amf0Value.Null) // Command Object
        Amf0.serialize(bos, Amf0Value.String(streamKey))
        Amf0.serialize(bos, Amf0Value.String("live"))

        return RtmpMessage(
            type = 20, // AMF0 Command
            chunkStreamId = 3,
            messageStreamId = streamId,
            timestamp = 0L,
            payload = bos.toByteArray()
        )
    }

    /**
     * Registers a callback for a transaction.
     */
    fun registerTransaction(tid: Double, callback: (Amf0Value?, Amf0Value?) -> Unit) {
        pendingTransactions[tid] = callback
    }

    /**
     * Handles an incoming command response or onStatus message.
     */
    fun handleCommandResponse(message: RtmpMessage): CommandResult? {
        val bais = ByteArrayInputStream(message.payload)
        try {
            val cmdNameVal = Amf0.deserialize(bais) as? Amf0Value.String ?: return null
            val cmdName = cmdNameVal.value
            val tidVal = Amf0.deserialize(bais) as? Amf0Value.Number ?: return null
            val tid = tidVal.value

            val arg1 = if (bais.available() > 0) Amf0.deserialize(bais) else null
            val arg2 = if (bais.available() > 0) Amf0.deserialize(bais) else null

            if (cmdName == "_result") {
                val callback = pendingTransactions.remove(tid)
                callback?.invoke(arg1, arg2)
                return CommandResult.Result(tid, arg1, arg2)
            } else if (cmdName == "_error") {
                val callback = pendingTransactions.remove(tid)
                callback?.invoke(arg1, arg2)
                return CommandResult.Error(tid, arg1, arg2)
            } else if (cmdName == "onStatus") {
                // Info object contains code, level, description etc.
                val infoObj = (arg1 as? Amf0Value.Object) ?: (arg2 as? Amf0Value.Object)
                val code = (infoObj?.properties?.get("code") as? Amf0Value.String)?.value ?: ""
                val level = (infoObj?.properties?.get("level") as? Amf0Value.String)?.value ?: ""
                return CommandResult.Status(code, level)
            }
        } catch (e: Exception) {
            // Parser exception or non-standard payload
        }
        return null
    }

    sealed class CommandResult {
        data class Result(val tid: Double, val info: Amf0Value?, val result: Amf0Value?) : CommandResult()
        data class Error(val tid: Double, val info: Amf0Value?, val error: Amf0Value?) : CommandResult()
        data class Status(val code: String, val level: String) : CommandResult()
    }
}
