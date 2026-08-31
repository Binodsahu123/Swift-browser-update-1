package com.swift.browser.adblockengine.storage

import android.content.Context

/**
 * Main persistent data repository for managing rule states and database transactions.
 */
class AdBlockRepository(context: Context) {
    private val dao = AdBlockDao(context)

    fun addWhitelistDomain(domain: String) {
        dao.addException(domain, "whitelist")
    }

    fun removeWhitelistDomain(domain: String) {
        dao.removeException(domain)
    }

    fun getWhitelist(): List<String> {
        return dao.getExceptions("whitelist")
    }

    fun addBlacklistDomain(domain: String) {
        dao.addException(domain, "blacklist")
    }

    fun removeBlacklistDomain(domain: String) {
        dao.removeException(domain)
    }

    fun getBlacklist(): List<String> {
        return dao.getExceptions("blacklist")
    }

    fun recordBlock(domain: String) {
        dao.recordSiteBlocked(domain)
    }

    fun getBlockCount(domain: String): Int {
        return dao.getSiteBlockedCount(domain)
    }
}
