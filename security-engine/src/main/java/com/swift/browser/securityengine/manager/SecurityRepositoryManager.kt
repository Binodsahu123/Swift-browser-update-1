package com.swift.browser.securityengine.manager

import com.swift.browser.securityengine.repository.SecurityRepository

class SecurityRepositoryManager(
    val repository: SecurityRepository = SecurityRepository()
) {
    fun whitelistDomain(host: String) = repository.whitelistDomain(host)
    fun unwhitelistDomain(host: String) = repository.unwhitelistDomain(host)
    fun isDomainWhitelisted(host: String): Boolean = repository.isDomainWhitelisted(host)
    fun getWhitelistedDomains(): Set<String> = repository.getWhitelistedDomains()
    fun getBlacklistedPatterns(): List<String> = repository.getBlacklistedPatterns()
}
