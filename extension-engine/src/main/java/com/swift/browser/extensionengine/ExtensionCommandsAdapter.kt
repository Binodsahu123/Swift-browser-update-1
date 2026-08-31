package com.swift.browser.extensionengine

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

data class CommandSpec(
    val name: String,
    val description: String,
    val shortcut: String
)

class ExtensionCommandsAdapter(
    private val permissionManager: PermissionManager,
    private val registry: ExtensionRegistry,
    private val eventManager: EventManager
) {
    companion object {
        val registeredShortcuts = ConcurrentHashMap<String, String>() // shortcut -> extensionId

        fun cleanupExtensionState(extensionId: String) {
            val keysToRemove = registeredShortcuts.filterValues { it == extensionId }.keys
            for (k in keysToRemove) {
                registeredShortcuts.remove(k)
            }
        }
    }

    private fun validate(sender: ExtensionSender): ParsedExtension {
        val extId = sender.extensionId.lowercase().trim()
        val ext = registry.getExtension(extId) ?: throw IllegalArgumentException("EXTENSION_NOT_FOUND")
        if (!registry.isExtensionEnabled(extId)) {
            throw IllegalArgumentException("EXTENSION_DISABLED")
        }
        if (sender.isPrivate && !permissionManager.isAllowedInPrivate(extId)) {
            throw IllegalArgumentException("PRIVATE_MODE_DENIED")
        }
        return ext
    }

    private fun getCommandsFromManifest(ext: ParsedExtension): List<CommandSpec> {
        val result = ArrayList<CommandSpec>()
        try {
            val root = JSONObject(ext.manifestJson)
            if (root.has("commands")) {
                val cmdsObj = root.getJSONObject("commands")
                val keys = cmdsObj.keys()
                while (keys.hasNext()) {
                    val name = keys.next()
                    val cmdObj = cmdsObj.getJSONObject(name)
                    val desc = cmdObj.optString("description", "")
                    
                    var suggestedKey = ""
                    if (cmdObj.has("suggested_key")) {
                        val sug = cmdObj.get("suggested_key")
                        if (sug is JSONObject) {
                            suggestedKey = sug.optString("default", "")
                        } else if (sug is String) {
                            suggestedKey = sug
                        }
                    }
                    result.add(CommandSpec(name, desc, suggestedKey))
                }
            }
        } catch (e: Exception) {
            // Safe fallback
        }
        return result
    }

    fun getAll(sender: ExtensionSender): JSONArray {
        val ext = validate(sender)
        val list = getCommandsFromManifest(ext)
        val arr = JSONArray()
        for (cmd in list) {
            // Register shortcut and check for conflict
            val shortcut = cmd.shortcut.trim()
            if (shortcut.isNotBlank()) {
                val existingOwner = registeredShortcuts[shortcut]
                if (existingOwner != null && existingOwner != ext.id) {
                    throw IllegalArgumentException("COMMAND_CONFLICT")
                }
                
                // Validate if shortcut is supported in Android/Orion
                if (!isShortcutSupported(shortcut)) {
                    throw IllegalArgumentException("COMMAND_SHORTCUT_UNSUPPORTED")
                }
                
                registeredShortcuts[shortcut] = ext.id
            }

            if (cmd.name.isBlank()) {
                throw IllegalArgumentException("COMMAND_INVALID")
            }

            val obj = JSONObject().apply {
                put("name", cmd.name)
                put("description", cmd.description)
                put("shortcut", cmd.shortcut)
            }
            arr.put(obj)
        }
        return arr
    }

    private fun isShortcutSupported(shortcut: String): Boolean {
        // Simple Android/Orion constraint: must have valid structure or containing Ctrl/Alt/Shift/Command/Meta
        val upper = shortcut.uppercase()
        if (upper.isBlank()) return true
        val parts = upper.split("+")
        if (parts.size < 2) return false // Must have at least modifier and key
        val modifiers = listOf("CTRL", "ALT", "SHIFT", "COMMAND", "META", "MACCTRL")
        val hasModifier = parts.subList(0, parts.size - 1).all { modifiers.contains(it.trim()) }
        val lastKey = parts.last().trim()
        val isValidKey = lastKey.length == 1 && lastKey[0] in 'A'..'Z' || lastKey in listOf("LEFT", "RIGHT", "UP", "DOWN", "ENTER", "SPACE", "TAB", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9")
        return hasModifier && isValidKey
    }

    fun triggerCommand(extensionId: String, commandName: String) {
        val ext = registry.getExtension(extensionId) ?: throw IllegalArgumentException("EXTENSION_NOT_FOUND")
        if (!registry.isExtensionEnabled(extensionId)) {
            throw IllegalArgumentException("EXTENSION_DISABLED")
        }
        
        val list = getCommandsFromManifest(ext)
        if (list.none { it.name == commandName }) {
            throw IllegalArgumentException("COMMAND_NOT_FOUND")
        }

        val params = JSONObject().apply {
            put("command", commandName)
        }
        eventManager.triggerEvent("commands.onCommand", params)
    }
}
