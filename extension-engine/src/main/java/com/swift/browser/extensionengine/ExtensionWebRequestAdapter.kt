package com.swift.browser.extensionengine

import org.json.JSONObject

data class WebRequestData(
    val requestId: String,
    val url: String,
    val method: String,
    val type: String,
    val tabId: Int,
    val frameId: Int = 0,
    val documentId: String? = null,
    val timeStamp: Long = System.currentTimeMillis(),
    val isPrivate: Boolean = false
)

sealed class WebRequestInterceptResult {
    object Continue : WebRequestInterceptResult()
    object Blocked : WebRequestInterceptResult()
    data class Redirect(val redirectUrl: String) : WebRequestInterceptResult()
    data class ModifyHeaders(
        val requestHeaders: List<DnrHeader> = emptyList(),
        val responseHeaders: List<DnrHeader> = emptyList()
    ) : WebRequestInterceptResult()
}

/**
 * Adapter implementing Chrome WebRequest API (chrome.webRequest.*) and network interception.
 * Uses the canonical ExtensionPermissionAdapter, unified ExtensionDnrAdapter,
 * WebRequestSubscriptionRegistry, and WebRequestIdMapper.
 */
class ExtensionWebRequestAdapter(
    private val permissionAdapter: ExtensionPermissionAdapter,
    private val registry: ExtensionRegistry,
    private val eventManager: EventManager,
    private val dnrAdapter: ExtensionDnrAdapter,
    val subscriptionRegistry: WebRequestSubscriptionRegistry = WebRequestSubscriptionRegistry(),
    val requestRegistry: WebRequestRegistry = WebRequestRegistry(),
    val idMapper: WebRequestIdMapper = WebRequestIdMapper()
) {
    /**
     * Compatibility constructor for legacy code/tests taking PermissionManager.
     */
    constructor(
        permissionManager: PermissionManager,
        registry: ExtensionRegistry,
        eventManager: EventManager,
        dnrAdapter: ExtensionDnrAdapter
    ) : this(
        permissionAdapter = ExtensionPermissionAdapter(permissionManager.context).apply {
            setRegistry(registry)
            setEventManager(eventManager)
        },
        registry = registry,
        eventManager = eventManager,
        dnrAdapter = dnrAdapter
    )

    fun notifyEvent(eventName: String, request: ExtensionNetworkRequestContext, extraDetails: JSONObject? = null) {
        val enabledExts = registry.getEnabledExtensions()
        val cleanEvent = if (eventName.startsWith("webRequest.")) eventName else "webRequest.$eventName"

        // Track lifecycle in request registry
        when (eventName.removePrefix("webRequest.")) {
            "onBeforeRequest" -> requestRegistry.trackRequest(request)
            "onCompleted" -> requestRegistry.completeRequest(request.requestId)
            "onErrorOccurred" -> requestRegistry.errorRequest(request.requestId, extraDetails?.optString("error", "unknown") ?: "error")
        }

        // Get matching subscriptions
        val matchingSubs = subscriptionRegistry.getMatchingSubscriptions(cleanEvent, request)
        val subscribedExtIds = matchingSubs.map { it.extensionId.lowercase().trim() }.toSet()

        for (ext in enabledExts) {
            val extId = ext.id.lowercase().trim()
            if (request.isPrivate && !permissionAdapter.isAllowedInPrivate(extId)) continue

            // Canonical Permission Checks via ExtensionPermissionAdapter ONLY
            val hasWebRequestPerm = permissionAdapter.hasApiPermission(extId, "webRequest", request.isPrivate) ||
                    permissionAdapter.hasApiPermission(extId, "webRequestBlocking", request.isPrivate) ||
                    permissionAdapter.hasApiPermission(extId, "declarativeNetRequest", request.isPrivate)

            if (!hasWebRequestPerm) continue

            val hasHostPerm = permissionAdapter.hasHostPermission(extId, request.url, request.isPrivate)
            if (!hasHostPerm) continue

            // If extension registered explicit subscription with filters, ensure it matches
            if (subscriptionRegistry.hasSubscribers(cleanEvent) && !subscribedExtIds.contains(extId)) {
                if (!eventManager.hasListener(cleanEvent, extId)) {
                    continue
                }
            }

            val payload = request.toEventPayload(extraDetails)
            eventManager.triggerEventForExtension(extId, cleanEvent, payload)
        }
    }

    fun notifyEvent(eventName: String, request: WebRequestData, extraDetails: JSONObject? = null) {
        val context = ExtensionNetworkRequestContext.fromWebRequestData(request)
        notifyEvent(eventName, context, extraDetails)
    }

    fun interceptRequest(request: ExtensionNetworkRequestContext): WebRequestInterceptResult {
        val dnrMatch = dnrAdapter.evaluateRequest(
            url = request.url,
            resourceType = request.resourceType,
            requestHeaders = request.requestHeaders,
            isPrivate = request.isPrivate,
            initiator = request.initiator,
            method = request.method
        )

        if (dnrMatch != null) {
            when (dnrMatch.actionType.lowercase()) {
                "block" -> {
                    notifyEvent("onErrorOccurred", request, JSONObject().put("error", "net::ERR_BLOCKED_BY_CLIENT"))
                    return WebRequestInterceptResult.Blocked
                }
                "redirect", "upgradescheme" -> {
                    val redirectUrl = dnrMatch.redirectUrl
                    if (!redirectUrl.isNullOrBlank()) {
                        notifyEvent("onBeforeRedirect", request, JSONObject().put("redirectUrl", redirectUrl))
                        return WebRequestInterceptResult.Redirect(redirectUrl)
                    }
                }
                "modifyheaders" -> {
                    notifyEvent("onBeforeRequest", request)
                    return WebRequestInterceptResult.ModifyHeaders(
                        requestHeaders = dnrMatch.requestHeaders,
                        responseHeaders = dnrMatch.responseHeaders
                    )
                }
                "allow", "allowallrequests" -> {
                    notifyEvent("onBeforeRequest", request)
                    return WebRequestInterceptResult.Continue
                }
            }
        }

        notifyEvent("onBeforeRequest", request)
        return WebRequestInterceptResult.Continue
    }

    fun interceptRequest(request: WebRequestData): WebRequestInterceptResult {
        val context = ExtensionNetworkRequestContext.fromWebRequestData(request)
        return interceptRequest(context)
    }
}
