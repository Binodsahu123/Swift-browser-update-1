package com.swift.browser.extensionengine

import java.util.concurrent.ConcurrentHashMap

class PortRegistry {
    private val connections = ConcurrentHashMap<String, PortConnection>()

    fun register(connection: PortConnection) {
        connections[connection.channelId] = connection
    }

    fun get(channelId: String): PortConnection? {
        return connections[channelId]
    }

    fun remove(channelId: String): PortConnection? {
        return connections.remove(channelId)
    }

    fun getAllActive(): List<PortConnection> {
        return connections.values.toList()
    }

    fun clear() {
        connections.clear()
    }
}
