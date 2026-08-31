package com.swift.browser.extensionengine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * Unified, atomic, thread-safe, persistent DNR rule store and snapshot compiler.
 * Guarantees transactional dynamic rule persistence across restarts and publishes
 * immutable snapshots (`DnrRuleSnapshot`) with compiled matchers.
 */
class ExtensionDnrRuleStore(private val context: Context? = null) {
    private val ruleStore = ConcurrentHashMap<String, ExtensionRulesBundle>()
    private val snapshots = ConcurrentHashMap<String, DnrRuleSnapshot>()

    companion object {
        const val MAX_DYNAMIC_RULES = 5000
        const val MAX_SESSION_RULES = 5000
        const val MAX_REGEX_RULES = 1000
        private const val DYNAMIC_RULES_PREFS = "orion_dnr_dynamic_rules_v1"
    }

    init {
        loadPersistedDynamicRules()
    }

    /**
     * Loads dynamic rules from persistent storage on initialization.
     */
    private fun loadPersistedDynamicRules() {
        if (context == null) return
        try {
            val prefs = context.getSharedPreferences(DYNAMIC_RULES_PREFS, Context.MODE_PRIVATE)
            val allEntries = prefs.all
            for ((extId, jsonStr) in allEntries) {
                if (jsonStr is String && jsonStr.isNotBlank()) {
                    try {
                        val rulesList = parseRulesJson(jsonStr)
                        val bundle = ruleStore[extId.lowercase()] ?: ExtensionRulesBundle()
                        val updated = bundle.copy(dynamicRules = rulesList)
                        ruleStore[extId.lowercase()] = updated
                        publishSnapshot(extId.lowercase(), updated)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Persists dynamic rules for an extension to disk / SharedPreferences.
     */
    private fun persistDynamicRules(extensionId: String, rules: List<DnrRule>) {
        if (context == null) return
        try {
            val prefs = context.getSharedPreferences(DYNAMIC_RULES_PREFS, Context.MODE_PRIVATE)
            val jsonArr = JSONArray()
            rules.forEach { rule ->
                jsonArr.put(ruleToJson(rule))
            }
            prefs.edit().putString(extensionId.lowercase(), jsonArr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removePersistedDynamicRules(extensionId: String) {
        if (context == null) return
        try {
            val prefs = context.getSharedPreferences(DYNAMIC_RULES_PREFS, Context.MODE_PRIVATE)
            prefs.edit().remove(extensionId.lowercase()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Atomically updates dynamic rules for an extension.
     */
    @Synchronized
    fun updateDynamicRules(
        extensionId: String,
        removeRuleIds: Set<Int>,
        addRules: List<DnrRule>
    ): List<DnrRule> {
        val cleanExtId = extensionId.lowercase().trim()
        val currentBundle = ruleStore[cleanExtId] ?: ExtensionRulesBundle()

        // 1. Snapshot and filter out removals
        val workingList = currentBundle.dynamicRules.filterNot { removeRuleIds.contains(it.id) }.toMutableList()

        // 2. Validate and add all new rules atomically
        for (newRule in addRules) {
            validateRule(newRule)
            if (workingList.any { it.id == newRule.id }) {
                throw IllegalArgumentException("DNR_RULE_DUPLICATE: Duplicate dynamic rule ID ${newRule.id}")
            }
            workingList.add(newRule)
        }

        // 3. Validate constraints
        if (workingList.size > MAX_DYNAMIC_RULES) {
            throw IllegalStateException("DNR_RULE_LIMIT_EXCEEDED: Dynamic rules limit of $MAX_DYNAMIC_RULES exceeded (found ${workingList.size})")
        }

        val regexCount = workingList.count { it.condition.regexFilter != null }
        if (regexCount > MAX_REGEX_RULES) {
            throw IllegalStateException("DNR_REGEX_LIMIT_EXCEEDED: Regex rules limit of $MAX_REGEX_RULES exceeded (found $regexCount)")
        }

        // 4. Commit atomic snapshot & persist
        val updatedBundle = currentBundle.copy(dynamicRules = workingList.toList())
        ruleStore[cleanExtId] = updatedBundle
        persistDynamicRules(cleanExtId, updatedBundle.dynamicRules)
        publishSnapshot(cleanExtId, updatedBundle)

        return updatedBundle.dynamicRules
    }

    /**
     * Atomically updates session rules for an extension.
     */
    @Synchronized
    fun updateSessionRules(
        extensionId: String,
        removeRuleIds: Set<Int>,
        addRules: List<DnrRule>
    ): List<DnrRule> {
        val cleanExtId = extensionId.lowercase().trim()
        val currentBundle = ruleStore[cleanExtId] ?: ExtensionRulesBundle()

        // 1. Snapshot and filter out removals
        val workingList = currentBundle.sessionRules.filterNot { removeRuleIds.contains(it.id) }.toMutableList()

        // 2. Validate and add all new rules atomically
        for (newRule in addRules) {
            validateRule(newRule)
            if (workingList.any { it.id == newRule.id }) {
                throw IllegalArgumentException("DNR_RULE_DUPLICATE: Duplicate session rule ID ${newRule.id}")
            }
            workingList.add(newRule)
        }

        // 3. Validate constraints
        if (workingList.size > MAX_SESSION_RULES) {
            throw IllegalStateException("DNR_RULE_LIMIT_EXCEEDED: Session rules limit of $MAX_SESSION_RULES exceeded (found ${workingList.size})")
        }

        val regexCount = workingList.count { it.condition.regexFilter != null }
        if (regexCount > MAX_REGEX_RULES) {
            throw IllegalStateException("DNR_REGEX_LIMIT_EXCEEDED: Regex rules limit of $MAX_REGEX_RULES exceeded (found $regexCount)")
        }

        // 4. Commit atomic snapshot (session rules are not saved to disk)
        val updatedBundle = currentBundle.copy(sessionRules = workingList.toList())
        ruleStore[cleanExtId] = updatedBundle
        publishSnapshot(cleanExtId, updatedBundle)

        return updatedBundle.sessionRules
    }

    /**
     * Registers static rules for an extension.
     */
    @Synchronized
    fun registerStaticRules(extensionId: String, rules: List<DnrRule>) {
        val cleanExtId = extensionId.lowercase().trim()
        rules.forEach { validateRule(it) }
        val currentBundle = ruleStore[cleanExtId] ?: ExtensionRulesBundle()
        val updatedBundle = currentBundle.copy(staticRules = rules.toList())
        ruleStore[cleanExtId] = updatedBundle
        publishSnapshot(cleanExtId, updatedBundle)
    }

    fun getDynamicRules(extensionId: String): List<DnrRule> {
        val cleanExtId = extensionId.lowercase().trim()
        return ruleStore[cleanExtId]?.dynamicRules ?: emptyList()
    }

    fun getSessionRules(extensionId: String): List<DnrRule> {
        val cleanExtId = extensionId.lowercase().trim()
        return ruleStore[cleanExtId]?.sessionRules ?: emptyList()
    }

    fun getStaticRules(extensionId: String): List<DnrRule> {
        val cleanExtId = extensionId.lowercase().trim()
        return ruleStore[cleanExtId]?.staticRules ?: emptyList()
    }

    fun getBundle(extensionId: String): ExtensionRulesBundle {
        val cleanExtId = extensionId.lowercase().trim()
        return ruleStore[cleanExtId] ?: ExtensionRulesBundle()
    }

    fun getSnapshot(extensionId: String): DnrRuleSnapshot? {
        val cleanExtId = extensionId.lowercase().trim()
        return snapshots[cleanExtId]
    }

    fun getAllSnapshots(): List<DnrRuleSnapshot> {
        return snapshots.values.toList()
    }

    fun clearExtension(extensionId: String) {
        val cleanExtId = extensionId.lowercase().trim()
        ruleStore.remove(cleanExtId)
        snapshots.remove(cleanExtId)
        removePersistedDynamicRules(cleanExtId)
    }

    fun clear() {
        ruleStore.clear()
        snapshots.clear()
    }

    /**
     * Compiles and publishes an immutable rule snapshot with pre-compiled matchers.
     */
    private fun publishSnapshot(extensionId: String, bundle: ExtensionRulesBundle) {
        val rawList = bundle.getAllRulesWithSource()
        val compiledList = mutableListOf<CompiledDnrRule>()

        for ((rule, source) in rawList) {
            try {
                val compiledRegex = rule.condition.regexFilter?.let { reg ->
                    val flags = if (!rule.condition.isUrlFilterCaseSensitive) Pattern.CASE_INSENSITIVE else 0
                    Pattern.compile(reg, flags)
                }

                val compiledUrlPattern = rule.condition.urlFilter?.let { uf ->
                    compileUrlFilterToPattern(uf, rule.condition.isUrlFilterCaseSensitive)
                }

                val cleanDomains = rule.condition.domains.map { it.lowercase().removePrefix(".") }.toSet()
                val cleanExDomains = rule.condition.excludedDomains.map { it.lowercase().removePrefix(".") }.toSet()
                val cleanInitDomains = rule.condition.initiatorDomains.map { it.lowercase().removePrefix(".") }.toSet()
                val cleanExInitDomains = rule.condition.excludedInitiatorDomains.map { it.lowercase().removePrefix(".") }.toSet()
                val resourceTypes = rule.condition.resourceTypes.map { it.lowercase() }.toSet()
                val exResourceTypes = rule.condition.excludedResourceTypes.map { it.lowercase() }.toSet()
                val requestMethods = rule.condition.requestMethods.map { it.uppercase() }.toSet()
                val exRequestMethods = rule.condition.excludedRequestMethods.map { it.uppercase() }.toSet()

                compiledList.add(
                    CompiledDnrRule(
                        rule = rule,
                        source = source,
                        extensionId = extensionId,
                        compiledRegex = compiledRegex,
                        compiledUrlFilterPattern = compiledUrlPattern,
                        cleanDomains = cleanDomains,
                        cleanExcludedDomains = cleanExDomains,
                        cleanInitiatorDomains = cleanInitDomains,
                        cleanExcludedInitiatorDomains = cleanExInitDomains,
                        resourceTypeSet = resourceTypes,
                        excludedResourceTypeSet = exResourceTypes,
                        requestMethodSet = requestMethods,
                        excludedRequestMethodSet = exRequestMethods
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        snapshots[extensionId] = DnrRuleSnapshot(
            extensionId = extensionId,
            rules = rawList,
            compiledRules = compiledList,
            generationId = System.currentTimeMillis()
        )
    }

    private fun compileUrlFilterToPattern(filter: String, caseSensitive: Boolean): Pattern? {
        if (filter.isBlank()) return null
        try {
            var p = filter
            var startAnchor = false
            var domainAnchor = false
            var endAnchor = false

            if (p.startsWith("||")) {
                domainAnchor = true
                p = p.substring(2)
            } else if (p.startsWith("|")) {
                startAnchor = true
                p = p.substring(1)
            }

            if (p.endsWith("|") && !p.endsWith("\\|")) {
                endAnchor = true
                p = p.substring(0, p.length - 1)
            }

            val sb = StringBuilder()
            if (domainAnchor) {
                sb.append("^https?://([^/]+\\.)?")
            } else if (startAnchor) {
                sb.append("^")
            }

            var i = 0
            while (i < p.length) {
                val c = p[i]
                when (c) {
                    '*' -> sb.append(".*")
                    '^' -> sb.append("([^a-zA-Z0-9_.-]|$)")
                    '.', '?', '+', '{', '}', '[', ']', '(', ')', '\\', '$' -> sb.append("\\").append(c)
                    else -> sb.append(c)
                }
                i++
            }

            if (endAnchor) {
                sb.append("$")
            }

            val flags = if (!caseSensitive) Pattern.CASE_INSENSITIVE else 0
            return Pattern.compile(sb.toString(), flags)
        } catch (e: Exception) {
            return null
        }
    }

    private fun validateRule(rule: DnrRule) {
        if (rule.id <= 0) {
            throw IllegalArgumentException("DNR_RULE_INVALID: Rule ID must be > 0")
        }
        val allowedActions = listOf("block", "allow", "redirect", "modifyheaders", "upgradescheme", "allowallrequests")
        if (rule.action.type.lowercase() !in allowedActions) {
            throw IllegalArgumentException("DNR_ACTION_UNSUPPORTED: Action type '${rule.action.type}' is not supported")
        }
        if (rule.action.type.equals("redirect", ignoreCase = true)) {
            val redirectUrl = rule.action.redirectUrl
            if (redirectUrl.isNullOrBlank()) {
                throw IllegalArgumentException("DNR_REDIRECT_UNSUPPORTED: Redirect action requires redirectUrl")
            }
        }
        if (rule.action.type.equals("modifyHeaders", ignoreCase = true)) {
            if (rule.action.requestHeaders.isEmpty() && rule.action.responseHeaders.isEmpty()) {
                throw IllegalArgumentException("DNR_HEADER_MODIFICATION_UNSUPPORTED: ModifyHeaders action requires requestHeaders or responseHeaders")
            }
        }

        // Header injections validation (CRLF / NUL)
        for (hdr in rule.action.requestHeaders) {
            if (!hdr.isValid()) {
                throw IllegalArgumentException("DNR_HEADER_MODIFICATION_UNSUPPORTED: Invalid request header ${hdr.header}")
            }
        }
        for (hdr in rule.action.responseHeaders) {
            if (!hdr.isValid()) {
                throw IllegalArgumentException("DNR_HEADER_MODIFICATION_UNSUPPORTED: Invalid response header ${hdr.header}")
            }
        }

        if (rule.condition.regexFilter != null) {
            validateRegex(rule.condition.regexFilter)
        }
    }

    private fun validateRegex(pattern: String) {
        if (pattern.length > 500) {
            throw IllegalArgumentException("DNR_REGEX_UNSUPPORTED: Regex pattern exceeds maximum allowed length of 500 chars")
        }
        val nestedQuantifier = Regex("(\\([^)]+\\)[*+?])[*+?]")
        if (nestedQuantifier.containsMatchIn(pattern) || pattern.contains("*+") || pattern.contains("++")) {
            throw IllegalArgumentException("DNR_REGEX_UNSUPPORTED: Catastrophic nested quantifiers are not allowed")
        }
        try {
            Regex(pattern)
        } catch (e: Exception) {
            throw IllegalArgumentException("DNR_REGEX_UNSUPPORTED: Invalid regex pattern: ${e.message}")
        }
    }

    private fun ruleToJson(rule: DnrRule): JSONObject {
        val obj = JSONObject()
        obj.put("id", rule.id)
        obj.put("priority", rule.priority)

        val actObj = JSONObject()
        actObj.put("type", rule.action.type)
        rule.action.redirectUrl?.let { actObj.put("redirectUrl", it) }

        if (rule.action.requestHeaders.isNotEmpty()) {
            val reqArr = JSONArray()
            rule.action.requestHeaders.forEach { h ->
                reqArr.put(JSONObject().apply {
                    put("header", h.header)
                    put("operation", h.operation)
                    h.value?.let { put("value", it) }
                })
            }
            actObj.put("requestHeaders", reqArr)
        }

        if (rule.action.responseHeaders.isNotEmpty()) {
            val resArr = JSONArray()
            rule.action.responseHeaders.forEach { h ->
                resArr.put(JSONObject().apply {
                    put("header", h.header)
                    put("operation", h.operation)
                    h.value?.let { put("value", it) }
                })
            }
            actObj.put("responseHeaders", resArr)
        }
        obj.put("action", actObj)

        val condObj = JSONObject()
        rule.condition.urlFilter?.let { condObj.put("urlFilter", it) }
        rule.condition.regexFilter?.let { condObj.put("regexFilter", it) }
        condObj.put("isUrlFilterCaseSensitive", rule.condition.isUrlFilterCaseSensitive)

        if (rule.condition.resourceTypes.isNotEmpty()) {
            condObj.put("resourceTypes", JSONArray(rule.condition.resourceTypes))
        }
        if (rule.condition.excludedResourceTypes.isNotEmpty()) {
            condObj.put("excludedResourceTypes", JSONArray(rule.condition.excludedResourceTypes))
        }
        if (rule.condition.domains.isNotEmpty()) {
            condObj.put("domains", JSONArray(rule.condition.domains))
        }
        if (rule.condition.excludedDomains.isNotEmpty()) {
            condObj.put("excludedDomains", JSONArray(rule.condition.excludedDomains))
        }
        if (rule.condition.initiatorDomains.isNotEmpty()) {
            condObj.put("initiatorDomains", JSONArray(rule.condition.initiatorDomains))
        }
        if (rule.condition.excludedInitiatorDomains.isNotEmpty()) {
            condObj.put("excludedInitiatorDomains", JSONArray(rule.condition.excludedInitiatorDomains))
        }
        if (rule.condition.requestMethods.isNotEmpty()) {
            condObj.put("requestMethods", JSONArray(rule.condition.requestMethods))
        }
        if (rule.condition.excludedRequestMethods.isNotEmpty()) {
            condObj.put("excludedRequestMethods", JSONArray(rule.condition.excludedRequestMethods))
        }
        rule.condition.domainType?.let { condObj.put("domainType", it) }

        obj.put("condition", condObj)
        return obj
    }

    private fun parseRulesJson(jsonStr: String): List<DnrRule> {
        val list = mutableListOf<DnrRule>()
        val jsonArr = JSONArray(jsonStr)
        for (i in 0 until jsonArr.length()) {
            val obj = jsonArr.getJSONObject(i)
            list.add(parseRuleObj(obj))
        }
        return list
    }

    private fun parseRuleObj(obj: JSONObject): DnrRule {
        val id = obj.getInt("id")
        val priority = obj.optInt("priority", 1)

        val actObj = obj.getJSONObject("action")
        val type = actObj.getString("type")
        val redirectUrl = actObj.optString("redirectUrl", null)

        val reqHeaders = mutableListOf<DnrHeader>()
        val reqArr = actObj.optJSONArray("requestHeaders")
        if (reqArr != null) {
            for (i in 0 until reqArr.length()) {
                val h = reqArr.getJSONObject(i)
                reqHeaders.add(
                    DnrHeader(
                        header = h.getString("header"),
                        operation = h.getString("operation"),
                        value = h.optString("value", null)
                    )
                )
            }
        }

        val resHeaders = mutableListOf<DnrHeader>()
        val resArr = actObj.optJSONArray("responseHeaders")
        if (resArr != null) {
            for (i in 0 until resArr.length()) {
                val h = resArr.getJSONObject(i)
                resHeaders.add(
                    DnrHeader(
                        header = h.getString("header"),
                        operation = h.getString("operation"),
                        value = h.optString("value", null)
                    )
                )
            }
        }

        val action = DnrAction(type, redirectUrl, reqHeaders, resHeaders)

        val condObj = obj.getJSONObject("condition")
        val urlFilter = condObj.optString("urlFilter", null)
        val regexFilter = condObj.optString("regexFilter", null)
        val isCaseSens = condObj.optBoolean("isUrlFilterCaseSensitive", false)

        val resTypes = jsonArrayToList(condObj.optJSONArray("resourceTypes"))
        val exResTypes = jsonArrayToList(condObj.optJSONArray("excludedResourceTypes"))
        val domains = jsonArrayToList(condObj.optJSONArray("domains"))
        val exDomains = jsonArrayToList(condObj.optJSONArray("excludedDomains"))
        val initDomains = jsonArrayToList(condObj.optJSONArray("initiatorDomains"))
        val exInitDomains = jsonArrayToList(condObj.optJSONArray("excludedInitiatorDomains"))
        val reqMethods = jsonArrayToList(condObj.optJSONArray("requestMethods"))
        val exReqMethods = jsonArrayToList(condObj.optJSONArray("excludedRequestMethods"))
        val domainType = condObj.optString("domainType", null)

        val condition = DnrCondition(
            urlFilter = urlFilter,
            regexFilter = regexFilter,
            isUrlFilterCaseSensitive = isCaseSens,
            resourceTypes = resTypes,
            excludedResourceTypes = exResTypes,
            domains = domains,
            excludedDomains = exDomains,
            initiatorDomains = initDomains,
            excludedInitiatorDomains = exInitDomains,
            requestMethods = reqMethods,
            excludedRequestMethods = exReqMethods,
            domainType = domainType
        )

        return DnrRule(id, priority, action, condition)
    }

    private fun jsonArrayToList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            list.add(arr.getString(i))
        }
        return list
    }
}
