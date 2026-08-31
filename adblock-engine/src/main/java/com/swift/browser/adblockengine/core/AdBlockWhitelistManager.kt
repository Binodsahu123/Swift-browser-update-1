package com.swift.browser.adblockengine.core

import android.content.Context
import com.swift.browser.adblockengine.filters.FilterListManager
import com.swift.browser.adblockengine.storage.AdBlockPreferenceStore
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles the list of white-listed domains where shields should be temporarily paused.
 */
object AdBlockWhitelistManager {
    private val whitelist = ConcurrentHashMap.newKeySet<String>()

    fun init(context: Context) {
        val saved = AdBlockPreferenceStore.getStringSet(context, "whitelist_sites")
        whitelist.clear()
        if (saved != null) {
            whitelist.addAll(saved)
        }
    }

    fun getWhitelist(): Set<String> = whitelist.toSet()

    fun isWhitelisted(domain: String): Boolean {
        var host = domain.lowercase().trim()
        if (host.startsWith("www.")) {
            host = host.substring(4)
        }
        return whitelist.contains(host)
    }

    fun add(context: Context, domain: String) {
        var host = domain.lowercase().trim()
        if (host.startsWith("www.")) {
            host = host.substring(4)
        }
        if (host.isNotEmpty()) {
            whitelist.add(host)
            save(context)
            FilterListManager.rebuildActiveRules(context)
        }
    }

    fun remove(context: Context, domain: String) {
        var host = domain.lowercase().trim()
        if (host.startsWith("www.")) {
            host = host.substring(4)
        }
        whitelist.remove(host)
        save(context)
        FilterListManager.rebuildActiveRules(context)
    }

    private fun save(context: Context) {
        AdBlockPreferenceStore.saveStringSet(context, "whitelist_sites", whitelist)
    }
}
