package com.swift.browser.extensionengine

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Adapter implementing the Chrome declarativeNetRequest (DNR) API.
 * Uses the canonical ExtensionPermissionAdapter and the unified ExtensionDnrRuleStore.
 */
class ExtensionDnrAdapter(
    private val permissionAdapter: ExtensionPermissionAdapter,
    private val registry: ExtensionRegistry,
    private val ruleStore: ExtensionDnrRuleStore = ExtensionDnrRuleStore()
) {
    /**
     * Compatibility constructor for legacy code/tests taking PermissionManager.
     */
    constructor(
        permissionManager: PermissionManager,
        registry: ExtensionRegistry
    ) : this(
        permissionAdapter = ExtensionPermissionAdapter(permissionManager.context).apply {
            setRegistry(registry)
        },
        registry = registry
    )

    private val matchedRulesHistory = ConcurrentHashMap<String, CopyOnWriteArrayList<DnrMatchResult>>()

    fun updateDynamicRules(sender: ExtensionSender, options: JSONObject): JSONObject {
        val extId = sender.extensionId
        validateDnrPermission(sender)

        val removeIds = mutableSetOf<Int>()
        val removeArray = options.optJSONArray("removeRuleIds")
        if (removeArray != null) {
            for (i in 0 until removeArray.length()) {
                removeIds.add(removeArray.getInt(i))
            }
        }

        val addList = mutableListOf<DnrRule>()
        val addArray = options.optJSONArray("addRules")
        if (addArray != null) {
            for (i in 0 until addArray.length()) {
                val ruleObj = addArray.getJSONObject(i)
                addList.add(parseRule(ruleObj))
            }
        }

        ruleStore.updateDynamicRules(extId, removeIds, addList)
        return JSONObject().put("status", "success")
    }

    fun getDynamicRules(sender: ExtensionSender): JSONArray {
        validateDnrPermission(sender)
        val rules = ruleStore.getDynamicRules(sender.extensionId)
        val array = JSONArray()
        rules.forEach { array.put(ruleToJson(it)) }
        return array
    }

    fun updateSessionRules(sender: ExtensionSender, options: JSONObject): JSONObject {
        val extId = sender.extensionId
        validateDnrPermission(sender)

        val removeIds = mutableSetOf<Int>()
        val removeArray = options.optJSONArray("removeRuleIds")
        if (removeArray != null) {
            for (i in 0 until removeArray.length()) {
                removeIds.add(removeArray.getInt(i))
            }
        }

        val addList = mutableListOf<DnrRule>()
        val addArray = options.optJSONArray("addRules")
        if (addArray != null) {
            for (i in 0 until addArray.length()) {
                val ruleObj = addArray.getJSONObject(i)
                addList.add(parseRule(ruleObj))
            }
        }

        ruleStore.updateSessionRules(extId, removeIds, addList)
        return JSONObject().put("status", "success")
    }

    fun getSessionRules(sender: ExtensionSender): JSONArray {
        validateDnrPermission(sender)
        val rules = ruleStore.getSessionRules(sender.extensionId)
        val array = JSONArray()
        rules.forEach { array.put(ruleToJson(it)) }
        return array
    }

    fun getMatchedRules(sender: ExtensionSender, options: JSONObject? = null): JSONObject {
        validateDnrPermission(sender)
        val extId = sender.extensionId
        val list = matchedRulesHistory[extId] ?: emptyList()
        val rulesArr = JSONArray()
        for (match in list) {
            rulesArr.put(JSONObject().apply {
                put("ruleId", match.matchedRuleId)
                put("timeStamp", match.timeStamp.toDouble())
            })
        }
        return JSONObject().apply {
            put("rulesMatchedInfo", rulesArr)
        }
    }

    fun isRegexSupported(sender: ExtensionSender, regexOptions: JSONObject): JSONObject {
        validateDnrPermission(sender)
        val regex = regexOptions.optString("regex", "")
        val isCaseSensitive = regexOptions.optBoolean("isCaseSensitive", false)
        return try {
            if (regex.length > 500) {
                JSONObject().put("isSupported", false).put("reason", "regex_too_long")
            } else {
                val nestedQuantifier = Regex("(\\([^)]+\\)[*+?])[*+?]")
                if (nestedQuantifier.containsMatchIn(regex) || regex.contains("*+") || regex.contains("++")) {
                    JSONObject().put("isSupported", false).put("reason", "catastrophic_backtracking")
                } else {
                    Regex(regex, if (isCaseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE))
                    JSONObject().put("isSupported", true)
                }
            }
        } catch (e: Exception) {
            JSONObject().put("isSupported", false).put("reason", "syntax_error")
        }
    }

    fun setExtensionActionOptions(sender: ExtensionSender, options: JSONObject): JSONObject {
        validateDnrPermission(sender)
        return JSONObject().put("status", "success")
    }

    fun registerStaticRules(extensionId: String, rules: List<DnrRule>) {
        ruleStore.registerStaticRules(extensionId, rules)
    }

    fun evaluateRequest(
        url: String,
        resourceType: String,
        requestHeaders: Map<String, String> = emptyMap(),
        isPrivate: Boolean = false,
        initiator: String? = null,
        method: String = "GET"
    ): DnrMatchResult? {
        val snapshots = ruleStore.getAllSnapshots()
        if (snapshots.isEmpty()) return null

        val candidateMatches = mutableListOf<MatchedCandidate>()

        val host = try {
            val uri = Uri.parse(url)
            uri.host?.lowercase() ?: ""
        } catch (e: Exception) { "" }

        val initHost = if (initiator != null) {
            try {
                val uri = Uri.parse(initiator)
                uri.host?.lowercase() ?: ""
            } catch (e: Exception) { "" }
        } else null

        for (snapshot in snapshots) {
            val extId = snapshot.extensionId
            if (!registry.isExtensionEnabled(extId)) continue
            if (isPrivate && !permissionAdapter.isAllowedInPrivate(extId)) continue

            // Canonical Permission Check via ExtensionPermissionAdapter ONLY
            val hasDnrPermission = permissionAdapter.hasApiPermission(extId, "declarativeNetRequest", isPrivate) ||
                    permissionAdapter.hasApiPermission(extId, "declarativeNetRequestWithHostAccess", isPrivate) ||
                    permissionAdapter.hasApiPermission(extId, "declarativeNetRequestFeedback", isPrivate)

            if (!hasDnrPermission) continue

            for (compiled in snapshot.compiledRules) {
                if (matchesCompiledRule(compiled, url, host, resourceType, method, initiator, initHost)) {
                    val actionType = compiled.rule.action.type.lowercase()
                    // Host permission check for redirect, modifyHeaders, upgradeScheme
                    if (actionType == "redirect" || actionType == "modifyheaders" || actionType == "upgradescheme") {
                        if (!permissionAdapter.hasHostPermission(extId, url, isPrivate)) {
                            continue
                        }
                    }
                    candidateMatches.add(
                        MatchedCandidate(
                            extensionId = extId,
                            rule = compiled.rule,
                            source = compiled.source,
                            priority = compiled.rule.priority
                        )
                    )
                }
            }
        }

        if (candidateMatches.isEmpty()) return null

        // Priority resolution:
        // 1. Higher rule priority
        // 2. Action preference: allowAllRequests / allow (4) > block (3) > upgradeScheme / redirect (2) > modifyHeaders (1)
        // 3. Rule source: session (3) > dynamic (2) > static (1)
        val actionPreference = mapOf(
            "allowallrequests" to 4,
            "allow" to 4,
            "block" to 3,
            "upgradescheme" to 2,
            "redirect" to 2,
            "modifyheaders" to 1
        )
        val sourcePreference = mapOf(
            DnrRuleSource.SESSION to 3,
            DnrRuleSource.DYNAMIC to 2,
            DnrRuleSource.STATIC to 1
        )

        val best = candidateMatches.maxWithOrNull(
            compareBy<MatchedCandidate> { it.priority }
                .thenBy { actionPreference[it.rule.action.type.lowercase()] ?: 0 }
                .thenBy { sourcePreference[it.source] ?: 0 }
        ) ?: return null

        val resolvedRedirectUrl = if (best.rule.action.type.equals("upgradeScheme", ignoreCase = true)) {
            if (url.startsWith("http://", ignoreCase = true)) {
                "https://" + url.substring(7)
            } else url
        } else {
            best.rule.action.redirectUrl
        }

        val result = DnrMatchResult(
            matchedRuleId = best.rule.id,
            extensionId = best.extensionId,
            actionType = best.rule.action.type,
            redirectUrl = resolvedRedirectUrl,
            requestHeaders = best.rule.action.requestHeaders,
            responseHeaders = best.rule.action.responseHeaders
        )

        // Record matched rule history for declarativeNetRequestFeedback
        val history = matchedRulesHistory.getOrPut(best.extensionId) { CopyOnWriteArrayList() }
        if (history.size > 200) history.removeAt(0)
        history.add(result)

        return result
    }

    private fun matchesCompiledRule(
        compiled: CompiledDnrRule,
        url: String,
        host: String,
        resourceType: String,
        method: String,
        initiator: String?,
        initHost: String?
    ): Boolean {
        val cond = compiled.rule.condition

        // Resource types
        val lowerResType = resourceType.lowercase()
        if (compiled.resourceTypeSet.isNotEmpty() && lowerResType !in compiled.resourceTypeSet) return false
        if (compiled.excludedResourceTypeSet.isNotEmpty() && lowerResType in compiled.excludedResourceTypeSet) return false

        // Request methods
        val upperMethod = method.uppercase()
        if (compiled.requestMethodSet.isNotEmpty() && upperMethod !in compiled.requestMethodSet) return false
        if (compiled.excludedRequestMethodSet.isNotEmpty() && upperMethod in compiled.excludedRequestMethodSet) return false

        // Request domains with strict boundary check
        if (compiled.cleanDomains.isNotEmpty()) {
            val match = compiled.cleanDomains.any { d -> host == d || host.endsWith(".$d") }
            if (!match) return false
        }
        if (compiled.cleanExcludedDomains.isNotEmpty()) {
            val exc = compiled.cleanExcludedDomains.any { d -> host == d || host.endsWith(".$d") }
            if (exc) return false
        }

        // Initiator domains with strict boundary check
        if (initHost != null) {
            if (compiled.cleanInitiatorDomains.isNotEmpty()) {
                val match = compiled.cleanInitiatorDomains.any { d -> initHost == d || initHost.endsWith(".$d") }
                if (!match) return false
            }
            if (compiled.cleanExcludedInitiatorDomains.isNotEmpty()) {
                val exc = compiled.cleanExcludedInitiatorDomains.any { d -> initHost == d || initHost.endsWith(".$d") }
                if (exc) return false
            }

            // Domain type (firstParty / thirdParty)
            if (cond.domainType != null && host.isNotEmpty() && initHost.isNotEmpty()) {
                val isFirstParty = host == initHost || host.endsWith(".$initHost") || initHost.endsWith(".$host")
                if (cond.domainType.equals("firstParty", ignoreCase = true) && !isFirstParty) return false
                if (cond.domainType.equals("thirdParty", ignoreCase = true) && isFirstParty) return false
            }
        }

        // URL filter matching
        if (compiled.compiledUrlFilterPattern != null) {
            if (!compiled.compiledUrlFilterPattern.matcher(url).find()) {
                return false
            }
        } else if (cond.urlFilter != null) {
            val targetUrl = if (cond.isUrlFilterCaseSensitive) url else url.lowercase()
            val targetFilter = if (cond.isUrlFilterCaseSensitive) cond.urlFilter else cond.urlFilter.lowercase()
            if (!targetUrl.contains(targetFilter)) return false
        }

        // Regex filter matching
        if (compiled.compiledRegex != null) {
            if (!compiled.compiledRegex.matcher(url).find()) {
                return false
            }
        }

        return true
    }

    private data class MatchedCandidate(
        val extensionId: String,
        val rule: DnrRule,
        val source: DnrRuleSource,
        val priority: Int
    )

    private fun validateDnrPermission(sender: ExtensionSender) {
        val extId = sender.extensionId
        if (!registry.isExtensionEnabled(extId)) {
            throw SecurityException("Extension is disabled: $extId")
        }
        if (sender.isPrivate && !permissionAdapter.isAllowedInPrivate(extId)) {
            throw SecurityException("SecurityError: Extension not allowed in private mode")
        }
        val hasPerm = permissionAdapter.hasApiPermission(extId, "declarativeNetRequest", sender.isPrivate) ||
                permissionAdapter.hasApiPermission(extId, "declarativeNetRequestWithHostAccess", sender.isPrivate) ||
                permissionAdapter.hasApiPermission(extId, "declarativeNetRequestFeedback", sender.isPrivate)

        if (!hasPerm) {
            throw SecurityException("SecurityError: Missing declarativeNetRequest permission")
        }
    }

    private fun parseRule(obj: JSONObject): DnrRule {
        val id = obj.getInt("id")
        val priority = obj.optInt("priority", 1)
        val actionObj = obj.getJSONObject("action")
        val actionType = actionObj.getString("type")
        val redirectUrl = if (actionObj.has("redirectUrl")) actionObj.getString("redirectUrl") else null

        val reqHeadersList = mutableListOf<DnrHeader>()
        val reqHeadersArray = actionObj.optJSONArray("requestHeaders")
        if (reqHeadersArray != null) {
            for (i in 0 until reqHeadersArray.length()) {
                val h = reqHeadersArray.getJSONObject(i)
                reqHeadersList.add(
                    DnrHeader(
                        header = h.getString("header"),
                        operation = h.optString("operation", "set"),
                        value = if (h.has("value")) h.getString("value") else null
                    )
                )
            }
        }

        val resHeadersList = mutableListOf<DnrHeader>()
        val resHeadersArray = actionObj.optJSONArray("responseHeaders")
        if (resHeadersArray != null) {
            for (i in 0 until resHeadersArray.length()) {
                val h = resHeadersArray.getJSONObject(i)
                resHeadersList.add(
                    DnrHeader(
                        header = h.getString("header"),
                        operation = h.optString("operation", "set"),
                        value = if (h.has("value")) h.getString("value") else null
                    )
                )
            }
        }

        val action = DnrAction(actionType, redirectUrl, reqHeadersList, resHeadersList)

        val condObj = obj.optJSONObject("condition") ?: JSONObject()
        val urlFilter = if (condObj.has("urlFilter")) condObj.getString("urlFilter") else null
        val regexFilter = if (condObj.has("regexFilter")) condObj.getString("regexFilter") else null
        val caseSensitive = condObj.optBoolean("isUrlFilterCaseSensitive", false)

        val resTypes = parseStringList(condObj.optJSONArray("resourceTypes"))
        val excResTypes = parseStringList(condObj.optJSONArray("excludedResourceTypes"))
        val domains = parseStringList(condObj.optJSONArray("domains"))
        val excDomains = parseStringList(condObj.optJSONArray("excludedDomains"))
        val reqDomains = parseStringList(condObj.optJSONArray("requestDomains"))
        val excReqDomains = parseStringList(condObj.optJSONArray("excludedRequestDomains"))
        val initDomains = parseStringList(condObj.optJSONArray("initiatorDomains"))
        val excInitDomains = parseStringList(condObj.optJSONArray("excludedInitiatorDomains"))
        val reqMethods = parseStringList(condObj.optJSONArray("requestMethods"))
        val excReqMethods = parseStringList(condObj.optJSONArray("excludedRequestMethods"))
        val domainType = if (condObj.has("domainType")) condObj.getString("domainType") else null

        val condition = DnrCondition(
            urlFilter = urlFilter,
            regexFilter = regexFilter,
            isUrlFilterCaseSensitive = caseSensitive,
            resourceTypes = resTypes,
            excludedResourceTypes = excResTypes,
            domains = domains.ifEmpty { reqDomains },
            excludedDomains = excDomains.ifEmpty { excReqDomains },
            requestDomains = reqDomains,
            excludedRequestDomains = excReqDomains,
            initiatorDomains = initDomains,
            excludedInitiatorDomains = excInitDomains,
            requestMethods = reqMethods,
            excludedRequestMethods = excReqMethods,
            domainType = domainType
        )

        return DnrRule(id, priority, action, condition)
    }

    private fun parseStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until array.length()) {
            list.add(array.getString(i))
        }
        return list
    }

    private fun ruleToJson(rule: DnrRule): JSONObject {
        val actionObj = JSONObject().apply {
            put("type", rule.action.type)
            if (rule.action.redirectUrl != null) put("redirectUrl", rule.action.redirectUrl)
            if (rule.action.requestHeaders.isNotEmpty()) {
                val arr = JSONArray()
                rule.action.requestHeaders.forEach { h ->
                    arr.put(JSONObject().apply {
                        put("header", h.header)
                        put("operation", h.operation)
                        if (h.value != null) put("value", h.value)
                    })
                }
                put("requestHeaders", arr)
            }
            if (rule.action.responseHeaders.isNotEmpty()) {
                val arr = JSONArray()
                rule.action.responseHeaders.forEach { h ->
                    arr.put(JSONObject().apply {
                        put("header", h.header)
                        put("operation", h.operation)
                        if (h.value != null) put("value", h.value)
                    })
                }
                put("responseHeaders", arr)
            }
        }
        val condObj = JSONObject().apply {
            if (rule.condition.urlFilter != null) put("urlFilter", rule.condition.urlFilter)
            if (rule.condition.regexFilter != null) put("regexFilter", rule.condition.regexFilter)
            put("isUrlFilterCaseSensitive", rule.condition.isUrlFilterCaseSensitive)
            if (rule.condition.resourceTypes.isNotEmpty()) put("resourceTypes", JSONArray(rule.condition.resourceTypes))
            if (rule.condition.excludedResourceTypes.isNotEmpty()) put("excludedResourceTypes", JSONArray(rule.condition.excludedResourceTypes))
            if (rule.condition.domains.isNotEmpty()) put("domains", JSONArray(rule.condition.domains))
            if (rule.condition.excludedDomains.isNotEmpty()) put("excludedDomains", JSONArray(rule.condition.excludedDomains))
            if (rule.condition.requestDomains.isNotEmpty()) put("requestDomains", JSONArray(rule.condition.requestDomains))
            if (rule.condition.excludedRequestDomains.isNotEmpty()) put("excludedRequestDomains", JSONArray(rule.condition.excludedRequestDomains))
            if (rule.condition.initiatorDomains.isNotEmpty()) put("initiatorDomains", JSONArray(rule.condition.initiatorDomains))
            if (rule.condition.excludedInitiatorDomains.isNotEmpty()) put("excludedInitiatorDomains", JSONArray(rule.condition.excludedInitiatorDomains))
            if (rule.condition.requestMethods.isNotEmpty()) put("requestMethods", JSONArray(rule.condition.requestMethods))
            if (rule.condition.excludedRequestMethods.isNotEmpty()) put("excludedRequestMethods", JSONArray(rule.condition.excludedRequestMethods))
            if (rule.condition.domainType != null) put("domainType", rule.condition.domainType)
        }
        return JSONObject().apply {
            put("id", rule.id)
            put("priority", rule.priority)
            put("action", actionObj)
            put("condition", condObj)
        }
    }
}
