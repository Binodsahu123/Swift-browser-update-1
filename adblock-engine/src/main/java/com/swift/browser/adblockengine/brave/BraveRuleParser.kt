package com.swift.browser.adblockengine.brave

import android.util.Log

/**
 * Represents a parsed structured rule compatible with Brave/uBlock style filter syntax.
 */
data class BraveRule(
    val rawRule: String,
    val isException: Boolean = false,
    val isCosmetic: Boolean = false,
    val pattern: String = "",
    val domains: Set<String> = emptySet(),
    val excludedDomains: Set<String> = emptySet(),
    val isThirdPartyOnly: Boolean = false,
    val isScriptOnly: Boolean = false,
    val isImageOnly: Boolean = false,
    val elementSelector: String? = null,
    val originalDomainConstraint: String? = null
)

/**
 * Parses Brave-compatible / filter-list-compatible rules.
 * Supports cosmetic hiding rules (e.g., domain##selector, ##selector, domain#@#selector),
 * standard block rules, domains constraints, and options ($third-party, $script, $image).
 */
object BraveRuleParser {
    private const val TAG = "BraveRuleParser"

    fun parseLine(line: String): BraveRule? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("!") || trimmed.startsWith("[")) {
            return null
        }

        try {
            // Check if cosmetic element hiding rule
            if (trimmed.contains("##") || trimmed.contains("#@#")) {
                val isException = trimmed.contains("#@#")
                val parts = if (isException) trimmed.split("#@#", limit = 2) else trimmed.split("##", limit = 2)
                if (parts.size < 2) return null
                
                val domainPart = parts[0].trim()
                val selector = parts[1].trim()
                if (selector.isEmpty()) return null

                val domains = mutableSetOf<String>()
                val excludedDomains = mutableSetOf<String>()
                if (domainPart.isNotEmpty()) {
                    for (d in domainPart.split(",")) {
                        val dom = d.trim().lowercase()
                        if (dom.startsWith("~")) {
                            excludedDomains.add(dom.substring(1))
                        } else {
                            domains.add(dom)
                        }
                    }
                }

                return BraveRule(
                    rawRule = trimmed,
                    isException = isException,
                    isCosmetic = true,
                    elementSelector = selector,
                    domains = domains,
                    excludedDomains = excludedDomains
                )
            }

            // Network block rule or exception
            var ruleText = trimmed
            var isException = false
            if (ruleText.startsWith("@@")) {
                isException = true
                ruleText = ruleText.substring(2)
            }

            // Extract options
            var patternPart = ruleText
            var domains = emptySet<String>()
            var excludedDomains = emptySet<String>()
            var isThirdPartyOnly = false
            var isScriptOnly = false
            var isImageOnly = false
            var originalDomainConstraint: String? = null

            if (ruleText.contains("$")) {
                val parts = ruleText.split("$", limit = 2)
                patternPart = parts[0]
                val options = parts[1].split(",")
                for (opt in options) {
                    val trimmedOpt = opt.trim().lowercase()
                    when {
                        trimmedOpt == "third-party" -> isThirdPartyOnly = true
                        trimmedOpt == "script" -> isScriptOnly = true
                        trimmedOpt == "image" -> isImageOnly = true
                        trimmedOpt.startsWith("domain=") -> {
                            originalDomainConstraint = trimmedOpt.substring(7)
                            val doms = mutableSetOf<String>()
                            val exDoms = mutableSetOf<String>()
                            for (d in originalDomainConstraint.split("|")) {
                                val dTrim = d.trim()
                                if (dTrim.startsWith("~")) {
                                    exDoms.add(dTrim.substring(1))
                                } else {
                                    doms.add(dTrim)
                                }
                            }
                            domains = doms
                            excludedDomains = exDoms
                        }
                    }
                }
            }

            return BraveRule(
                rawRule = trimmed,
                isException = isException,
                isCosmetic = false,
                pattern = patternPart,
                domains = domains,
                excludedDomains = excludedDomains,
                isThirdPartyOnly = isThirdPartyOnly,
                isScriptOnly = isScriptOnly,
                isImageOnly = isImageOnly,
                originalDomainConstraint = originalDomainConstraint
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing rule: $line", e)
            return null
        }
    }
}
