package com.swift.browser.extensionengine

import java.util.regex.Pattern

/**
 * Single canonical Declarative Net Request (DNR) Data Models & Snapshots.
 */

enum class DnrRuleSource {
    SESSION,
    DYNAMIC,
    STATIC
}

data class DnrHeader(
    val header: String,
    val operation: String, // "set", "append", "remove"
    val value: String? = null
) {
    fun isValid(): Boolean {
        if (header.isBlank()) return false
        // Prevent HTTP header injection / response smuggling
        if (header.contains("\r") || header.contains("\n") || header.contains("\u0000")) return false
        if (value != null && (value.contains("\r") || value.contains("\n") || value.contains("\u0000"))) return false
        val op = operation.lowercase()
        return op == "set" || op == "append" || op == "remove"
    }
}

data class DnrAction(
    val type: String, // "block", "allow", "redirect", "modifyHeaders", "upgradeScheme", "allowAllRequests"
    val redirectUrl: String? = null,
    val requestHeaders: List<DnrHeader> = emptyList(),
    val responseHeaders: List<DnrHeader> = emptyList()
) {
    fun isValid(): Boolean {
        val t = type.lowercase()
        val validTypes = listOf("block", "allow", "redirect", "modifyheaders", "upgradescheme", "allowallrequests")
        if (!validTypes.contains(t)) return false
        if (t == "redirect" && redirectUrl.isNull_orBlank()) return false
        if (requestHeaders.any { !it.isValid() }) return false
        if (responseHeaders.any { !it.isValid() }) return false
        return true
    }
}

private fun String?.isNull_orBlank(): Boolean = this.isNullOrEmpty() || this.isBlank()

data class DnrCondition(
    val urlFilter: String? = null,
    val regexFilter: String? = null,
    val isUrlFilterCaseSensitive: Boolean = false,
    val resourceTypes: List<String> = emptyList(),
    val excludedResourceTypes: List<String> = emptyList(),
    val domains: List<String> = emptyList(),
    val excludedDomains: List<String> = emptyList(),
    val requestDomains: List<String> = emptyList(),
    val excludedRequestDomains: List<String> = emptyList(),
    val initiatorDomains: List<String> = emptyList(),
    val excludedInitiatorDomains: List<String> = emptyList(),
    val requestMethods: List<String> = emptyList(),
    val excludedRequestMethods: List<String> = emptyList(),
    val domainType: String? = null // "firstParty", "thirdParty"
)

data class DnrRule(
    val id: Int,
    val priority: Int = 1,
    val action: DnrAction,
    val condition: DnrCondition
) {
    fun isValid(): Boolean {
        if (id <= 0) return false
        if (!action.isValid()) return false
        
        // Regex safety check
        condition.regexFilter?.let { regex ->
            if (regex.length > 500) return false
            // Check for dangerous nested quantifiers that cause exponential backtracking
            if (regex.contains("*+") || regex.contains("++") || regex.contains("?+") || regex.contains("*{") || regex.contains("+{")) {
                return false
            }
            try {
                Pattern.compile(regex)
            } catch (e: Exception) {
                return false
            }
        }
        return true
    }
}

data class ExtensionRulesBundle(
    val sessionRules: List<DnrRule> = emptyList(),
    val dynamicRules: List<DnrRule> = emptyList(),
    val staticRules: List<DnrRule> = emptyList()
) {
    fun getAllRulesWithSource(): List<Pair<DnrRule, DnrRuleSource>> {
        val result = mutableListOf<Pair<DnrRule, DnrRuleSource>>()
        sessionRules.forEach { result.add(it to DnrRuleSource.SESSION) }
        dynamicRules.forEach { result.add(it to DnrRuleSource.DYNAMIC) }
        staticRules.forEach { result.add(it to DnrRuleSource.STATIC) }
        return result
    }
}

/**
 * Immutable Compiled Rule representation for atomic, thread-safe evaluation.
 */
data class CompiledDnrRule(
    val rule: DnrRule,
    val source: DnrRuleSource,
    val extensionId: String,
    val compiledRegex: Pattern? = null,
    val compiledUrlFilterPattern: Pattern? = null,
    val cleanDomains: Set<String> = emptySet(),
    val cleanExcludedDomains: Set<String> = emptySet(),
    val cleanInitiatorDomains: Set<String> = emptySet(),
    val cleanExcludedInitiatorDomains: Set<String> = emptySet(),
    val resourceTypeSet: Set<String> = emptySet(),
    val excludedResourceTypeSet: Set<String> = emptySet(),
    val requestMethodSet: Set<String> = emptySet(),
    val excludedRequestMethodSet: Set<String> = emptySet()
)

/**
 * Thread-safe Immutable Rule Snapshot evaluated against incoming requests.
 */
data class DnrRuleSnapshot(
    val extensionId: String,
    val rules: List<Pair<DnrRule, DnrRuleSource>>,
    val compiledRules: List<CompiledDnrRule>,
    val generationId: Long = System.currentTimeMillis()
)

data class DnrMatchResult(
    val matchedRuleId: Int,
    val extensionId: String,
    val actionType: String,
    val redirectUrl: String? = null,
    val requestHeaders: List<DnrHeader> = emptyList(),
    val responseHeaders: List<DnrHeader> = emptyList(),
    val timeStamp: Long = System.currentTimeMillis()
)
