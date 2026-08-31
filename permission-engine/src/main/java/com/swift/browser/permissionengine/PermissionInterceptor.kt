package com.swift.browser.permissionengine

import android.webkit.PermissionRequest
import java.util.UUID

object PermissionInterceptor {
    fun interceptWebViewRequest(
        request: PermissionRequest,
        context: PermissionRequestContext
    ): PermissionRequestModel {
        val originUrl = OriginNormalizer.normalize(context.origin.ifBlank { request.origin?.toString() ?: context.pageUrl })
        val resources = request.resources?.toList() ?: emptyList()
        
        val types = resources.map { PermissionPolicyResolver.mapResourceToPermissionType(it) }.distinct()
        val permissionType = if (types.size == 1) {
            types.first()
        } else if (types.contains("CAMERA") && types.contains("MICROPHONE")) {
            "CAMERA_AND_MICROPHONE"
        } else {
            types.firstOrNull() ?: "UNKNOWN"
        }
        
        val isSecure = PermissionPolicyResolver.isSecureOrigin(originUrl)
        
        val model = PermissionRequestModel(
            requestId = context.requestId,
            origin = originUrl,
            siteUrl = originUrl,
            pageUrl = context.pageUrl,
            frameId = context.frameId ?: "main_frame",
            tabId = context.tabId,
            requestSourceType = context.requestSource,
            permissionType = permissionType,
            resourcesRequested = resources,
            isUserGesture = context.isUserGesture,
            isTopLevelFrame = context.isMainFrame,
            isSecureOrigin = isSecure,
            isIncognito = context.isIncognito,
            riskLevel = "Medium"
        )

        // Log interception event
        PermissionDiagnostics.recordEvent(
            PermissionEventModel(
                eventId = "evt_" + System.nanoTime(),
                requestId = context.requestId,
                stage = "REQUEST_RECEIVED",
                status = "SUCCESS",
                reason = "Interception successful",
                fileName = "PermissionInterceptor.kt",
                className = "PermissionInterceptor",
                methodName = "interceptWebViewRequest",
                callbackName = "onPermissionRequest",
                details = "Intercepted WebView resource request. Resources: $resources, TabId: ${context.tabId}, Incognito: ${context.isIncognito}"
            )
        )

        // Record trace for Dev diagnostics screen
        PermissionDiagnostics.recordTrace(
            PermissionTraceModel(
                traceId = "tr_" + context.requestId,
                requestId = context.requestId,
                origin = originUrl,
                permissionType = permissionType,
                stage = "REQUEST_RECEIVED",
                status = "SUCCESS",
                reason = "Intercepted permission request",
                suggestedFix = "No action needed",
                fileName = "PermissionInterceptor.kt",
                className = "PermissionInterceptor",
                methodName = "interceptWebViewRequest",
                callbackName = "onPermissionRequest",
                androidPermissionState = "NOT_REQUESTED",
                hardwareState = "NOT_CHECKED",
                finalResult = "PENDING"
            )
        )

        return model
    }

    // Overload for backward compatibility if called with individual parameters
    fun interceptWebViewRequest(
        request: PermissionRequest,
        tabId: String,
        isIncognito: Boolean,
        pageUrl: String,
        isUserGesture: Boolean? = null
    ): PermissionRequestModel {
        val context = PermissionRequestContext(
            requestId = "req_" + UUID.randomUUID().toString().substring(0, 8),
            tabId = tabId,
            origin = request.origin?.toString() ?: pageUrl,
            pageUrl = pageUrl,
            isMainFrame = true,
            isUserGesture = isUserGesture,
            isIncognito = isIncognito,
            requestSource = "website"
        )
        return interceptWebViewRequest(request, context)
    }

    fun interceptManualRequest(
        origin: String,
        permissionType: String,
        sourceType: String,
        tabId: String,
        isIncognito: Boolean,
        requestId: String = "req_man_" + UUID.randomUUID().toString().substring(0, 8),
        isUserGesture: Boolean? = null
    ): PermissionRequestModel {
        val normalizedOrigin = OriginNormalizer.normalize(origin)
        val isSecure = PermissionPolicyResolver.isSecureOrigin(normalizedOrigin)
        
        val model = PermissionRequestModel(
            requestId = requestId,
            origin = normalizedOrigin,
            siteUrl = normalizedOrigin,
            pageUrl = normalizedOrigin,
            frameId = "main",
            tabId = tabId,
            requestSourceType = sourceType,
            permissionType = permissionType,
            resourcesRequested = listOf(permissionType),
            isUserGesture = isUserGesture,
            isTopLevelFrame = true,
            isSecureOrigin = isSecure,
            isIncognito = isIncognito,
            riskLevel = "Medium"
        )

        PermissionDiagnostics.recordEvent(
            PermissionEventModel(
                eventId = "evt_" + System.nanoTime(),
                requestId = requestId,
                stage = "REQUEST_RECEIVED",
                status = "SUCCESS",
                reason = "Intercepted manual browser request",
                fileName = "PermissionInterceptor.kt",
                className = "PermissionInterceptor",
                methodName = "interceptManualRequest",
                callbackName = "N/A",
                details = "Request source type: $sourceType, Type: $permissionType, TabId: $tabId, Incognito: $isIncognito"
            )
        )

        PermissionDiagnostics.recordTrace(
            PermissionTraceModel(
                traceId = "tr_" + requestId,
                requestId = requestId,
                origin = normalizedOrigin,
                permissionType = permissionType,
                stage = "REQUEST_RECEIVED",
                status = "SUCCESS",
                reason = "Intercepted manual request",
                suggestedFix = "Verify if permission is remembered",
                fileName = "PermissionInterceptor.kt",
                className = "PermissionInterceptor",
                methodName = "interceptManualRequest",
                callbackName = "N/A",
                androidPermissionState = "NOT_REQUESTED",
                hardwareState = "NOT_CHECKED",
                finalResult = "PENDING"
            )
        )

        return model
    }
}

