package com.swift.browser.adblockengine.filters

import android.content.Context
import com.swift.browser.adblockengine.storage.AdBlockPreferenceStore

/**
 * Handles custom rule expressions entered manually by users.
 */
object CustomFilterSource {
    private val memoryRules = LinkedHashSet<String>()

    fun getCustomFilters(context: Context): List<String> {
        if (memoryRules.isEmpty()) {
            val saved = AdBlockPreferenceStore.getStringSet(context, "custom_user_rules")
            if (saved != null) {
                memoryRules.addAll(saved)
            }
        }
        return memoryRules.toList()
    }

    fun addCustomFilter(context: Context, rule: String) {
        val trimmed = rule.trim()
        if (trimmed.isNotEmpty()) {
            memoryRules.add(trimmed)
            save(context)
            FilterListManager.rebuildActiveRules(context)
        }
    }

    fun removeCustomFilter(context: Context, rule: String) {
        memoryRules.remove(rule)
        save(context)
        FilterListManager.rebuildActiveRules(context)
    }

    fun clear(context: Context) {
        memoryRules.clear()
        save(context)
        FilterListManager.rebuildActiveRules(context)
    }

    private fun save(context: Context) {
        AdBlockPreferenceStore.saveStringSet(context, "custom_user_rules", memoryRules)
    }
}
