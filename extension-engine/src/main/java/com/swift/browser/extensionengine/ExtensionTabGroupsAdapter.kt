package com.swift.browser.extensionengine

import com.swift.browser.tabengine.api.TabEngineApi
import com.swift.browser.tabengine.model.TabGroupModel
import org.json.JSONArray
import org.json.JSONObject

class ExtensionTabGroupsAdapter(
    private val tabEngine: TabEngineApi,
    private val registry: ExtensionRegistry,
    private val permissionManager: PermissionManager
) {

    fun queryGroups(sender: ExtensionSender, queryInfo: JSONObject): JSONArray {
        val valResult = ExtensionMessagingValidator.validateSender(sender, registry, permissionManager)
        if (valResult is ExtensionMessagingValidator.ValidationResult.Denied) {
            throw IllegalStateException(valResult.reason)
        }

        val allGroups = tabEngine.groups.value
        val titleFilter = if (queryInfo.has("title")) queryInfo.getString("title") else null
        val colorFilter = if (queryInfo.has("color")) queryInfo.getString("color") else null

        val filtered = allGroups.filter { group ->
            if (group.isPrivate && !sender.isPrivate && !permissionManager.isAllowedInPrivate(sender.extensionId)) {
                return@filter false
            }
            if (titleFilter != null && !group.name.contains(titleFilter, ignoreCase = true)) {
                return@filter false
            }
            true
        }

        val array = JSONArray()
        filtered.forEach { group ->
            array.put(formatGroupObject(group))
        }
        return array
    }

    fun getGroup(sender: ExtensionSender, groupIdInput: Any): JSONObject {
        val valResult = ExtensionMessagingValidator.validateSender(sender, registry, permissionManager)
        if (valResult is ExtensionMessagingValidator.ValidationResult.Denied) {
            throw IllegalStateException(valResult.reason)
        }

        val groupIdStr = groupIdInput.toString()
        val group = tabEngine.groups.value.find { it.id == groupIdStr || it.id.hashCode().toString() == groupIdStr }
            ?: throw IllegalArgumentException("Tab group not found: $groupIdInput")

        if (group.isPrivate && !sender.isPrivate && !permissionManager.isAllowedInPrivate(sender.extensionId)) {
            throw SecurityException("Access to private tab group denied")
        }

        return formatGroupObject(group)
    }

    fun updateGroup(sender: ExtensionSender, groupIdInput: Any, updateProperties: JSONObject): JSONObject {
        val valResult = ExtensionMessagingValidator.validateSender(sender, registry, permissionManager)
        if (valResult is ExtensionMessagingValidator.ValidationResult.Denied) {
            throw IllegalStateException(valResult.reason)
        }

        val groupIdStr = groupIdInput.toString()
        val group = tabEngine.groups.value.find { it.id == groupIdStr || it.id.hashCode().toString() == groupIdStr }
            ?: throw IllegalArgumentException("Tab group not found: $groupIdInput")

        val newTitle = updateProperties.optString("title", group.name)
        val newColor = if (updateProperties.has("color")) parseColor(updateProperties.getString("color")) else group.color

        // Update group in TabEngine by recreating or updating state
        tabEngine.updateGroup(group.id) { it.copy(name = newTitle, color = newColor) }
        val updatedGroup = tabEngine.groups.value.find { it.id == group.id } ?: group.copy(name = newTitle, color = newColor)
        return formatGroupObject(updatedGroup)
    }

    fun moveGroup(sender: ExtensionSender, groupIdInput: Any, moveProperties: JSONObject): JSONObject {
        val valResult = ExtensionMessagingValidator.validateSender(sender, registry, permissionManager)
        if (valResult is ExtensionMessagingValidator.ValidationResult.Denied) {
            throw IllegalStateException(valResult.reason)
        }

        return getGroup(sender, groupIdInput)
    }

    private fun formatGroupObject(group: TabGroupModel): JSONObject {
        return JSONObject().apply {
            put("id", group.id.hashCode())
            put("collapsed", false)
            put("color", colorToChromeString(group.color))
            put("title", group.name)
            put("windowId", if (group.isPrivate || group.isIncognito) 2 else 1)
        }
    }

    private fun colorToChromeString(color: Long): String {
        return when (color) {
            0xFF475569 -> "grey"
            0xFFEF4444 -> "red"
            0xFF3B82F6 -> "blue"
            0xFF10B981 -> "green"
            0xFFF59E0B -> "yellow"
            0xFF8B5CF6 -> "purple"
            0xFFEC4899 -> "pink"
            else -> "blue"
        }
    }

    private fun parseColor(colorStr: String): Long {
        return when (colorStr.lowercase()) {
            "grey" -> 0xFF475569
            "red" -> 0xFFEF4444
            "blue" -> 0xFF3B82F6
            "green" -> 0xFF10B981
            "yellow" -> 0xFFF59E0B
            "purple" -> 0xFF8B5CF6
            "pink" -> 0xFFEC4899
            else -> 0xFF3B82F6
        }
    }
}
