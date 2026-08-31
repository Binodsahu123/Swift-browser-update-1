package com.swift.browser.permissionengine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PermissionState(val isTerminal: Boolean) {
    PENDING(false),
    WAITING_USER(false),
    WAITING_ANDROID(false),
    GRANTING(false),
    ALLOWED(true),
    GRANTED(true),
    DENIED(true),
    CANCELED(true),
    EXPIRED(true),
    FAILED(true);
}

class PermissionStateMachine(
    val requestId: String = "",
    initialState: PermissionState = PermissionState.PENDING
) {
    private val _currentState = MutableStateFlow(initialState)
    val currentState: StateFlow<PermissionState> = _currentState.asStateFlow()

    @Synchronized
    fun transitionTo(newState: PermissionState): Boolean {
        val current = _currentState.value
        if (current.isTerminal) {
            try {
                android.util.Log.w("PermissionStateMachine", "[$requestId] Rejected transition from terminal state $current to $newState")
            } catch (_: Throwable) {}
            return false
        }

        val isValid = when (current) {
            PermissionState.PENDING -> true
            PermissionState.WAITING_USER -> newState in setOf(
                PermissionState.WAITING_ANDROID,
                PermissionState.GRANTING,
                PermissionState.ALLOWED,
                PermissionState.GRANTED,
                PermissionState.DENIED,
                PermissionState.CANCELED,
                PermissionState.EXPIRED,
                PermissionState.FAILED
            )
            PermissionState.WAITING_ANDROID -> newState in setOf(
                PermissionState.GRANTING,
                PermissionState.ALLOWED,
                PermissionState.GRANTED,
                PermissionState.DENIED,
                PermissionState.CANCELED,
                PermissionState.EXPIRED,
                PermissionState.FAILED
            )
            PermissionState.GRANTING -> newState in setOf(
                PermissionState.ALLOWED,
                PermissionState.GRANTED,
                PermissionState.DENIED,
                PermissionState.CANCELED,
                PermissionState.EXPIRED,
                PermissionState.FAILED
            )
            else -> false
        }

        if (isValid) {
            try {
                android.util.Log.d("PermissionStateMachine", "[$requestId] State transition: $current -> $newState")
            } catch (_: Throwable) {}
            _currentState.value = newState
            return true
        } else {
            try {
                android.util.Log.e("PermissionStateMachine", "[$requestId] Invalid transition: $current -> $newState")
            } catch (_: Throwable) {}
            return false
        }
    }

    fun reset() {
        _currentState.value = PermissionState.PENDING
    }
}

