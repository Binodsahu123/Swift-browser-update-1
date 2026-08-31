package com.swift.browser.permissionengine

import android.os.Handler
import android.os.Looper
import android.webkit.PermissionRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

object PermissionGrantEngine {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val pendingTransactions = ConcurrentHashMap<String, PendingPermissionTransaction>()
    private val terminalTransactions = ConcurrentHashMap<String, String>() // requestId -> terminal state (CANCELED, GRANTED, DENIED, EXPIRED, FAILED)
    private val mainHandler by lazy {
        try {
            Handler(Looper.getMainLooper())
        } catch (_: Throwable) {
            null
        }
    }

    fun registerPendingTransaction(transaction: PendingPermissionTransaction) {
        val existing = pendingTransactions[transaction.requestId]
        if (existing != null) {
            existing.enrich(
                incomingRequest = transaction.request,
                incomingContext = transaction.context,
                incomingUniversal = transaction.universalRequest,
                incomingResources = transaction.resources,
                incomingCallback = transaction.onResultCallback,
                incomingPreferredDecision = transaction.preferredDecision
            )
            return
        }

        val normalizedOrigin = OriginNormalizer.normalize(transaction.origin)
        pendingTransactions[transaction.requestId] = transaction
        terminalTransactions.remove(transaction.requestId)

        // Schedule real timeout mechanism for this transaction
        val delayMs = (transaction.expiration - System.currentTimeMillis()).coerceAtLeast(100L)
        mainHandler?.postDelayed({
            expireTransaction(transaction.requestId)
        }, delayMs)
    }
    
    fun removePendingTransaction(requestId: String): PendingPermissionTransaction? {
        return pendingTransactions.remove(requestId)
    }
    
    fun getPendingTransaction(requestId: String): PendingPermissionTransaction? {
        checkExpirations()
        return pendingTransactions[requestId]
    }

    fun checkExpirations() {
        val now = System.currentTimeMillis()
        pendingTransactions.values.forEach { tx ->
            if (now >= tx.expiration) {
                expireTransaction(tx.requestId)
            }
        }
    }

    fun expireTransaction(requestId: String) {
        val tx = pendingTransactions[requestId] ?: return
        if (!tx.markTerminal(PermissionState.EXPIRED)) {
            return
        }
        pendingTransactions.remove(requestId)
        terminalTransactions[requestId] = "EXPIRED"

        PermissionDialogEngine.dismissPrompt(requestId)
        tx.dispatchResult("EXPIRED")
        mainHandler?.post {
            try {
                tx.request?.deny()
            } catch (e: Exception) {
                PermissionLogger.logFailure(tx.origin, "EXPIRED", "Failed request.deny on timeout", e.toString())
            }
        }

        PermissionDiagnostics.updateTraceStage(
            requestId = requestId,
            stage = "EXPIRED",
            status = "FAILED",
            reason = "Permission request timed out",
            finalResult = "EXPIRED"
        )
    }

    fun cancelPendingTransaction(requestId: String) {
        val tx = pendingTransactions[requestId]
        if (tx == null) {
            val state = terminalTransactions[requestId]
            if (state != null) {
                PermissionLogger.logEvent("PermissionGrantEngine", "CANCELED", "STALE_PERMISSION_CALLBACK", "Ignored duplicate cancellation for terminal requestId: $requestId ($state)")
            }
            return
        }

        if (!tx.markTerminal(PermissionState.CANCELED)) {
            return
        }
        pendingTransactions.remove(requestId)
        terminalTransactions[requestId] = "CANCELED"

        PermissionDialogEngine.dismissPrompt(requestId)
        tx.dispatchResult("CANCELED")
        mainHandler?.post {
            try {
                tx.request?.deny()
            } catch (e: Exception) {
                PermissionLogger.logFailure(tx.origin, "CANCELED", "Failed request.deny on cancel", e.toString())
            }
        }
        PermissionDiagnostics.updateTraceStage(
            requestId = requestId,
            stage = "CANCELED",
            status = "SUCCESS",
            reason = "Permission transaction canceled",
            finalResult = "CANCELED"
        )
    }

    fun cancelPendingTransactionsForTab(tabId: String) {
        val matching = pendingTransactions.values.filter { it.tabId == tabId }
        matching.forEach { tx ->
            cancelPendingTransaction(tx.requestId)
        }
    }

    fun cancelPendingTransactionsForOrigin(origin: String) {
        val normalized = OriginNormalizer.normalize(origin)
        val matching = pendingTransactions.values.filter { 
            OriginNormalizer.normalize(it.origin) == normalized 
        }
        matching.forEach { tx ->
            cancelPendingTransaction(tx.requestId)
        }
    }

    fun cancelPendingTransactionsForRequest(request: PermissionRequest) {
        val matching = pendingTransactions.values.filter { it.request == request }
        matching.forEach { tx ->
            cancelPendingTransaction(tx.requestId)
        }
    }
    
    fun cancelAllPendingTransactions() {
        val all = pendingTransactions.values.toList()
        all.forEach { tx ->
            cancelPendingTransaction(tx.requestId)
        }
        pendingTransactions.clear()
    }

    fun applyGrant(
        requestId: String,
        origin: String,
        permissionType: String,
        decision: String, // "ALLOW_ONCE", "ALLOW_ALWAYS"
        repository: SitePermissionRepository? = null,
        allowedResources: List<String>? = null,
        isIncognito: Boolean = false,
        onNativeGrantNeeded: (() -> Unit)? = null
    ) {
        val normOrigin = OriginNormalizer.normalize(origin)
        val transaction = pendingTransactions[requestId]
        val terminalState = terminalTransactions[requestId]

        if (transaction != null) {
            val txNormOrigin = OriginNormalizer.normalize(transaction.origin)
            if (txNormOrigin != normOrigin) {
                PermissionLogger.logFailure(normOrigin, permissionType, "ORIGIN_MISMATCH", "Origin mismatch on grant: $normOrigin vs tx $txNormOrigin")
                return
            }
            if (!transaction.markTerminal(PermissionState.GRANTED)) {
                PermissionLogger.logEvent(normOrigin, permissionType, "STALE_PERMISSION_CALLBACK", "Ignored grant callback for non-active transaction state: $requestId (${transaction.decisionState})")
                return
            }
            pendingTransactions.remove(requestId)
            transaction.dispatchResult("ALLOW")
        } else {
            if (terminalState != null) {
                PermissionLogger.logEvent(normOrigin, permissionType, "STALE_PERMISSION_CALLBACK", "Ignored grant callback for already terminal transaction: $requestId ($terminalState)")
                return
            }
        }

        terminalTransactions[requestId] = "GRANTED"
        val incognito = isIncognito || (transaction?.isIncognito == true)

        // Cache decision using canonical origin
        if (incognito) {
            PermissionCache.cacheIncognitoDecision(normOrigin, permissionType, if (decision == "ALLOW_ALWAYS") "ALLOW_ALWAYS" else "ALLOW_ONCE")
        } else if (decision == "ALLOW_ONCE") {
            PermissionCache.cacheSessionDecision(normOrigin, permissionType, "ALLOW_ONCE")
        } else {
            PermissionCache.cachePersistentDecision(normOrigin, permissionType, "ALLOW_ALWAYS")
            if (repository != null) {
                scope.launch {
                    repository.savePermission(normOrigin, permissionType, "ALLOW_ALWAYS")
                }
            }
        }

        PermissionDiagnostics.updateTraceStage(
            requestId = requestId,
            stage = "GRANTED",
            status = "SUCCESS",
            reason = "Permission granted as: $decision (Incognito: $incognito)",
            finalResult = "ALLOW"
        )

        onNativeGrantNeeded?.invoke()

        val nativeRequest = transaction?.request
        if (nativeRequest != null) {
            try {
                mainHandler?.post {
                    try {
                        val finalResources = allowedResources?.toTypedArray() ?: emptyArray()
                        if (finalResources.isNotEmpty()) {
                            nativeRequest.grant(finalResources)
                        } else {
                            nativeRequest.deny()
                        }
                        PermissionLogger.logSuccess(
                            origin = normOrigin,
                            permission = permissionType,
                            androidResult = "GRANTED",
                            grantResult = "WEBVIEW_GRANTED",
                            verificationResult = "VERIFIED_ACTIVE"
                        )
                    } catch (e: Exception) {
                        PermissionLogger.logFailure(normOrigin, permissionType, "WebView request.grant exception", e.toString())
                    }
                }
            } catch (e: Exception) {
                PermissionLogger.logFailure(normOrigin, permissionType, "WebView request.grant main looper post failed", e.toString())
            }
        }
    }

    fun applyDeny(
        requestId: String,
        origin: String,
        permissionType: String,
        decision: String, // "BLOCK"
        repository: SitePermissionRepository? = null,
        isIncognito: Boolean = false
    ) {
        val normOrigin = OriginNormalizer.normalize(origin)
        val transaction = pendingTransactions[requestId]
        val terminalState = terminalTransactions[requestId]

        if (transaction != null) {
            val txNormOrigin = OriginNormalizer.normalize(transaction.origin)
            if (txNormOrigin != normOrigin) {
                PermissionLogger.logFailure(normOrigin, permissionType, "ORIGIN_MISMATCH", "Origin mismatch on deny: $normOrigin vs tx $txNormOrigin")
                return
            }
            if (!transaction.markTerminal(PermissionState.DENIED)) {
                PermissionLogger.logEvent(normOrigin, permissionType, "STALE_PERMISSION_CALLBACK", "Ignored deny callback for non-active transaction state: $requestId (${transaction.decisionState})")
                return
            }
            pendingTransactions.remove(requestId)
            transaction.dispatchResult("BLOCK")
        } else {
            if (terminalState != null) {
                PermissionLogger.logEvent(normOrigin, permissionType, "STALE_PERMISSION_CALLBACK", "Ignored deny callback for already terminal transaction: $requestId ($terminalState)")
                return
            }
        }

        terminalTransactions[requestId] = "DENIED"
        val incognito = isIncognito || (transaction?.isIncognito == true)

        if (incognito) {
            PermissionCache.cacheIncognitoDecision(normOrigin, permissionType, "BLOCK")
        } else if (decision == "BLOCK_ONCE") {
            PermissionCache.cacheSessionDecision(normOrigin, permissionType, "BLOCK_ONCE")
        } else {
            PermissionCache.cachePersistentDecision(normOrigin, permissionType, "BLOCK")
            if (repository != null) {
                scope.launch {
                    repository.savePermission(normOrigin, permissionType, "BLOCK")
                }
            }
        }

        PermissionDiagnostics.updateTraceStage(
            requestId = requestId,
            stage = "DENIED",
            status = "SUCCESS",
            reason = "Permission denied / blocked by engine rule",
            finalResult = "BLOCK"
        )

        val nativeRequest = transaction?.request
        if (nativeRequest != null) {
            try {
                mainHandler?.post {
                    try {
                        nativeRequest.deny()
                        PermissionLogger.logFailure(normOrigin, permissionType, "WebView permission request denied by engine rule")
                    } catch (e: Exception) {
                        PermissionLogger.logFailure(normOrigin, permissionType, "WebView request.deny failed", e.toString())
                    }
                }
            } catch (e: Exception) {
                PermissionLogger.logFailure(normOrigin, permissionType, "WebView request.deny main looper failed", e.toString())
            }
        }
    }

    /**
     * Canonical method for granting verified resources for an active pending transaction.
     */
    fun grantVerifiedResources(
        requestId: String,
        origin: String,
        permissionType: String,
        decision: String = "ALLOW_ALWAYS",
        repository: SitePermissionRepository? = null,
        allowedResources: List<String>? = null,
        isIncognito: Boolean = false,
        onNativeGrantNeeded: (() -> Unit)? = null
    ) {
        applyGrant(
            requestId = requestId,
            origin = origin,
            permissionType = permissionType,
            decision = decision,
            repository = repository,
            allowedResources = allowedResources,
            isIncognito = isIncognito,
            onNativeGrantNeeded = onNativeGrantNeeded
        )
    }

    /**
     * Canonical method for denying a request for an active pending transaction.
     */
    fun denyRequest(
        requestId: String,
        origin: String = "",
        permissionType: String = "UNKNOWN",
        decision: String = "BLOCK",
        repository: SitePermissionRepository? = null,
        isIncognito: Boolean = false
    ) {
        applyDeny(
            requestId = requestId,
            origin = origin,
            permissionType = permissionType,
            decision = decision,
            repository = repository,
            isIncognito = isIncognito
        )
    }

    /**
     * Safely dispatches a denial for a [PermissionRequest] when a request is malformed,
     * invalid, or rejected before a [PendingPermissionTransaction] has been registered.
     * Guarantees that no fake transaction is created that could conflict with real transactions,
     * dispatches the denial on the Main thread, and records the rejection event in diagnostics/logs.
     */
    fun rejectUnregisteredRequest(
        request: PermissionRequest?,
        reason: String,
        origin: String = "UNKNOWN_ORIGIN",
        requestId: String = "UNREGISTERED",
        permissionType: String = "UNKNOWN"
    ) {
        PermissionDiagnostics.recordEvent(
            PermissionEventModel(
                eventId = "evt_" + System.nanoTime(),
                requestId = requestId,
                stage = "UNREGISTERED_REJECTION",
                status = "DENIED",
                reason = reason,
                fileName = "PermissionGrantEngine.kt",
                className = "PermissionGrantEngine",
                methodName = "rejectUnregisteredRequest",
                callbackName = "onPermissionRequest",
                details = "Safely rejected unregistered request. Origin: $origin"
            )
        )
        PermissionLogger.logFailure(
            origin = origin.ifBlank { "UNKNOWN_ORIGIN" },
            permission = permissionType,
            reason = "REJECTED_UNREGISTERED: $reason"
        )

        if (request != null) {
            mainHandler?.post {
                try {
                    request.deny()
                } catch (e: Exception) {
                    PermissionLogger.logFailure(
                        origin = origin,
                        permission = permissionType,
                        reason = "WEBVIEW_DENY_FAILED",
                        stackTrace = e.toString()
                    )
                }
            }
        }
    }
}

