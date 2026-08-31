package com.swift.browser.extensionengine

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object TabIdMapper {
    private val uuidToProtoId = ConcurrentHashMap<String, Int>()
    private val protoIdToUuid = ConcurrentHashMap<Int, String>()
    private val counter = AtomicInteger(1)

    fun getIntId(uuid: String): Int {
        return uuidToProtoId.computeIfAbsent(uuid) {
            val id = counter.getAndIncrement()
            protoIdToUuid[id] = uuid
            id
        }
    }

    fun getUuid(intId: Int): String? {
        return protoIdToUuid[intId]
    }

    fun getUuidFromString(idStr: String): String {
        if (idStr.isBlank()) return ""
        val parsedInt = idStr.toIntOrNull()
        if (parsedInt != null) {
            return protoIdToUuid[parsedInt] ?: idStr
        }
        return idStr
    }
}
