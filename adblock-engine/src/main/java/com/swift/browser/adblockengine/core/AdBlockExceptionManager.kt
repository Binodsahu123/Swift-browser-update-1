package com.swift.browser.adblockengine.core

import android.content.Context
import com.swift.browser.adblockengine.filters.FilterListManager
import com.swift.browser.adblockengine.storage.AdBlockPreferenceStore
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages explicit blacklists and exceptions per site domain.
 */
object AdBlockExceptionManager {
    private val blacklist = ConcurrentHashMap.newKeySet<String>()

    fun init(context: Context) {
        val saved = AdBlockPreferenceStore.getStringSet(context, "blacklist_sites")
        blacklist.clear()
        if (saved != null) {
            blacklist.addAll(saved)
        }
    }

    fun getBlacklist(): Set<String> = blacklist.toSet()

    fun isExplicitlyBlacklisted(domain: String): Boolean {
        var host = domain.lowercase().trim()
        if (host.startsWith("www.")) {
            host = host.substring(4)
        }
        return blacklist.contains(host)
    }

    fun add(context: Context, domain: String) {
        var host = domain.lowercase().trim()
        if (host.startsWith("www.")) {
            host = host.substring(4)
        }
        if (host.isNotEmpty()) {
            blacklist.add(host)
            save(context)
            FilterListManager.rebuildActiveRules(context)
        }
    }

    fun remove(context: Context, domain: String) {
        var host = domain.lowercase().trim()
        if (host.startsWith("www.")) {
            host = host.substring(4)
        }
        blacklist.remove(host)
        save(context)
        FilterListManager.rebuildActiveRules(context)
    }

    private fun save(context: Context) {
        AdBlockPreferenceStore.saveStringSet(context, "blacklist_sites", blacklist)
    }
}
