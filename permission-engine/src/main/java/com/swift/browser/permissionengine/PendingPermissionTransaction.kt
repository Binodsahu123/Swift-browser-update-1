package com.swift.browser.permissionengine

import android.webkit.PermissionRequest
import java.util.concurrent.atomic.AtomicBoolean

data class PendingPermissionTransaction(
    val requestId: String,
    val tabId: String,
    val origin: String,
    val resources: List<String>,
    @Volatile var request: PermissionRequest? = null,
    @Volatile var context: PermissionRequestContext? = null,
    @Volatile var universalRequest: UniversalCapabilityRequest? = null,
    val topLevelOrigin: String = universalRequest?.topLevelOrigin ?: context?.pageUrl ?: origin,
    val frameOrigin: String? = universalRequest?.frameOrigin ?: context?.pageUrl,
    val frameId: String? = universalRequest?.frameId ?: context?.frameId,
    val incognito: Boolean = universalRequest?.incognito ?: context?.isIncognito ?: false,
    val isIncognito: Boolean = incognito,
    val userGesture: Boolean? = universalRequest?.userGesture ?: context?.isUserGesture,
    val requestSource: String = universalRequest?.requestSource ?: context?.requestSource ?: "website",
    val capabilityId: String = universalRequest?.capabilityId ?: resources.firstOrNull() ?: "",
    val createdAt: Long = universalRequest?.timestamp ?: context?.timestamp ?: System.currentTimeMillis(),
    val expiresAt: Long = universalRequest?.expiration ?: (System.currentTimeMillis() + 60000L),
    val expiration: Long = expiresAt,
    val stateMachine: PermissionStateMachine = PermissionStateMachine(requestId, PermissionState.PENDING),
    val mappedPermissions: Map<String, String> = emptyMap(),
    val allowedResources: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf<String>()),
    val deniedResources: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf<String>()),
    @Volatile var onResultCallback: ((String) -> Unit)? = null,
    @Volatile var preferredDecision: String = "ALLOW_ALWAYS"
) {
    val isTerminated = AtomicBoolean(false)
    private val callbackDispatched = AtomicBoolean(false)

    val state: PermissionState
        get() = stateMachine.currentState.value

    var decisionState: String
        get() = stateMachine.currentState.value.name
        set(_) {}

    fun markTerminal(terminalState: PermissionState): Boolean {
        if (!terminalState.isTerminal) return false
        if (isTerminated.compareAndSet(false, true)) {
            stateMachine.transitionTo(terminalState)
            return true
        }
        return false
    }

    fun dispatchResult(result: String) {
        if (callbackDispatched.compareAndSet(false, true)) {
            try {
                onResultCallback?.invoke(result)
            } catch (_: Throwable) {}
        }
    }

    fun enrich(
        incomingRequest: PermissionRequest?,
        incomingContext: PermissionRequestContext?,
        incomingUniversal: UniversalCapabilityRequest?,
        incomingResources: List<String>?,
        incomingCallback: ((String) -> Unit)?,
        incomingPreferredDecision: String?
    ) {
        if (incomingRequest != null && this.request == null) {
            this.request = incomingRequest
        }
        if (incomingContext != null && this.context == null) {
            this.context = incomingContext
        }
        if (incomingUniversal != null && this.universalRequest == null) {
            this.universalRequest = incomingUniversal
        }
        if (incomingCallback != null && this.onResultCallback == null) {
            this.onResultCallback = incomingCallback
        }
        if (!incomingPreferredDecision.isNullOrBlank()) {
            this.preferredDecision = incomingPreferredDecision
        }
        if (!incomingResources.isNullOrEmpty()) {
            allowedResources.addAll(incomingResources.filter { it.isNotBlank() })
        }
    }
}


