package com.swift.browser.permissionengine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PermissionRecoveryEngine {
    private val _recoveredRequests = MutableStateFlow<Map<String, PermissionRequestModel>>(emptyMap())
    val recoveredRequests: StateFlow<Map<String, PermissionRequestModel>> = _recoveredRequests.asStateFlow()

    private val activeRequestHistory = mutableMapOf<String, PermissionRequestModel>()

    fun trackActiveRequest(request: PermissionRequestModel) {
        activeRequestHistory[request.requestId] = request
        _recoveredRequests.value = activeRequestHistory.toMap()
    }

    fun removeActiveRequest(requestId: String) {
        activeRequestHistory.remove(requestId)
        _recoveredRequests.value = activeRequestHistory.toMap()
    }

    fun getRequestForRecovery(requestId: String): PermissionRequestModel? {
        return activeRequestHistory[requestId]
    }

    fun handleAppRotationOrRestore() {
        PermissionLogger.logEvent("recovery_engine", "RECOVER", "RECOVERY_ACTIVE", "Re-syncing permission state machine across system life cycle.")
        // Keep active states in track but refresh standard connections
    }
}
