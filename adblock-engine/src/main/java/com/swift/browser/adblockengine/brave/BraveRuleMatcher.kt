package com.swift.browser.adblockengine.brave

import java.util.regex.Pattern

/**
 * Runs high-performance rule matching on compiled BraveRule dataset.
 */
object BraveRuleMatcher {
    @Volatile private var exactBlockedHosts: Set<String> = emptySet()
    @Volatile private var exactExceptionHosts: Set<String> = emptySet()
    @Volatile private var networkBlockRules: List<BraveRule> = emptyList()
    @Volatile private var networkExceptionRules: List<BraveRule> = emptyList()
    @Volatile private var cosmeticRules: List<BraveRule> = emptyList()
    @Volatile private var cosmeticExceptionRules: List<BraveRule> = emptyList()

    fun clear() {
        exactBlockedHosts = emptySet()
        exactExceptionHosts = emptySet()
        networkBlockRules = emptyList()
        networkExceptionRules = emptyList()
        cosmeticRules = emptyList()
        cosmeticExceptionRules = emptyList()
    }

    fun addRules(rules: Collection<BraveRule>) {
        val newExactBlocked = HashSet<String>(exactBlockedHosts)
        val newExactException = HashSet<String>(exactExceptionHosts)
        val newNetworkBlock = ArrayList<BraveRule>(networkBlockRules)
        val newNetworkException = ArrayList<BraveRule>(networkExceptionRules)
        val newCosmetic = ArrayList<BraveRule>(cosmeticRules)
        val newCosmeticException = ArrayList<BraveRule>(cosmeticExceptionRules)

        for (rule in rules) {
            if (rule.isCosmetic) {
                if (rule.isException) {
                    newCosmeticException.add(rule)
                } else {
                    newCosmetic.add(rule)
                }
            } else {
                val trimmedPattern = rule.pattern.trim()
                if (trimmedPattern.isEmpty() || trimmedPattern == "||" || trimmedPattern == "^") {
                    continue
                }
                if (rule.isException) {
                    if (rule.pattern.startsWith("||") && rule.pattern.endsWith("^") && !rule.isThirdPartyOnly && !rule.isScriptOnly && !rule.isImageOnly && rule.domains.isEmpty()) {
                        val host = rule.pattern.substring(2, rule.pattern.length - 1).lowercase().trim()
                        if (host.isNotEmpty()) {
                            newExactException.add(host)
                        } else {
                            newNetworkException.add(rule)
                        }
                    } else {
                        newNetworkException.add(rule)
                    }
                } else {
                    if (rule.pattern.startsWith("||") && rule.pattern.endsWith("^") && !rule.isThirdPartyOnly && !rule.isScriptOnly && !rule.isImageOnly && rule.domains.isEmpty()) {
                        val host = rule.pattern.substring(2, rule.pattern.length - 1).lowercase().trim()
                        if (host.isNotEmpty()) {
                            newExactBlocked.add(host)
                        } else {
                            newNetworkBlock.add(rule)
                        }
                    } else {
                        newNetworkBlock.add(rule)
                    }
                }
            }
        }
        
        exactBlockedHosts = newExactBlocked
        exactExceptionHosts = newExactException
        networkBlockRules = newNetworkBlock
        networkExceptionRules = newNetworkException
        cosmeticRules = newCosmetic
        cosmeticExceptionRules = newCosmeticException
    }

    fun getRulesCount(): Int {
        return exactBlockedHosts.size + exactExceptionHosts.size + networkBlockRules.size + networkExceptionRules.size + cosmeticRules.size + cosmeticExceptionRules.size
    }

    fun matchesRequest(
        url: String,
        reqHost: String,
        isThirdParty: Boolean,
        resourceType: String?,
        documentHost: String?
    ): Boolean {
        val result = evaluate(url, isThirdParty, resourceType, documentHost)
        return result == MatchResult.BLOCK
    }

    fun evaluate(
        url: String,
        isThirdParty: Boolean,
        resourceType: String?,
        documentHost: String?
    ): MatchResult {
        val reqHost = try {
            android.net.Uri.parse(url).host?.lowercase() ?: ""
        } catch (e: Exception) {
            ""
        }
        
        // 1. Check O(1) exceptions first
        if (exactExceptionHosts.contains(reqHost) || isDomainOrSubdomainInSet(reqHost, exactExceptionHosts)) {
            return MatchResult.EXCEPTION
        }
        
        // 2. Check full network exception rules list
        if (networkExceptionRules.isNotEmpty()) {
            for (rule in networkExceptionRules) {
                if (ruleMatches(rule, url, reqHost, isThirdParty, resourceType, documentHost)) {
                    return MatchResult.EXCEPTION
                }
            }
        }
        
        // 3. Check O(1) blocked hosts list
        if (exactBlockedHosts.contains(reqHost) || isDomainOrSubdomainInSet(reqHost, exactBlockedHosts)) {
            return MatchResult.BLOCK
        }
        
        // 4. Check network block rules
        if (networkBlockRules.isNotEmpty()) {
            for (rule in networkBlockRules) {
                if (ruleMatches(rule, url, reqHost, isThirdParty, resourceType, documentHost)) {
                    return MatchResult.BLOCK
                }
            }
        }
        return MatchResult.ALLOW
    }

    fun getCosmeticSelectors(documentHost: String?): List<String> {
        val host = documentHost?.lowercase() ?: ""
        val selectors = mutableListOf<String>()
        
        // Find applicable elements
        for (rule in cosmeticRules) {
            val selector = rule.elementSelector ?: continue
            
            // Check domain inclusion constraint
            if (rule.domains.isNotEmpty()) {
                val matchedDomain = rule.domains.any { d -> host == d || host.endsWith(".$d") }
                if (!matchedDomain) continue
            }
            
            // Check domain exclusion constraint
            if (rule.excludedDomains.isNotEmpty()) {
                val matchedExclusion = rule.excludedDomains.any { d -> host == d || host.endsWith(".$d") }
                if (matchedExclusion) continue
            }
            selectors.add(selector)
        }
        
        // Subtract exception rules
        if (cosmeticExceptionRules.isNotEmpty()) {
            val exceptions = mutableSetOf<String>()
            for (rule in cosmeticExceptionRules) {
                val selector = rule.elementSelector ?: continue
                if (rule.domains.isNotEmpty()) {
                    val matchedDomain = rule.domains.any { d -> host == d || host.endsWith(".$d") }
                    if (!matchedDomain) continue
                }
                if (rule.excludedDomains.isNotEmpty()) {
                    val matchedExclusion = rule.excludedDomains.any { d -> host == d || host.endsWith(".$d") }
                    if (matchedExclusion) continue
                }
                exceptions.add(selector)
            }
            selectors.removeAll(exceptions)
        }
        return selectors
    }

    private fun isDomainOrSubdomainInSet(host: String, set: Set<String>): Boolean {
        var domain = host
        while (domain.isNotEmpty()) {
            if (set.contains(domain)) return true
            val nextDot = domain.indexOf('.')
            if (nextDot == -1) break
            domain = domain.substring(nextDot + 1)
        }
        return false
    }

    private fun ruleMatches(
        rule: BraveRule,
        url: String,
        reqHost: String,
        isThirdParty: Boolean,
        resourceType: String?,
        documentHost: String?
    ): Boolean {
        // Option constraints
        if (rule.isThirdPartyOnly && !isThirdParty) return false
        if (rule.isScriptOnly && resourceType != "script") return false
        if (rule.isImageOnly && resourceType != "image") return false
        
        // Document domain restriction constraint
        if (documentHost != null) {
            if (rule.domains.isNotEmpty()) {
                val matchesDomain = rule.domains.any { d -> documentHost == d || documentHost.endsWith(".$d") }
                if (!matchesDomain) return false
            }
            if (rule.excludedDomains.isNotEmpty()) {
                val matchesExclusion = rule.excludedDomains.any { d -> documentHost == d || documentHost.endsWith(".$d") }
                if (matchesExclusion) return false
            }
        }
        
        // Pattern matching
        val pattern = rule.pattern
        if (pattern.trim().isEmpty() || pattern == "||" || pattern == "^") return false
        return urlContainsPattern(url, reqHost, pattern)
    }

    private fun urlContainsPattern(url: String, reqHost: String, pattern: String): Boolean {
        var cleanPattern = pattern.trim()
        if (cleanPattern.isEmpty() || cleanPattern == "||" || cleanPattern == "^") return false
        var startsWithHostAnchor = false
        var endsWithHostAnchor = false
        if (cleanPattern.startsWith("||")) {
            startsWithHostAnchor = true
            cleanPattern = cleanPattern.substring(2)
        }
        if (cleanPattern.endsWith("^")) {
            endsWithHostAnchor = true
            cleanPattern = cleanPattern.substring(0, cleanPattern.length - 1)
        }
        val urlLower = url.lowercase()
        val patternLower = cleanPattern.lowercase().trim()
        if (patternLower.isEmpty()) return false
        
        if (startsWithHostAnchor) {
            if (endsWithHostAnchor) {
                return reqHost == patternLower || reqHost.endsWith(".$patternLower")
            }
            return urlLower.contains("://$patternLower") || urlLower.contains(".$patternLower")
        }
        
        // Wildcard fallback simple regex
        if (patternLower.contains("*") || patternLower.contains("?")) {
            try {
                val regex = patternLower.replace(".", "\\.").replace("*", ".*").replace("?", ".?")
                return Pattern.compile(regex).matcher(urlLower).find()
            } catch (e: Exception) {
                return urlLower.contains(patternLower.replace("*", ""))
            }
        }
        return urlLower.contains(patternLower)
    }

    enum class MatchResult {
        ALLOW, BLOCK, EXCEPTION
    }
}
