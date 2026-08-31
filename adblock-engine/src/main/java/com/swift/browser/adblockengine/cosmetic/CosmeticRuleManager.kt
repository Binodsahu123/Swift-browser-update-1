package com.swift.browser.adblockengine.cosmetic

/**
 * Stores and manages cosmetic selector indices for rapid page hiding lookups.
 */
object CosmeticRuleManager {
    private val ruleMap = HashMap<String, MutableList<String>>()

    fun addRule(domain: String, selector: String) {
        val list = ruleMap.getOrPut(domain.lowercase()) { ArrayList() }
        list.add(selector)
    }

    fun getRulesForDomain(domain: String): List<String> {
        return ruleMap[domain.lowercase()] ?: emptyList()
    }

    fun clear() {
        ruleMap.clear()
    }
}
