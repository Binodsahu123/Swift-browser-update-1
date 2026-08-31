package com.swift.browser.permissionengine

object PermissionRequestNormalizer {

    fun normalize(request: UniversalCapabilityRequest): UniversalCapabilityRequest {
        return request
    }

    fun normalize(model: PermissionRequestModel): UniversalCapabilityRequest {
        return WebViewPermissionAdapter.adapt(model)
    }

    fun normalizeContext(
        context: PermissionRequestContext,
        capabilityId: String,
        requestedResources: List<String> = emptyList()
    ): UniversalCapabilityRequest {
        return UniversalCapabilityRequest.builder(
            rawUrl = context.origin,
            capabilityId = capabilityId,
            topLevelUrl = context.pageUrl,
            frameUrl = context.origin
        )
            .setRequestId(context.requestId)
            .setRequestedResources(requestedResources)
            .setTabId(context.tabId)
            .setFrameId(context.frameId)
            .setIncognito(context.isIncognito)
            .setUserGesture(context.isUserGesture)
            .setRequestSource(context.requestSource)
            .build()
    }

    fun normalizeDynamicOrigin(
        dynamicOrigin: DynamicOrigin,
        capabilityId: String,
        requestedResources: List<String> = emptyList()
    ): UniversalCapabilityRequest {
        return UniversalCapabilityRequest.builder(
            rawUrl = dynamicOrigin.canonicalOrigin,
            capabilityId = capabilityId,
            topLevelUrl = dynamicOrigin.topLevelOrigin,
            frameUrl = dynamicOrigin.frameOrigin
        )
            .setRequestId(dynamicOrigin.requestId)
            .setRequestedResources(requestedResources)
            .setTabId(dynamicOrigin.tabId)
            .setIncognito(dynamicOrigin.isIncognito)
            .setUserGesture(dynamicOrigin.isUserGesture)
            .setRequestSource(dynamicOrigin.requestSource)
            .setWebApiName(dynamicOrigin.apiName)
            .build()
    }

    fun normalizeWebViewResources(
        origin: String,
        resources: Array<String>,
        tabId: String = "default_tab",
        isIncognito: Boolean = false,
        requestId: String = "req_" + java.util.UUID.randomUUID().toString().substring(0, 8)
    ): UniversalCapabilityRequest {
        return WebViewPermissionAdapter.adaptRawResources(
            origin = origin,
            resources = resources,
            tabId = tabId,
            isIncognito = isIncognito,
            requestId = requestId
        )
    }

    fun normalizeFileCapture(params: FileCaptureRequestParams): UniversalCapabilityRequest {
        return FileCaptureAdapter.adapt(params)
    }

    fun normalizePopupWindow(params: PopupWindowRequestParams): UniversalCapabilityRequest {
        return PopupWindowAdapter.adapt(params)
    }

    fun normalizeSpeechRecognition(params: SpeechRecognitionRequestParams): UniversalCapabilityRequest {
        return SpeechRecognitionAdapter.adapt(params)
    }

    fun normalizeNotification(params: NotificationRequestParams): UniversalCapabilityRequest {
        return NotificationRequestAdapter.adapt(params)
    }

    fun normalizeFullscreen(params: FullscreenRequestParams): UniversalCapabilityRequest {
        return FullscreenAdapter.adapt(params)
    }

    fun normalizeClipboard(params: ClipboardRequestParams): UniversalCapabilityRequest {
        return ClipboardRequestAdapter.adapt(params)
    }

    fun normalizeNativeCapability(params: NativeCapabilityParams): UniversalCapabilityRequest {
        return NativeCapabilityAdapter.adapt(params)
    }
}
