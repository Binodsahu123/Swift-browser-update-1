package com.swift.browser.permissionengine

import android.content.Context
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import kotlinx.coroutines.flow.Flow
import java.util.UUID

object PermissionEngineApi {

    fun handleWebViewPermissionRequest(
        context: PermissionRequestContext,
        request: PermissionRequest,
        androidContext: Context,
        requestSystemPermissions: ((List<String>, (AndroidPermissionResult) -> Unit) -> Unit)? = null,
        onComplete: ((String) -> Unit)? = null
    ) {
        if (context.origin.isBlank() || context.requestId.isBlank()) {
            PermissionDiagnostics.recordEvent(
                PermissionEventModel(
                    eventId = "evt_" + System.nanoTime(),
                    requestId = context.requestId.ifBlank { "MISSING_ID" },
                    stage = "MISSING_CONTEXT",
                    status = "DENIED",
                    reason = "Permission request missing mandatory origin or requestId context",
                    fileName = "PermissionEngineApi.kt",
                    className = "PermissionEngineApi",
                    methodName = "handleWebViewPermissionRequest",
                    callbackName = "onPermissionRequest",
                    details = "Denied due to missing required context"
                )
            )
            PermissionGrantEngine.rejectUnregisteredRequest(
                request = request,
                reason = "Permission request missing mandatory origin or requestId context",
                origin = context.origin,
                requestId = context.requestId.ifBlank { "MISSING_ID" }
            )
            onComplete?.invoke("BLOCK")
            return
        }

        val requestModel = PermissionInterceptor.interceptWebViewRequest(
            request = request,
            context = context
        )

        val engine = PermissionEngineProvider.get(androidContext)
        
        val universal = WebViewPermissionAdapter.adapt(requestModel)

        val transaction = PendingPermissionTransaction(
            requestId = context.requestId,
            tabId = context.tabId,
            origin = requestModel.origin,
            resources = requestModel.resourcesRequested,
            request = request,
            context = context,
            universalRequest = universal,
            createdAt = System.currentTimeMillis(),
            expiration = System.currentTimeMillis() + 60000,
            isIncognito = context.isIncognito
        )
        PermissionGrantEngine.registerPendingTransaction(transaction)

        handleUniversalCapabilityRequest(universal, androidContext, onComplete)
    }

    fun handleUniversalCapabilityRequest(
        universalRequest: UniversalCapabilityRequest,
        androidContext: Context,
        onComplete: ((String) -> Unit)? = null
    ) {
        val request = PermissionRequestNormalizer.normalize(universalRequest)
        
        PermissionDiagnostics.recordEvent(
            PermissionEventModel(
                eventId = "evt_" + System.nanoTime(),
                requestId = request.requestId,
                stage = "CAPABILITY_REQUEST_DISPATCHED",
                status = "SUCCESS",
                reason = "Capability request dispatched to PermissionEngine: ${request.capabilityId}",
                fileName = "PermissionEngineApi.kt",
                className = "PermissionEngineApi",
                methodName = "handleUniversalCapabilityRequest",
                callbackName = "capability_dispatched",
                details = "Capability: ${request.capabilityId}, Origin: ${request.origin}, TabId: ${request.tabId}"
            )
        )

        val engine = PermissionEngineProvider.get(androidContext)

        val transaction = PendingPermissionTransaction(
            requestId = request.requestId,
            tabId = request.tabId,
            origin = request.origin,
            resources = request.requestedResources,
            request = null,
            universalRequest = request,
            createdAt = System.currentTimeMillis(),
            expiration = System.currentTimeMillis() + 60000,
            isIncognito = request.incognito
        )
        PermissionGrantEngine.registerPendingTransaction(transaction)

        engine.handleUniversalRequest(request, androidContext, onComplete)
    }

    fun handleWebViewRequest(
        androidContext: Context,
        context: Any?, // PermissionRequestContext or null
        request: PermissionRequest,
        requestSystemPermissions: ((List<String>, (AndroidPermissionResult) -> Unit) -> Unit)? = null,
        onComplete: (String) -> Unit
    ) {
        val permContext = context as? PermissionRequestContext
        if (permContext == null || permContext.origin.isBlank()) {
            PermissionDiagnostics.recordEvent(
                PermissionEventModel(
                    eventId = "evt_" + System.nanoTime(),
                    requestId = permContext?.requestId ?: "MISSING_ID",
                    stage = "MISSING_CONTEXT",
                    status = "DENIED",
                    reason = "PermissionRequestContext is null or invalid in handleWebViewRequest",
                    fileName = "PermissionEngineApi.kt",
                    className = "PermissionEngineApi",
                    methodName = "handleWebViewRequest",
                    callbackName = "onPermissionRequest",
                    details = "Denied due to missing context"
                )
            )
            PermissionGrantEngine.rejectUnregisteredRequest(
                request = request,
                reason = "PermissionRequestContext is null or invalid in handleWebViewRequest",
                origin = permContext?.origin ?: "",
                requestId = permContext?.requestId ?: "MISSING_ID"
            )
            onComplete("BLOCK")
            return
        }

        handleWebViewPermissionRequest(
            context = permContext,
            request = request,
            androidContext = androidContext,
            onComplete = onComplete
        )
    }

    fun handleGeolocationRequest(
        context: PermissionRequestContext,
        androidContext: Context,
        requestSystemPermissions: ((List<String>, (AndroidPermissionResult) -> Unit) -> Unit)? = null,
        callback: GeolocationPermissions.Callback
    ) {
        val universal = GeolocationRequestAdapter.adaptContext(context)
        handleUniversalCapabilityRequest(universal, androidContext) { outcome ->
            val isAllowed = outcome == "ALLOW" || outcome == "ALLOW_ALWAYS" || outcome == "ALLOW_ONCE"
            val remember = isAllowed && outcome == "ALLOW_ALWAYS" && !context.isIncognito
            PermissionDiagnostics.recordEvent(
                PermissionEventModel(
                    eventId = "evt_" + System.nanoTime(),
                    requestId = context.requestId,
                    stage = "GEOLOCATION_RESOLVED",
                    status = if (isAllowed) "SUCCESS" else "DENIED",
                    reason = "Geolocation permission resolved with outcome: $outcome",
                    fileName = "PermissionEngineApi.kt",
                    className = "PermissionEngineApi",
                    methodName = "handleGeolocationRequest",
                    callbackName = "GeolocationPermissions.Callback",
                    details = "Allowed: $isAllowed, Remember: $remember"
                )
            )
            callback.invoke(context.origin, isAllowed, remember)
        }
    }

    fun handleGeolocationRequest(
        androidContext: Context,
        origin: String,
        isIncognito: Boolean,
        requestSystemPermissions: ((List<String>, (AndroidPermissionResult) -> Unit) -> Unit)? = null,
        callback: GeolocationPermissions.Callback,
        tabId: String = "active"
    ) {
        val permContext = PermissionRequestContext(
            requestId = "req_geo_" + UUID.randomUUID().toString().substring(0, 8),
            tabId = tabId,
            origin = origin,
            pageUrl = origin,
            isIncognito = isIncognito,
            requestSource = "geolocation"
        )
        handleGeolocationRequest(
            context = permContext,
            androidContext = androidContext,
            callback = callback
        )
    }

    fun handlePopupWindowRequest(
        androidContext: Context,
        params: PopupWindowRequestParams,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val universal = PopupWindowAdapter.adapt(params)
        handleUniversalCapabilityRequest(universal, androidContext) { outcome ->
            val isAllowed = outcome == "ALLOW" || outcome == "ALLOW_ALWAYS" || outcome == "ALLOW_ONCE"
            onComplete?.invoke(isAllowed)
        }
    }

    fun evaluatePopupRequest(
        context: PermissionRequestContext,
        isUserGesture: Boolean,
        androidContext: Context,
        onResult: (Boolean) -> Unit
    ) {
        val params = PopupWindowRequestParams(
            origin = context.origin,
            targetUrl = context.pageUrl,
            tabId = context.tabId,
            userGesture = isUserGesture,
            isIncognito = context.isIncognito,
            requestId = context.requestId
        )
        val universal = PopupWindowAdapter.adapt(params)
        val engine = PermissionEngineProvider.get(androidContext)

        val isHandled = java.util.concurrent.atomic.AtomicBoolean(false)
        val resultWrapper: (String) -> Unit = { outcome ->
            if (isHandled.compareAndSet(false, true)) {
                val isAllowed = outcome.equals("ALLOW", ignoreCase = true) ||
                                outcome.equals("ALLOW_ALWAYS", ignoreCase = true) ||
                                outcome.equals("ALLOW_ONCE", ignoreCase = true)
                onResult(isAllowed)
            }
        }

        val tx = PendingPermissionTransaction(
            requestId = context.requestId,
            tabId = context.tabId,
            origin = context.origin.ifBlank { context.pageUrl },
            resources = listOf("POPUPS"),
            context = context,
            universalRequest = universal,
            createdAt = System.currentTimeMillis(),
            expiration = System.currentTimeMillis() + 60000L,
            isIncognito = context.isIncognito,
            onResultCallback = resultWrapper
        )
        PermissionGrantEngine.registerPendingTransaction(tx)

        handleUniversalCapabilityRequest(universal, androidContext, resultWrapper)
    }

    fun handleFileChooserRequest(
        androidContext: Context,
        params: FileCaptureRequestParams,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val universal = FileCaptureAdapter.adapt(params)
        handleUniversalCapabilityRequest(universal, androidContext) { outcome ->
            val isAllowed = outcome == "ALLOW" || outcome == "ALLOW_ALWAYS" || outcome == "ALLOW_ONCE"
            onComplete?.invoke(isAllowed)
        }
    }

    fun evaluateFileChooserRequest(
        context: PermissionRequestContext,
        fileChooserParams: android.webkit.WebChromeClient.FileChooserParams?,
        androidContext: Context,
        onResult: (Boolean) -> Unit
    ) {
        val acceptTypes = fileChooserParams?.acceptTypes?.toList() ?: emptyList()
        val isMultiple = fileChooserParams?.mode == android.webkit.WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
        val isCaptureEnabled = fileChooserParams?.isCaptureEnabled == true

        val captureMode = if (isCaptureEnabled) {
            val acceptLower = acceptTypes.map { it.lowercase() }
            when {
                acceptLower.any { it.contains("image") || it.contains("camera") } -> "camera"
                acceptLower.any { it.contains("video") || it.contains("camcorder") } -> "camcorder"
                acceptLower.any { it.contains("audio") || it.contains("microphone") } -> "microphone"
                else -> "camera"
            }
        } else {
            null
        }

        val modeStr = when (fileChooserParams?.mode) {
            android.webkit.WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE -> "MODE_OPEN_MULTIPLE"
            android.webkit.WebChromeClient.FileChooserParams.MODE_SAVE -> "MODE_SAVE"
            else -> "MODE_OPEN"
        }

        val params = FileCaptureRequestParams(
            origin = context.origin,
            topLevelOrigin = context.pageUrl,
            frameOrigin = context.pageUrl,
            tabId = context.tabId,
            acceptTypes = acceptTypes,
            isMultiple = isMultiple,
            captureMode = captureMode,
            mode = modeStr,
            isIncognito = context.isIncognito,
            userGesture = context.isUserGesture,
            requestId = context.requestId,
            timestamp = context.timestamp,
            expiration = System.currentTimeMillis() + 60000L
        )
        val universal = FileCaptureAdapter.adapt(params)

        val isHandled = java.util.concurrent.atomic.AtomicBoolean(false)
        val resultWrapper: (String) -> Unit = { outcome ->
            if (isHandled.compareAndSet(false, true)) {
                val isAllowed = outcome.equals("ALLOW", ignoreCase = true) ||
                                outcome.equals("ALLOW_ALWAYS", ignoreCase = true) ||
                                outcome.equals("ALLOW_ONCE", ignoreCase = true)
                onResult(isAllowed)
            }
        }

        val tx = PendingPermissionTransaction(
            requestId = context.requestId,
            tabId = context.tabId,
            origin = context.origin.ifBlank { context.pageUrl },
            resources = listOf(universal.capabilityId),
            context = context,
            universalRequest = universal,
            createdAt = context.timestamp,
            expiration = System.currentTimeMillis() + 60000L,
            isIncognito = context.isIncognito,
            onResultCallback = resultWrapper
        )
        PermissionGrantEngine.registerPendingTransaction(tx)

        handleUniversalCapabilityRequest(universal, androidContext, resultWrapper)
    }

    fun handleSpeechRecognitionRequest(
        androidContext: Context,
        params: SpeechRecognitionRequestParams,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val universal = SpeechRecognitionAdapter.adapt(params)
        handleUniversalCapabilityRequest(universal, androidContext) { outcome ->
            val isAllowed = outcome == "ALLOW" || outcome == "ALLOW_ALWAYS" || outcome == "ALLOW_ONCE"
            onComplete?.invoke(isAllowed)
        }
    }

    fun handleNotificationRequest(
        androidContext: Context,
        params: NotificationRequestParams,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val universal = NotificationRequestAdapter.adapt(params)
        handleUniversalCapabilityRequest(universal, androidContext) { outcome ->
            val isAllowed = outcome == "ALLOW" || outcome == "ALLOW_ALWAYS" || outcome == "ALLOW_ONCE"
            onComplete?.invoke(isAllowed)
        }
    }

    fun handleInternalPermissionRequest(
        androidContext: Context,
        transactionId: String,
        origin: String,
        permissionType: String,
        isIncognito: Boolean,
        tabId: String,
        requestSystemPermissions: ((List<String>, (AndroidPermissionResult) -> Unit) -> Unit)? = null,
        onComplete: (Boolean) -> Unit
    ) {
        if (origin.isBlank() || transactionId.isBlank() || tabId.isBlank()) {
            PermissionDiagnostics.recordEvent(
                PermissionEventModel(
                    eventId = "evt_" + System.nanoTime(),
                    requestId = transactionId.ifBlank { "MISSING_ID" },
                    stage = "MISSING_CONTEXT",
                    status = "DENIED",
                    reason = "Internal permission request missing mandatory origin, transactionId, or tabId",
                    fileName = "PermissionEngineApi.kt",
                    className = "PermissionEngineApi",
                    methodName = "handleInternalPermissionRequest",
                    callbackName = "handleInternalRequest",
                    details = "Denied due to missing context"
                )
            )
            onComplete(false)
            return
        }

        val requestModel = PermissionInterceptor.interceptManualRequest(
            origin = origin,
            permissionType = permissionType,
            sourceType = "internal",
            tabId = tabId,
            isIncognito = isIncognito,
            requestId = transactionId
        )

        val engine = PermissionEngineProvider.get(androidContext)

        val descriptor = PermissionDescriptorRegistry.getDescriptor(permissionType)
        val resources = descriptor?.webViewResources ?: listOf(permissionType)

        val transaction = PendingPermissionTransaction(
            requestId = transactionId,
            tabId = tabId,
            origin = requestModel.origin,
            resources = resources,
            request = null,
            createdAt = System.currentTimeMillis(),
            expiration = System.currentTimeMillis() + 60000,
            isIncognito = isIncognito
        )
        PermissionGrantEngine.registerPendingTransaction(transaction)

        engine.handleRequest(
            requestModel = requestModel,
            androidContext = androidContext,
            onComplete = { outcome ->
                val isAllowed = outcome == "ALLOW" || outcome == "ALLOW_ALWAYS" || outcome == "ALLOW_ONCE"
                onComplete(isAllowed)
            }
        )
    }

    fun handleFullscreenRequest(
        androidContext: Context,
        params: FullscreenRequestParams,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val universal = FullscreenAdapter.adapt(params)
        handleUniversalCapabilityRequest(universal, androidContext) { outcome ->
            val isAllowed = outcome == "ALLOW" || outcome == "ALLOW_ALWAYS" || outcome == "ALLOW_ONCE"
            onComplete?.invoke(isAllowed)
        }
    }

    /**
     * Strongly typed capability evaluation entry point for native modules / extension subsystems.
     * Evaluates capability requirements, policies, hardware, and user permission state without allowing
     * native modules to directly bypass or alter website permission stores.
     */
    fun evaluateNativeCapability(
        androidContext: Context,
        request: UniversalCapabilityRequest,
        onDecision: (CapabilityDecision) -> Unit
    ) {
        val normalized = PermissionRequestNormalizer.normalize(request)
        val dynamicOrigin = normalized.toDynamicOrigin()
        val evaluation = CapabilityBroker.evaluateCapability(
            resourceOrType = normalized.capabilityId,
            dynamicOrigin = dynamicOrigin,
            androidContext = androidContext
        )

        // If capability is unsupported or blocked by broker security/policy
        if (evaluation.capabilityState == CapabilityState.UNSUPPORTED_BY_WEBVIEW ||
            evaluation.capabilityState == CapabilityState.UNSUPPORTED_BY_ANDROID ||
            evaluation.capabilityState == CapabilityState.BLOCKED_BY_SECURITY ||
            evaluation.capabilityState == CapabilityState.BLOCKED_BY_USER_POLICY ||
            evaluation.capabilityState == CapabilityState.DENIED_BY_HARDWARE
        ) {
            val outcome = if (evaluation.capabilityState == CapabilityState.UNSUPPORTED_BY_WEBVIEW ||
                evaluation.capabilityState == CapabilityState.UNSUPPORTED_BY_ANDROID
            ) "UNSUPPORTED" else "BLOCK"

            val decision = CapabilityDecision(
                requestId = normalized.requestId,
                capabilityId = normalized.capabilityId,
                origin = normalized.origin,
                decision = outcome,
                isAllowed = false,
                capabilityState = evaluation.capabilityState,
                reason = evaluation.reason,
                requiresPrompt = false,
                isIncognito = normalized.incognito
            )
            onDecision(decision)
            return
        }

        val isHandled = java.util.concurrent.atomic.AtomicBoolean(false)
        val resultWrapper: (String) -> Unit = { outcome ->
            if (isHandled.compareAndSet(false, true)) {
                val isAllowed = outcome.equals("ALLOW", ignoreCase = true) ||
                                outcome.equals("ALLOW_ALWAYS", ignoreCase = true) ||
                                outcome.equals("ALLOW_ONCE", ignoreCase = true)
                val capState = if (isAllowed) CapabilityState.SUPPORTED else CapabilityState.BLOCKED_BY_USER_POLICY
                val decision = CapabilityDecision(
                    requestId = normalized.requestId,
                    capabilityId = normalized.capabilityId,
                    origin = normalized.origin,
                    decision = outcome,
                    isAllowed = isAllowed,
                    capabilityState = capState,
                    reason = "Native capability evaluation completed with outcome: $outcome",
                    requiresPrompt = evaluation.requiresUserPrompt,
                    isIncognito = normalized.incognito
                )
                onDecision(decision)
            }
        }

        val tx = PendingPermissionTransaction(
            requestId = normalized.requestId,
            tabId = normalized.tabId,
            origin = normalized.origin,
            resources = normalized.requestedResources.ifEmpty { listOf(normalized.capabilityId) },
            universalRequest = normalized,
            createdAt = System.currentTimeMillis(),
            expiration = System.currentTimeMillis() + 60000L,
            isIncognito = normalized.incognito,
            onResultCallback = resultWrapper
        )
        PermissionGrantEngine.registerPendingTransaction(tx)

        handleUniversalCapabilityRequest(normalized, androidContext, resultWrapper)
    }

    /**
     * Overloaded helper for native callers passing NativeCapabilityParams.
     */
    fun evaluateNativeCapability(
        androidContext: Context,
        params: NativeCapabilityParams,
        onDecision: (CapabilityDecision) -> Unit
    ) {
        val universal = NativeCapabilityAdapter.adapt(params)
        evaluateNativeCapability(androidContext, universal, onDecision)
    }

    fun handleNativeCapabilityRequest(
        androidContext: Context,
        params: NativeCapabilityParams,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        evaluateNativeCapability(androidContext, params) { decision ->
            onComplete?.invoke(decision.isAllowed)
        }
    }

    /**
     * Strongly typed capability evaluation entry point for Async Clipboard operations (read/write).
     * Preserves browser semantics: secure origins, user gesture requirements, read vs write separation,
     * incognito isolation, and pending transaction cancellation.
     */
    fun evaluateClipboardRequest(
        androidContext: Context,
        params: ClipboardRequestParams,
        onDecision: (CapabilityDecision) -> Unit
    ) {
        val universal = ClipboardRequestAdapter.adapt(params)
        val normalized = PermissionRequestNormalizer.normalize(universal)
        val dynamicOrigin = normalized.toDynamicOrigin()
        val evaluation = CapabilityBroker.evaluateCapability(
            resourceOrType = normalized.capabilityId,
            dynamicOrigin = dynamicOrigin,
            androidContext = androidContext
        )

        // Block immediately if insecure origin or blocked by security / policy
        if (evaluation.capabilityState == CapabilityState.BLOCKED_BY_SECURITY ||
            evaluation.capabilityState == CapabilityState.BLOCKED_BY_USER_POLICY ||
            evaluation.capabilityState == CapabilityState.UNSUPPORTED_BY_WEBVIEW
        ) {
            val decision = CapabilityDecision(
                requestId = normalized.requestId,
                capabilityId = normalized.capabilityId,
                origin = normalized.origin,
                decision = "BLOCK",
                isAllowed = false,
                capabilityState = evaluation.capabilityState,
                reason = evaluation.reason,
                requiresPrompt = false,
                isIncognito = normalized.incognito
            )
            onDecision(decision)
            return
        }

        val isHandled = java.util.concurrent.atomic.AtomicBoolean(false)
        val resultWrapper: (String) -> Unit = { outcome ->
            if (isHandled.compareAndSet(false, true)) {
                val isAllowed = outcome.equals("ALLOW", ignoreCase = true) ||
                                outcome.equals("ALLOW_ALWAYS", ignoreCase = true) ||
                                outcome.equals("ALLOW_ONCE", ignoreCase = true)
                val decision = CapabilityDecision(
                    requestId = normalized.requestId,
                    capabilityId = normalized.capabilityId,
                    origin = normalized.origin,
                    decision = outcome,
                    isAllowed = isAllowed,
                    capabilityState = if (isAllowed) CapabilityState.SUPPORTED else CapabilityState.BLOCKED_BY_USER_POLICY,
                    reason = "Clipboard permission evaluation completed with outcome: $outcome",
                    requiresPrompt = evaluation.requiresUserPrompt,
                    isIncognito = normalized.incognito
                )
                onDecision(decision)
            }
        }

        val tx = PendingPermissionTransaction(
            requestId = normalized.requestId,
            tabId = normalized.tabId,
            origin = normalized.origin,
            resources = listOf(normalized.capabilityId),
            universalRequest = normalized,
            createdAt = System.currentTimeMillis(),
            expiration = System.currentTimeMillis() + 60000L,
            isIncognito = normalized.incognito,
            onResultCallback = resultWrapper
        )
        PermissionGrantEngine.registerPendingTransaction(tx)

        handleUniversalCapabilityRequest(normalized, androidContext, resultWrapper)
    }

    /**
     * Standard runtime callback handler for clipboard operations.
     */
    fun handleClipboardRequest(
        androidContext: Context,
        params: ClipboardRequestParams,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        evaluateClipboardRequest(androidContext, params) { decision ->
            onComplete?.invoke(decision.isAllowed)
        }
    }

    /**
     * Strongly typed capability evaluation entry point for Web Screen-Sharing (navigator.mediaDevices.getDisplayMedia()).
     * Routes through standard PermissionEngine flow: secure context check, user gesture requirement,
     * site policy, user prompt dialog, and pending transaction lifecycle.
     */
    fun evaluateScreenCaptureRequest(
        androidContext: Context,
        params: ScreenCaptureRequestParams,
        onDecision: (CapabilityDecision) -> Unit
    ) {
        val universal = ScreenCaptureAdapter.adapt(params)
        val normalized = PermissionRequestNormalizer.normalize(universal)
        val dynamicOrigin = normalized.toDynamicOrigin()
        val evaluation = CapabilityBroker.evaluateCapability(
            resourceOrType = normalized.capabilityId,
            dynamicOrigin = dynamicOrigin,
            androidContext = androidContext
        )

        if (evaluation.capabilityState == CapabilityState.BLOCKED_BY_SECURITY ||
            evaluation.capabilityState == CapabilityState.BLOCKED_BY_USER_POLICY ||
            evaluation.capabilityState == CapabilityState.DENIED_BY_HARDWARE ||
            evaluation.capabilityState == CapabilityState.UNSUPPORTED_BY_WEBVIEW
        ) {
            val decision = CapabilityDecision(
                requestId = normalized.requestId,
                capabilityId = normalized.capabilityId,
                origin = normalized.origin,
                decision = "BLOCK",
                isAllowed = false,
                capabilityState = evaluation.capabilityState,
                reason = evaluation.reason,
                requiresPrompt = false,
                isIncognito = normalized.incognito
            )
            onDecision(decision)
            return
        }

        val isHandled = java.util.concurrent.atomic.AtomicBoolean(false)
        val resultWrapper: (String) -> Unit = { outcome ->
            if (isHandled.compareAndSet(false, true)) {
                val isAllowed = outcome.equals("ALLOW", ignoreCase = true) ||
                                outcome.equals("ALLOW_ALWAYS", ignoreCase = true) ||
                                outcome.equals("ALLOW_ONCE", ignoreCase = true)
                val decision = CapabilityDecision(
                    requestId = normalized.requestId,
                    capabilityId = normalized.capabilityId,
                    origin = normalized.origin,
                    decision = outcome,
                    isAllowed = isAllowed,
                    capabilityState = if (isAllowed) CapabilityState.SUPPORTED else CapabilityState.BLOCKED_BY_USER_POLICY,
                    reason = "Screen capture permission evaluation completed with outcome: $outcome",
                    requiresPrompt = evaluation.requiresUserPrompt,
                    isIncognito = normalized.incognito
                )
                onDecision(decision)
            }
        }

        val tx = PendingPermissionTransaction(
            requestId = normalized.requestId,
            tabId = normalized.tabId,
            origin = normalized.origin,
            resources = listOf("android.webkit.resource.DISPLAY_CAPTURE", "SCREEN_CAPTURE"),
            universalRequest = normalized,
            preferredDecision = "ALLOW_ONCE",
            createdAt = System.currentTimeMillis(),
            expiration = System.currentTimeMillis() + 60000L,
            isIncognito = normalized.incognito,
            onResultCallback = resultWrapper
        )
        PermissionGrantEngine.registerPendingTransaction(tx)

        handleUniversalCapabilityRequest(normalized, androidContext, resultWrapper)
    }

    fun handleScreenCaptureRequest(
        androidContext: Context,
        params: ScreenCaptureRequestParams,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        evaluateScreenCaptureRequest(androidContext, params) { decision ->
            onComplete?.invoke(decision.isAllowed)
        }
    }


    fun handleJavascriptRequest(
        androidContext: Context,
        transactionId: String,
        origin: String,
        permissionType: String,
        isIncognito: Boolean,
        onComplete: (Boolean) -> Unit,
        tabId: String,
        requestSystemPermissions: ((List<String>, (AndroidPermissionResult) -> Unit) -> Unit)? = null
    ) {
        handleInternalPermissionRequest(
            androidContext = androidContext,
            transactionId = transactionId,
            origin = origin,
            permissionType = permissionType,
            isIncognito = isIncognito,
            tabId = tabId,
            onComplete = onComplete
        )
    }

    fun resumeTransaction(
        requestId: String,
        androidResult: AndroidPermissionResult,
        androidContext: Context
    ) {
        val engine = PermissionEngineProvider.get(androidContext)
        engine.resumeTransaction(requestId, androidResult)
    }

    fun cancelWebViewPermissionRequest(requestId: String) {
        PermissionGrantEngine.cancelPendingTransaction(requestId)
    }

    fun cancelTabPermissionRequests(tabId: String) {
        PermissionGrantEngine.cancelPendingTransactionsForTab(tabId)
    }

    fun handlePermissionRequestCanceled(
        request: PermissionRequest?,
        clearPendingRequest: () -> Unit = {}
    ) {
        if (request != null) {
            PermissionGrantEngine.cancelPendingTransactionsForRequest(request)
        }
        clearPendingRequest()
    }

    fun observeAllPermissions(context: Context): Flow<List<PermissionEntity>> {
        val engine = PermissionEngineProvider.get(context)
        return engine.observeAllPermissions()
    }

    fun setDecision(context: Context, origin: String, permissionType: String, decision: String) {
        val engine = PermissionEngineProvider.get(context)
        engine.setPermissionState(origin, permissionType, decision)
    }

    fun revokePermission(context: Context, origin: String, permissionType: String) {
        val engine = PermissionEngineProvider.get(context)
        engine.clearPermissionState(origin, permissionType)
    }

    fun resetOrigin(context: Context, origin: String) {
        val engine = PermissionEngineProvider.get(context)
        engine.resetAllPermissionsForDomain(origin)
    }

    fun resetAll(context: Context) {
        val engine = PermissionEngineProvider.get(context)
        engine.resetAllPermissions()
    }
}
