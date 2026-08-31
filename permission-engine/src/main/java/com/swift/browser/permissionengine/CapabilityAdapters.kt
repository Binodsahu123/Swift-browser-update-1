package com.swift.browser.permissionengine

interface CapabilityRequestAdapter<T> {
    fun adapt(source: T): UniversalCapabilityRequest
}

private fun recordAdapterEvent(
    requestId: String,
    stage: String,
    adapterName: String,
    capabilityId: String,
    details: String
) {
    try {
        PermissionDiagnostics.recordEvent(
            PermissionEventModel(
                eventId = "evt_" + System.nanoTime(),
                requestId = requestId,
                stage = stage,
                status = "SUCCESS",
                reason = "[$adapterName] $stage: $capabilityId",
                fileName = "CapabilityAdapters.kt",
                className = adapterName,
                methodName = "adapt",
                callbackName = stage.lowercase(),
                details = details
            )
        )
    } catch (_: Exception) {
        // Fallback safety
    }
}

object WebViewPermissionAdapter : CapabilityRequestAdapter<PermissionRequestModel> {
    override fun adapt(source: PermissionRequestModel): UniversalCapabilityRequest {
        recordAdapterEvent(source.requestId, "NORMALIZED_WEBVIEW", "WebViewPermissionAdapter", source.permissionType, "Raw origin: ${source.origin}")
        val universal = UniversalCapabilityRequest.builder(
            rawUrl = source.origin.ifBlank { source.siteUrl },
            capabilityId = source.permissionType,
            topLevelUrl = source.siteUrl,
            frameUrl = source.pageUrl
        )
            .setRequestId(source.requestId)
            .setRequestedResources(source.resourcesRequested)
            .setTabId(source.tabId)
            .setFrameId(source.frameId)
            .setIncognito(source.isIncognito)
            .setUserGesture(source.isUserGesture)
            .setRequestSource(source.requestSourceType)
            .setMetadata(source.metadata)
            .build()
        recordAdapterEvent(universal.requestId, "NORMALIZED_WEBVIEW", "WebViewPermissionAdapter", universal.capabilityId, "Normalized origin: ${universal.origin}")
        return universal
    }

    fun adaptRawResources(
        origin: String,
        resources: Array<String>,
        tabId: String = "default_tab",
        isIncognito: Boolean = false,
        requestId: String = "req_" + java.util.UUID.randomUUID().toString().substring(0, 8)
    ): UniversalCapabilityRequest {
        val resList = resources.toList()
        val primaryType = resList.firstOrNull()?.let {
            PermissionDescriptorRegistry.mapResourceToPermissionType(it)
        } ?: "UNKNOWN"

        recordAdapterEvent(requestId, "NORMALIZED_WEBVIEW", "WebViewPermissionAdapter", primaryType, "Raw origin: $origin, Resources: ${resources.joinToString()}")
        val universal = UniversalCapabilityRequest.builder(
            rawUrl = origin,
            capabilityId = primaryType
        )
            .setRequestId(requestId)
            .setRequestedResources(resList)
            .setWebViewResourceNames(resList)
            .setTabId(tabId)
            .setIncognito(isIncognito)
            .setRequestSource("webview_on_permission_request")
            .setWebApiName("WebChromeClient.onPermissionRequest")
            .build()
        recordAdapterEvent(universal.requestId, "NORMALIZED_WEBVIEW", "WebViewPermissionAdapter", universal.capabilityId, "Normalized origin: ${universal.origin}")
        return universal
    }
}

object GeolocationRequestAdapter : CapabilityRequestAdapter<Pair<String, String>> {
    override fun adapt(source: Pair<String, String>): UniversalCapabilityRequest {
        val reqId = "req_geo_" + java.util.UUID.randomUUID().toString().substring(0, 8)
        recordAdapterEvent(reqId, "NORMALIZED_GEOLOCATION", "GeolocationRequestAdapter", "LOCATION", "Raw origin: ${source.first}")
        val universal = UniversalCapabilityRequest.builder(
            rawUrl = source.first,
            capabilityId = "LOCATION"
        )
            .setRequestId(reqId)
            .setTabId(source.second)
            .setRequestSource("geolocation_api")
            .setWebApiName("W3C Geolocation API")
            .build()
        recordAdapterEvent(universal.requestId, "NORMALIZED_GEOLOCATION", "GeolocationRequestAdapter", "LOCATION", "Normalized origin: ${universal.origin}")
        return universal
    }

    fun adaptContext(context: PermissionRequestContext): UniversalCapabilityRequest {
        recordAdapterEvent(context.requestId, "NORMALIZED_GEOLOCATION", "GeolocationRequestAdapter", "LOCATION", "Context origin: ${context.origin}")
        val universal = UniversalCapabilityRequest.builder(
            rawUrl = context.origin,
            capabilityId = "LOCATION",
            topLevelUrl = context.pageUrl,
            frameUrl = context.origin
        )
            .setRequestId(context.requestId)
            .setTabId(context.tabId)
            .setFrameId(context.frameId)
            .setIncognito(context.isIncognito)
            .setUserGesture(context.isUserGesture)
            .setRequestSource(context.requestSource)
            .setWebApiName("GeolocationPermissions.Callback")
            .build()
        recordAdapterEvent(universal.requestId, "NORMALIZED_GEOLOCATION", "GeolocationRequestAdapter", "LOCATION", "Normalized origin: ${universal.origin}")
        return universal
    }
}

data class SpeechRecognitionRequestParams(
    val origin: String,
    val pageUrl: String? = null,
    val language: String = "en-US",
    val continuous: Boolean = false,
    val interimResults: Boolean = false,
    val tabId: String = "default_tab",
    val userGesture: Boolean? = null,
    val isIncognito: Boolean = false,
    val requestId: String = "req_speech_" + java.util.UUID.randomUUID().toString().substring(0, 8)
)

object SpeechRecognitionAdapter : CapabilityRequestAdapter<SpeechRecognitionRequestParams> {
    override fun adapt(source: SpeechRecognitionRequestParams): UniversalCapabilityRequest {
        recordAdapterEvent(source.requestId, "NORMALIZED_SPEECH", "SpeechRecognitionAdapter", "SPEECH_RECOGNITION", "Origin: ${source.origin}, Lang: ${source.language}")
        val universal = UniversalCapabilityRequest.builder(
            rawUrl = source.origin,
            capabilityId = "SPEECH_RECOGNITION",
            topLevelUrl = source.pageUrl ?: source.origin,
            frameUrl = source.origin
        )
            .setRequestId(source.requestId)
            .setTabId(source.tabId)
            .setIncognito(source.isIncognito)
            .setUserGesture(source.userGesture)
            .setRequestSource("web_speech_api")
            .setWebApiName("Web Speech API (webkitSpeechRecognition)")
            .setMetadata(mapOf(
                "origin" to source.origin,
                "language" to source.language,
                "continuous" to source.continuous.toString(),
                "interimResults" to source.interimResults.toString(),
                "requestId" to source.requestId,
                "userGesture" to (source.userGesture?.toString() ?: "UNKNOWN")
            ))
            .build()
        recordAdapterEvent(universal.requestId, "NORMALIZED_SPEECH", "SpeechRecognitionAdapter", "SPEECH_RECOGNITION", "Normalized origin: ${universal.origin}")
        return universal
    }

    fun adaptContext(
        context: PermissionRequestContext,
        language: String = "en-US",
        continuous: Boolean = false,
        interimResults: Boolean = false
    ): UniversalCapabilityRequest {
        val params = SpeechRecognitionRequestParams(
            origin = context.origin,
            pageUrl = context.pageUrl,
            language = language,
            continuous = continuous,
            interimResults = interimResults,
            tabId = context.tabId,
            userGesture = context.isUserGesture,
            isIncognito = context.isIncognito,
            requestId = context.requestId
        )
        return adapt(params)
    }
}

data class FileCaptureRequestParams(
    val origin: String,
    val topLevelOrigin: String? = null,
    val frameOrigin: String? = null,
    val tabId: String = "default_tab",
    val acceptTypes: List<String> = emptyList(),
    val isMultiple: Boolean = false,
    val captureMode: String? = null,
    val mode: String = "MODE_OPEN",
    val isIncognito: Boolean = false,
    val userGesture: Boolean? = null,
    val requestId: String = "req_file_" + java.util.UUID.randomUUID().toString().substring(0, 8),
    val timestamp: Long = System.currentTimeMillis(),
    val expiration: Long = System.currentTimeMillis() + 60000L
)

object FileCaptureAdapter : CapabilityRequestAdapter<FileCaptureRequestParams> {
    override fun adapt(source: FileCaptureRequestParams): UniversalCapabilityRequest {
        val capabilityId = when {
            source.captureMode == "camera" || source.captureMode == "camcorder" -> "FILE_CAMERA_CAPTURE"
            source.captureMode == "microphone" -> "FILE_AUDIO_CAPTURE"
            source.isMultiple -> "FILE_MULTIPLE"
            else -> "FILE_UPLOAD"
        }

        recordAdapterEvent(source.requestId, "NORMALIZED_FILE", "FileCaptureAdapter", capabilityId, "Origin: ${source.origin}, CaptureMode: ${source.captureMode}")
        val universal = UniversalCapabilityRequest.builder(
            rawUrl = source.origin,
            capabilityId = capabilityId,
            topLevelUrl = source.topLevelOrigin ?: source.origin,
            frameUrl = source.frameOrigin ?: source.origin
        )
            .setRequestId(source.requestId)
            .setTabId(source.tabId)
            .setIncognito(source.isIncognito)
            .setUserGesture(source.userGesture)
            .setExpiration(source.expiration)
            .setRequestSource("file_chooser")
            .setWebApiName("HTML5 File Input")
            .setMetadata(mapOf(
                "acceptTypes" to source.acceptTypes.joinToString(","),
                "multiple" to source.isMultiple.toString(),
                "capture" to (source.captureMode ?: "none"),
                "mode" to source.mode,
                "origin" to source.origin,
                "topLevelOrigin" to (source.topLevelOrigin ?: source.origin),
                "frameOrigin" to (source.frameOrigin ?: source.origin),
                "tab" to source.tabId,
                "incognito" to source.isIncognito.toString(),
                "timestamp" to source.timestamp.toString(),
                "expiration" to source.expiration.toString()
            ))
            .build()
        recordAdapterEvent(universal.requestId, "NORMALIZED_FILE", "FileCaptureAdapter", universal.capabilityId, "Normalized origin: ${universal.origin}")
        return universal
    }
}

data class NotificationRequestParams(
    val origin: String,
    val pageUrl: String? = null,
    val title: String? = null,
    val tabId: String = "default_tab",
    val userGesture: Boolean? = null,
    val isIncognito: Boolean = false,
    val requestId: String = "req_notif_" + java.util.UUID.randomUUID().toString().substring(0, 8)
)

object NotificationRequestAdapter : CapabilityRequestAdapter<NotificationRequestParams> {
    override fun adapt(source: NotificationRequestParams): UniversalCapabilityRequest {
        recordAdapterEvent(source.requestId, "NORMALIZED_NOTIFICATION", "NotificationRequestAdapter", "NOTIFICATIONS", "Origin: ${source.origin}")
        val universal = UniversalCapabilityRequest.builder(
            rawUrl = source.origin,
            capabilityId = "NOTIFICATIONS",
            topLevelUrl = source.pageUrl ?: source.origin,
            frameUrl = source.origin
        )
            .setRequestId(source.requestId)
            .setTabId(source.tabId)
            .setIncognito(source.isIncognito)
            .setUserGesture(source.userGesture)
            .setRequestSource("notifications_api")
            .setWebApiName("Notifications API")
            .setMetadata(mapOf(
                "title" to (source.title ?: ""),
                "origin" to source.origin
            ))
            .build()
        recordAdapterEvent(universal.requestId, "NORMALIZED_NOTIFICATION", "NotificationRequestAdapter", "NOTIFICATIONS", "Normalized origin: ${universal.origin}")
        return universal
    }

    fun adaptContext(context: PermissionRequestContext): UniversalCapabilityRequest {
        val params = NotificationRequestParams(
            origin = context.origin,
            pageUrl = context.pageUrl,
            tabId = context.tabId,
            userGesture = context.isUserGesture,
            isIncognito = context.isIncognito,
            requestId = context.requestId
        )
        return adapt(params)
    }
}

data class PopupWindowRequestParams(
    val origin: String,
    val targetUrl: String = "",
    val tabId: String = "default_tab",
    val userGesture: Boolean? = null,
    val isIncognito: Boolean = false,
    val isDialog: Boolean = false,
    val requestId: String = "req_popup_" + java.util.UUID.randomUUID().toString().substring(0, 8)
)

object PopupWindowAdapter : CapabilityRequestAdapter<PopupWindowRequestParams> {
    override fun adapt(source: PopupWindowRequestParams): UniversalCapabilityRequest {
        recordAdapterEvent(source.requestId, "NORMALIZED_POPUP", "PopupWindowAdapter", "POPUPS", "Origin: ${source.origin}, Target: ${source.targetUrl}")
        val universal = UniversalCapabilityRequest.builder(
            rawUrl = source.origin,
            capabilityId = "POPUPS",
            topLevelUrl = source.origin,
            frameUrl = source.targetUrl
        )
            .setRequestId(source.requestId)
            .setTabId(source.tabId)
            .setIncognito(source.isIncognito)
            .setUserGesture(source.userGesture)
            .setRequestSource("window_open")
            .setWebApiName("Window.open()")
            .setMetadata(mapOf(
                "sourceOrigin" to source.origin,
                "targetUrl" to source.targetUrl,
                "userGesture" to (source.userGesture?.toString() ?: "UNKNOWN"),
                "dialogType" to (if (source.isDialog) "dialog" else "window")
            ))
            .build()
        recordAdapterEvent(universal.requestId, "NORMALIZED_POPUP", "PopupWindowAdapter", "POPUPS", "Normalized origin: ${universal.origin}")
        return universal
    }
}

data class FullscreenRequestParams(
    val origin: String,
    val tabId: String = "default_tab",
    val userGesture: Boolean? = null,
    val isIncognito: Boolean = false,
    val requestId: String = "req_full_" + java.util.UUID.randomUUID().toString().substring(0, 8)
)

object FullscreenAdapter : CapabilityRequestAdapter<FullscreenRequestParams> {
    override fun adapt(source: FullscreenRequestParams): UniversalCapabilityRequest {
        recordAdapterEvent(source.requestId, "NORMALIZED_FULLSCREEN", "FullscreenAdapter", "FULLSCREEN", "Origin: ${source.origin}")
        val universal = UniversalCapabilityRequest.builder(
            rawUrl = source.origin,
            capabilityId = "FULLSCREEN"
        )
            .setRequestId(source.requestId)
            .setTabId(source.tabId)
            .setIncognito(source.isIncognito)
            .setUserGesture(source.userGesture)
            .setRequestSource("fullscreen_api")
            .setWebApiName("Element.requestFullscreen()")
            .setMetadata(mapOf(
                "origin" to source.origin
            ))
            .build()
        recordAdapterEvent(universal.requestId, "NORMALIZED_FULLSCREEN", "FullscreenAdapter", "FULLSCREEN", "Normalized origin: ${universal.origin}")
        return universal
    }
}

data class ClipboardRequestParams(
    val origin: String,
    val operation: String, // "READ" or "WRITE"
    val tabId: String = "default_tab",
    val userGesture: Boolean? = null,
    val isIncognito: Boolean = false,
    val requestId: String = "req_clip_" + java.util.UUID.randomUUID().toString().substring(0, 8),
    val timestamp: Long = System.currentTimeMillis()
)

object ClipboardRequestAdapter : CapabilityRequestAdapter<ClipboardRequestParams> {
    override fun adapt(source: ClipboardRequestParams): UniversalCapabilityRequest {
        val capId = if (source.operation.equals("READ", ignoreCase = true)) "CLIPBOARD_READ" else "CLIPBOARD_WRITE"
        recordAdapterEvent(source.requestId, "NORMALIZED_CLIPBOARD", "ClipboardRequestAdapter", capId, "Origin: ${source.origin}, Op: ${source.operation}")
        val universal = UniversalCapabilityRequest.builder(
            rawUrl = source.origin,
            capabilityId = capId
        )
            .setRequestId(source.requestId)
            .setTabId(source.tabId)
            .setIncognito(source.isIncognito)
            .setUserGesture(source.userGesture)
            .setRequestSource("clipboard_api")
            .setWebApiName("Async Clipboard API (" + source.operation.uppercase() + ")")
            .setMetadata(mapOf(
                "operation" to source.operation.uppercase(),
                "origin" to source.origin,
                "timestamp" to source.timestamp.toString()
            ))
            .build()
        recordAdapterEvent(universal.requestId, "NORMALIZED_CLIPBOARD", "ClipboardRequestAdapter", universal.capabilityId, "Normalized origin: ${universal.origin}")
        return universal
    }
}

data class NativeCapabilityParams(
    val origin: String,
    val capabilityId: String,
    val tabId: String = "default_tab",
    val userGesture: Boolean? = null,
    val isIncognito: Boolean = false,
    val apiName: String = "NativeBridge",
    val metadata: Map<String, String> = emptyMap(),
    val requestId: String = "req_native_" + java.util.UUID.randomUUID().toString().substring(0, 8),
    val timestamp: Long = System.currentTimeMillis()
)

object NativeCapabilityAdapter : CapabilityRequestAdapter<NativeCapabilityParams> {
    override fun adapt(source: NativeCapabilityParams): UniversalCapabilityRequest {
        val normalizedCapId = source.capabilityId.uppercase()
        recordAdapterEvent(source.requestId, "NORMALIZED_NATIVE", "NativeCapabilityAdapter", normalizedCapId, "Origin: ${source.origin}, Api: ${source.apiName}")
        val universal = UniversalCapabilityRequest.builder(
            rawUrl = source.origin,
            capabilityId = normalizedCapId
        )
            .setRequestId(source.requestId)
            .setTabId(source.tabId)
            .setIncognito(source.isIncognito)
            .setUserGesture(source.userGesture)
            .setRequestSource("native_bridge")
            .setWebApiName(source.apiName)
            .setMetadata(source.metadata + mapOf(
                "origin" to source.origin,
                "timestamp" to source.timestamp.toString()
            ))
            .build()
        recordAdapterEvent(universal.requestId, "NORMALIZED_NATIVE", "NativeCapabilityAdapter", universal.capabilityId, "Normalized origin: ${universal.origin}")
        return universal
    }
}

data class ScreenCaptureRequestParams(
    val origin: String,
    val tabId: String = "default_tab",
    val userGesture: Boolean? = null,
    val isIncognito: Boolean = false,
    val videoConstraints: String? = null,
    val requestId: String = "req_screencap_" + java.util.UUID.randomUUID().toString().substring(0, 8),
    val timestamp: Long = System.currentTimeMillis()
)

object ScreenCaptureAdapter : CapabilityRequestAdapter<ScreenCaptureRequestParams> {
    override fun adapt(source: ScreenCaptureRequestParams): UniversalCapabilityRequest {
        recordAdapterEvent(source.requestId, "NORMALIZED_SCREEN_CAPTURE", "ScreenCaptureAdapter", "SCREEN_CAPTURE", "Origin: ${source.origin}")
        val universal = UniversalCapabilityRequest.builder(
            rawUrl = source.origin,
            capabilityId = "SCREEN_CAPTURE"
        )
            .setRequestId(source.requestId)
            .setTabId(source.tabId)
            .setIncognito(source.isIncognito)
            .setUserGesture(source.userGesture)
            .setRequestSource("screen_capture_bridge")
            .setWebApiName("navigator.mediaDevices.getDisplayMedia()")
            .setMetadata(mapOf(
                "origin" to source.origin,
                "videoConstraints" to (source.videoConstraints ?: ""),
                "timestamp" to source.timestamp.toString()
            ))
            .build()
        recordAdapterEvent(universal.requestId, "NORMALIZED_SCREEN_CAPTURE", "ScreenCaptureAdapter", universal.capabilityId, "Normalized origin: ${universal.origin}")
        return universal
    }
}



