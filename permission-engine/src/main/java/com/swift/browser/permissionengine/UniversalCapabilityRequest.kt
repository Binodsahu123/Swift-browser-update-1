package com.swift.browser.permissionengine

data class UniversalCapabilityRequest(
    val requestId: String = "req_" + java.util.UUID.randomUUID().toString().substring(0, 8),
    val capabilityId: String,
    val requestedResources: List<String> = emptyList(),
    val origin: String,
    val topLevelOrigin: String = origin,
    val frameOrigin: String? = null,
    val scheme: String = "",
    val host: String = "",
    val port: Int = -1,
    val tabId: String = "default_tab",
    val frameId: String? = null,
    val incognito: Boolean = false,
    val browsingMode: String = if (incognito) "INCOGNITO" else "NORMAL",
    val userGesture: Boolean? = null,
    val requestSource: String = "website",
    val webApiName: String = "",
    val webViewResourceNames: List<String> = emptyList(),
    val requestedAndroidPermissions: List<String> = emptyList(),
    val hardwareRequirements: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val expiration: Long? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    fun toDynamicOrigin(): DynamicOrigin {
        return DynamicOrigin(
            canonicalOrigin = origin,
            scheme = scheme,
            host = host,
            port = port,
            topLevelOrigin = topLevelOrigin,
            frameOrigin = frameOrigin,
            tabId = tabId,
            isIncognito = incognito,
            isUserGesture = userGesture,
            requestSource = requestSource,
            requestId = requestId,
            apiName = webApiName.ifBlank { capabilityId }
        )
    }

    fun toPermissionRequestModel(): PermissionRequestModel {
        val primaryResource = capabilityId
        val resList = if (requestedResources.isNotEmpty()) requestedResources else listOf(primaryResource)
        return PermissionRequestModel(
            requestId = requestId,
            origin = origin,
            siteUrl = topLevelOrigin,
            pageUrl = frameOrigin ?: origin,
            frameId = frameId ?: "main",
            tabId = tabId,
            requestSourceType = requestSource,
            permissionType = capabilityId,
            resourcesRequested = resList,
            isUserGesture = userGesture,
            isTopLevelFrame = frameOrigin == null || frameOrigin == topLevelOrigin,
            isSecureOrigin = PermissionPolicyResolver.isSecureOrigin(origin),
            isIncognito = incognito,
            riskLevel = PermissionDescriptorRegistry.getDescriptor(capabilityId)?.riskLevel ?: "Medium",
            timestamp = timestamp,
            metadata = metadata
        )
    }

    companion object {
        fun builder(
            rawUrl: String,
            capabilityId: String,
            topLevelUrl: String? = null,
            frameUrl: String? = null
        ): Builder = Builder(rawUrl, capabilityId, topLevelUrl, frameUrl)

        class Builder(
            private val rawUrl: String,
            private val capabilityId: String,
            private val topLevelUrl: String? = null,
            private val frameUrl: String? = null
        ) {
            private var requestedResources: List<String> = emptyList()
            private var tabId: String = "default_tab"
            private var frameId: String? = null
            private var incognito: Boolean = false
            private var userGesture: Boolean? = null
            private var requestSource: String = "website"
            private var webApiName: String = ""
            private var webViewResourceNames: List<String> = emptyList()
            private var requestedAndroidPermissions: List<String> = emptyList()
            private var hardwareRequirements: List<String> = emptyList()
            private var expiration: Long? = null
            private var metadata: Map<String, String> = emptyMap()
            private var customRequestId: String? = null

            fun setRequestedResources(res: List<String>) = apply { this.requestedResources = res }
            fun setTabId(tabId: String) = apply { this.tabId = tabId }
            fun setFrameId(frameId: String?) = apply { this.frameId = frameId }
            fun setIncognito(incognito: Boolean) = apply { this.incognito = incognito }
            fun setUserGesture(userGesture: Boolean?) = apply { this.userGesture = userGesture }
            fun setRequestSource(source: String) = apply { this.requestSource = source }
            fun setWebApiName(name: String) = apply { this.webApiName = name }
            fun setWebViewResourceNames(names: List<String>) = apply { this.webViewResourceNames = names }
            fun setRequestedAndroidPermissions(perms: List<String>) = apply { this.requestedAndroidPermissions = perms }
            fun setHardwareRequirements(hw: List<String>) = apply { this.hardwareRequirements = hw }
            fun setExpiration(exp: Long?) = apply { this.expiration = exp }
            fun setMetadata(meta: Map<String, String>) = apply { this.metadata = meta }
            fun setRequestId(reqId: String) = apply { this.customRequestId = reqId }

            fun build(): UniversalCapabilityRequest {
                val dynOrigin = DynamicOrigin.parse(
                    rawUrl = rawUrl,
                    topLevelUrl = topLevelUrl,
                    frameUrl = frameUrl,
                    tabId = tabId,
                    isIncognito = incognito,
                    isUserGesture = userGesture,
                    requestSource = requestSource,
                    apiName = webApiName
                )

                val descriptor = PermissionDescriptorRegistry.getDescriptor(capabilityId)
                    ?: PermissionDescriptorRegistry.getDescriptorForResource(capabilityId)

                val androidPerms = if (requestedAndroidPermissions.isNotEmpty()) {
                    requestedAndroidPermissions
                } else {
                    descriptor?.androidPermissions ?: emptyList()
                }

                val hwReqs = if (hardwareRequirements.isNotEmpty()) {
                    hardwareRequirements
                } else {
                    descriptor?.hardwareFeature?.let { listOf(it) } ?: emptyList()
                }

                val resList = if (requestedResources.isNotEmpty()) {
                    requestedResources
                } else {
                    descriptor?.webViewResources?.ifEmpty { listOf(capabilityId) } ?: listOf(capabilityId)
                }

                val finalReqId = customRequestId ?: dynOrigin.requestId

                return UniversalCapabilityRequest(
                    requestId = finalReqId,
                    capabilityId = descriptor?.capabilityId ?: capabilityId.uppercase(),
                    requestedResources = resList,
                    origin = dynOrigin.canonicalOrigin,
                    topLevelOrigin = dynOrigin.topLevelOrigin,
                    frameOrigin = dynOrigin.frameOrigin,
                    scheme = dynOrigin.scheme,
                    host = dynOrigin.host,
                    port = dynOrigin.port,
                    tabId = tabId,
                    frameId = frameId,
                    browsingMode = if (incognito) "INCOGNITO" else "NORMAL",
                    incognito = incognito,
                    userGesture = userGesture,
                    requestSource = requestSource,
                    webApiName = webApiName.ifBlank { descriptor?.webApiSource ?: capabilityId },
                    webViewResourceNames = webViewResourceNames.ifEmpty { descriptor?.webViewResources ?: emptyList() },
                    requestedAndroidPermissions = androidPerms,
                    hardwareRequirements = hwReqs,
                    timestamp = System.currentTimeMillis(),
                    expiration = expiration,
                    metadata = metadata
                )
            }
        }
    }
}
