package com.swift.browser.extensionengine

import android.content.Context
import com.swift.browser.extensionengine.resources.ExtensionResourceResolver
import org.json.JSONObject
import org.json.JSONArray
import java.io.File

class StorageManager(
    private val db: ExtensionDatabase,
    private val context: Context? = null,
    private val registry: ExtensionRegistry? = null
) {

    private val storageDao = db.storageDao()

    // Isolated in-memory storage for normal mode "session" area: extensionId -> key -> value
    private val sessionStorageMap = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentHashMap<String, String>>()

    // Isolated in-memory storage for private browsing sessions: sessionId -> "extensionId:area" -> key -> value
    private val privateStorageMap = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentHashMap<String, String>>>()

    // Callback used to dispatch chrome.storage.onChanged events downstream.
    // Receives arguments: (extensionId, area, changes)
    var changeListener: ((String, String, JSONObject) -> Unit)? = null

    private fun checkAreaSupported(area: String) {
        if (area == "managed" || area == "sync" || area == "local" || area == "session") {
            return
        }
        throw IllegalArgumentException("UNSUPPORTED_BY_ORION")
    }

    private fun getManagedStorage(extensionId: String, keys: Any?): JSONObject {
        val ctx = context ?: throw IllegalArgumentException("MANAGED_STORAGE_UNAVAILABLE")
        val reg = registry ?: throw IllegalArgumentException("MANAGED_STORAGE_UNAVAILABLE")
        val ext = reg.getExtension(extensionId) ?: throw IllegalArgumentException("Extension not found")
        
        val manifest = JSONObject(ext.manifestJson)
        val storageObj = manifest.optJSONObject("storage")
        val schemaPath = storageObj?.optString("managed_schema")
        if (schemaPath.isNullOrBlank()) {
            throw IllegalArgumentException("MANAGED_STORAGE_UNAVAILABLE")
        }

        val extensionDir = ExtensionDirectoryResolver.getExtensionDir(ctx, extensionId, ext.name)
        val schemaFile = ExtensionDirectoryResolver.findFileCaseInsensitive(extensionDir, schemaPath)
        if (schemaFile == null || !schemaFile.exists()) {
            throw IllegalArgumentException("MANAGED_STORAGE_UNAVAILABLE")
        }

        try {
            PathSanitizer.verifyCanonicalContainment(extensionDir, schemaFile)
        } catch (e: Exception) {
            throw SecurityException("SecurityError: Path traversal detected in managed schema path.")
        }

        val schemaContent = schemaFile.readText(Charsets.UTF_8)
        val schemaJson = try {
            JSONObject(schemaContent)
        } catch (e: Exception) {
            throw IllegalArgumentException("MANAGED_STORAGE_UNAVAILABLE")
        }

        if (schemaJson.optString("type") != "object") {
            throw IllegalArgumentException("MANAGED_STORAGE_UNAVAILABLE")
        }

        val restrictionsManager = ctx.getSystemService(Context.RESTRICTIONS_SERVICE) as? android.content.RestrictionsManager
        val restrictionsBundle = restrictionsManager?.applicationRestrictions

        val result = JSONObject()
        if (restrictionsBundle != null && !restrictionsBundle.isEmpty) {
            val schemaProperties = schemaJson.optJSONObject("properties") ?: JSONObject()
            for (propKey in schemaProperties.keys()) {
                if (restrictionsBundle.containsKey(propKey)) {
                    val rawValue = restrictionsBundle.get(propKey)
                    if (isKeyFiltered(propKey, keys)) {
                        result.put(propKey, rawValue)
                    }
                }
            }
        }
        return result
    }

    private fun isKeyFiltered(key: String, keys: Any?): Boolean {
        return when (keys) {
            null -> true
            is String -> key == keys
            is List<*> -> keys.contains(key)
            is JSONObject -> keys.has(key)
            else -> false
        }
    }

    fun clearPrivateStorage(privateSessionId: String? = null) {
        if (privateSessionId != null) {
            privateStorageMap.remove(privateSessionId)
        } else {
            privateStorageMap.clear()
        }
    }

    private fun parseRawValue(raw: String): Any {
        return try {
            if (raw.startsWith("{")) {
                JSONObject(raw)
            } else if (raw.startsWith("[")) {
                JSONArray(raw)
            } else if (raw == "true" || raw == "false") {
                raw.toBoolean()
            } else {
                val d = raw.toDoubleOrNull()
                if (d != null) {
                    if (raw.contains(".")) d else raw.toLong()
                } else {
                    raw
                }
            }
        } catch (e: Exception) {
            raw
        }
    }

    suspend fun get(
        extensionId: String,
        area: String,
        keys: Any?,
        isPrivate: Boolean = false,
        privateSessionId: String? = null
    ): JSONObject {
        checkAreaSupported(area)

        if (area == "managed") {
            return getManagedStorage(extensionId, keys)
        }

        if (isPrivate) {
            val sessionId = privateSessionId ?: "default_private"
            val keyStore = privateStorageMap[sessionId]?.get("$extensionId:$area") ?: emptyMap()
            val result = JSONObject()
            when (keys) {
                null -> {
                    for ((k, v) in keyStore) {
                        result.put(k, parseRawValue(v))
                    }
                }
                is String -> {
                    keyStore[keys]?.let { result.put(keys, parseRawValue(it)) }
                }
                is List<*> -> {
                    val keysList = keys.filterIsInstance<String>()
                    for (k in keysList) {
                        keyStore[k]?.let { result.put(k, parseRawValue(it)) }
                    }
                }
                is JSONObject -> {
                    val defaultKeys = keys.keys().asSequence().toList()
                    for (key in defaultKeys) {
                        val v = keyStore[key]
                        if (v != null) {
                            result.put(key, parseRawValue(v))
                        } else {
                            result.put(key, keys.get(key))
                        }
                    }
                }
            }
            return result
        }

        if (area == "session") {
            val keyStore = sessionStorageMap[extensionId] ?: emptyMap()
            val result = JSONObject()
            when (keys) {
                null -> {
                    for ((k, v) in keyStore) {
                        result.put(k, parseRawValue(v))
                    }
                }
                is String -> {
                    keyStore[keys]?.let { result.put(keys, parseRawValue(it)) }
                }
                is List<*> -> {
                    val keysList = keys.filterIsInstance<String>()
                    for (k in keysList) {
                        keyStore[k]?.let { result.put(k, parseRawValue(it)) }
                    }
                }
                is JSONObject -> {
                    val defaultKeys = keys.keys().asSequence().toList()
                    for (key in defaultKeys) {
                        val v = keyStore[key]
                        if (v != null) {
                            result.put(key, parseRawValue(v))
                        } else {
                            result.put(key, keys.get(key))
                        }
                    }
                }
            }
            return result
        }

        val result = JSONObject()
        val allEntities = when (keys) {
            null -> {
                storageDao.getStorageByArea(extensionId, area)
            }
            is String -> {
                storageDao.getStorageByKeys(extensionId, area, listOf(keys))
            }
            is List<*> -> {
                val keysList = keys.filterIsInstance<String>()
                storageDao.getStorageByKeys(extensionId, area, keysList)
            }
            is JSONObject -> {
                val defaultKeys = keys.keys().asSequence().toList()
                val loaded = storageDao.getStorageByKeys(extensionId, area, defaultKeys)
                for (key in defaultKeys) {
                    result.put(key, keys.get(key))
                }
                loaded
            }
            else -> emptyList()
        }

        for (entity in allEntities) {
            val jsonVal = parseRawValue(entity.valueJson)
            result.put(entity.key, jsonVal)
        }

        return result
    }

    suspend fun set(
        extensionId: String,
        area: String,
        items: JSONObject,
        isPrivate: Boolean = false,
        privateSessionId: String? = null
    ) {
        checkAreaSupported(area)
        if (area == "managed") {
            throw IllegalArgumentException("STORAGE_MANAGED_READ_ONLY")
        }
        val keysList = items.keys().asSequence().toList()
        if (keysList.isEmpty()) return

        if (isPrivate) {
            val sessionId = privateSessionId ?: "default_private"
            val sessionMap = privateStorageMap.getOrPut(sessionId) { java.util.concurrent.ConcurrentHashMap() }
            val keyStore = sessionMap.getOrPut("$extensionId:$area") { java.util.concurrent.ConcurrentHashMap() }

            val changes = JSONObject()
            for (key in keysList) {
                val newValue = items.get(key)
                val oldValueRaw = keyStore[key]
                val oldValue = oldValueRaw?.let { parseRawValue(it) }

                keyStore[key] = newValue.toString()

                val changeObj = JSONObject()
                if (oldValue != null) {
                    changeObj.put("oldValue", oldValue)
                }
                changeObj.put("newValue", newValue)
                changes.put(key, changeObj)
            }

            if (changes.length() > 0) {
                changeListener?.invoke(extensionId, area, changes)
            }
            return
        }

        if (area == "session") {
            val keyStore = sessionStorageMap.getOrPut(extensionId) { java.util.concurrent.ConcurrentHashMap() }
            val changes = JSONObject()
            for (key in keysList) {
                val newValue = items.get(key)
                val oldValueRaw = keyStore[key]
                val oldValue = oldValueRaw?.let { parseRawValue(it) }

                keyStore[key] = newValue.toString()

                val changeObj = JSONObject()
                if (oldValue != null) {
                    changeObj.put("oldValue", oldValue)
                }
                changeObj.put("newValue", newValue)
                changes.put(key, changeObj)
            }

            if (changes.length() > 0) {
                changeListener?.invoke(extensionId, area, changes)
            }
            return
        }

        // 1. Gather all existing items to track oldValue
        val oldEntities = storageDao.getStorageByKeys(extensionId, area, keysList)
        val oldValuesMap = oldEntities.associate { it.key to parseRawValue(it.valueJson) }

        // 2. Perform write
        val entities = mutableListOf<StorageEntity>()
        val changes = JSONObject()

        for (key in keysList) {
            val newValue = items.get(key)
            entities.add(StorageEntity(extensionId, area, key, newValue.toString()))

            val oldValue = oldValuesMap[key]
            val changeObj = JSONObject()
            if (oldValue != null) {
                changeObj.put("oldValue", oldValue)
            }
            changeObj.put("newValue", newValue)
            changes.put(key, changeObj)
        }

        storageDao.insertStorage(entities)

        // 3. Trigger change listeners
        if (changes.length() > 0) {
            changeListener?.invoke(extensionId, area, changes)
        }
    }

    suspend fun remove(extensionId: String, area: String, keys: List<String>, isPrivate: Boolean = false, privateSessionId: String? = null) {
        checkAreaSupported(area)
        if (area == "managed") {
            throw IllegalArgumentException("STORAGE_MANAGED_READ_ONLY")
        }
        if (keys.isEmpty()) return

        if (isPrivate) {
            val sessionId = privateSessionId ?: "default_private"
            val keyStore = privateStorageMap[sessionId]?.get("$extensionId:$area") ?: return
            val changes = JSONObject()
            for (key in keys) {
                val oldValueRaw = keyStore.remove(key)
                if (oldValueRaw != null) {
                    val changeObj = JSONObject()
                    changeObj.put("oldValue", parseRawValue(oldValueRaw))
                    changeObj.put("newValue", JSONObject.NULL)
                    changes.put(key, changeObj)
                }
            }
            if (changes.length() > 0) {
                changeListener?.invoke(extensionId, area, changes)
            }
            return
        }

        if (area == "session") {
            val keyStore = sessionStorageMap[extensionId] ?: return
            val changes = JSONObject()
            for (key in keys) {
                val oldValueRaw = keyStore.remove(key)
                if (oldValueRaw != null) {
                    val changeObj = JSONObject()
                    changeObj.put("oldValue", parseRawValue(oldValueRaw))
                    changeObj.put("newValue", JSONObject.NULL)
                    changes.put(key, changeObj)
                }
            }
            if (changes.length() > 0) {
                changeListener?.invoke(extensionId, area, changes)
            }
            return
        }

        // 1. Gather old values
        val oldEntities = storageDao.getStorageByKeys(extensionId, area, keys)
        val changes = JSONObject()

        for (entity in oldEntities) {
            val oldValue = parseRawValue(entity.valueJson)
            val changeObj = JSONObject()
            changeObj.put("oldValue", oldValue)
            changeObj.put("newValue", JSONObject.NULL)
            changes.put(entity.key, changeObj)
        }

        // 2. Perform deletion
        storageDao.deleteStorageByKeys(extensionId, area, keys)

        // 3. Trigger change listeners
        if (changes.length() > 0) {
            changeListener?.invoke(extensionId, area, changes)
        }
    }

    suspend fun clear(extensionId: String, area: String, isPrivate: Boolean = false, privateSessionId: String? = null) {
        checkAreaSupported(area)
        if (area == "managed") {
            throw IllegalArgumentException("STORAGE_MANAGED_READ_ONLY")
        }

        if (isPrivate) {
            val sessionId = privateSessionId ?: "default_private"
            val keyStore = privateStorageMap[sessionId]?.remove("$extensionId:$area") ?: return
            val changes = JSONObject()
            for ((key, rawVal) in keyStore) {
                val changeObj = JSONObject()
                changeObj.put("oldValue", parseRawValue(rawVal))
                changeObj.put("newValue", JSONObject.NULL)
                changes.put(key, changeObj)
            }
            if (changes.length() > 0) {
                changeListener?.invoke(extensionId, area, changes)
            }
            return
        }

        if (area == "session") {
            val keyStore = sessionStorageMap.remove(extensionId) ?: return
            val changes = JSONObject()
            for ((key, rawVal) in keyStore) {
                val changeObj = JSONObject()
                changeObj.put("oldValue", parseRawValue(rawVal))
                changeObj.put("newValue", JSONObject.NULL)
                changes.put(key, changeObj)
            }
            if (changes.length() > 0) {
                changeListener?.invoke(extensionId, area, changes)
            }
            return
        }

        // 1. Gather all existing keys
        val oldEntities = storageDao.getStorageByArea(extensionId, area)
        val changes = JSONObject()

        for (entity in oldEntities) {
            val oldValue = parseRawValue(entity.valueJson)
            val changeObj = JSONObject()
            changeObj.put("oldValue", oldValue)
            changeObj.put("newValue", JSONObject.NULL)
            changes.put(entity.key, changeObj)
        }

        // 2. Perform wipe
        storageDao.clearStorage(extensionId, area)

        // 3. Trigger change listeners
        if (changes.length() > 0) {
            changeListener?.invoke(extensionId, area, changes)
        }
    }

    suspend fun getBytesInUse(
        extensionId: String,
        area: String,
        keys: Any?,
        isPrivate: Boolean = false,
        privateSessionId: String? = null
    ): Long {
        checkAreaSupported(area)
        val data = get(extensionId, area, keys, isPrivate, privateSessionId)
        var totalBytes = 0L
        for (key in data.keys()) {
            val keyBytes = key.toByteArray(Charsets.UTF_8).size.toLong()
            val valueStr = data.opt(key)?.toString() ?: ""
            val valBytes = valueStr.toByteArray(Charsets.UTF_8).size.toLong()
            totalBytes += (keyBytes + valBytes)
        }
        return totalBytes
    }
}

