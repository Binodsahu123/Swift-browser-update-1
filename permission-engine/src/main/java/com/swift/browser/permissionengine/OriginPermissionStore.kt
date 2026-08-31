package com.swift.browser.permissionengine

import java.util.concurrent.ConcurrentHashMap

class OriginPermissionStore {
    private val memoryStore = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()

    fun getMemoryState(origin: String, permissionType: String): String? {
        val clean = cleanOrigin(origin)
        return memoryStore[clean]?.get(permissionType.uppercase())
    }

    fun setMemoryState(origin: String, permissionType: String, state: String) {
        val clean = cleanOrigin(origin)
        memoryStore.getOrPut(clean) { ConcurrentHashMap() }[permissionType.uppercase()] = state.uppercase()
    }

    fun clearMemoryState(origin: String, permissionType: String) {
        val clean = cleanOrigin(origin)
        memoryStore[clean]?.remove(permissionType.uppercase())
        if (memoryStore[clean]?.isEmpty() == true) {
            memoryStore.remove(clean)
        }
    }

    fun getAllCached(): Map<String, Map<String, String>> {
        val result = mutableMapOf<String, Map<String, String>>()
        for ((origin, permMap) in memoryStore) {
            result[origin] = permMap.toMap()
        }
        return result
    }

    fun clearAll() {
        memoryStore.clear()
    }

    private fun cleanOrigin(origin: String): String {
        return OriginNormalizer.normalize(origin)
    }
}
