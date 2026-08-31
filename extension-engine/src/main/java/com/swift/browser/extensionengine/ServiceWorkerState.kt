package com.swift.browser.extensionengine

import java.util.UUID

/**
 * Android WebView public APIs do not provide a true V8 isolate worker thread detached from UI contexts.
 * We explicitly expose WORKER_RUNTIME_CAPABILITY to indicate WebView-backed service worker lifecycle implementation.
 */
const val PARTIAL_SERVICE_WORKER_SUPPORT = true
const val WORKER_RUNTIME_CAPABILITY = "WEBVIEW_BACKED_SERVICE_WORKER_RUNTIME"

enum class ServiceWorkerState {
    REGISTERED,
    STOPPED,
    STARTING,
    RUNNING,
    IDLE,
    SUSPENDING,
    SUSPENDED,
    CRASHED,
    RESTARTING,
    RESTART_PENDING,
    DISABLED,
    UNINSTALLED,
    FAILED,
    NOT_REGISTERED,

    // Legacy aliases to keep perfect backward compatibility with older components and tests
    DORMANT,
    WAKE,
    EVENT,
    ACTIVE,
    SUSPEND;

    fun canTransitionTo(target: ServiceWorkerState): Boolean {
        if (this == target) return true
        if (this == UNINSTALLED) {
            // Do not allow: UNINSTALLED -> RUNNING or other active states
            if (target == RUNNING || target == STARTING || target == ACTIVE || target == EVENT || target == RESTARTING || target == WAKE) return false
        }
        if (this == DISABLED) {
            // Disabled extensions cannot transition directly to active execution states
            if (target == RUNNING || target == STARTING || target == ACTIVE || target == EVENT || target == WAKE) return false
        }
        return when (this) {
            NOT_REGISTERED -> target == REGISTERED || target == DORMANT
            REGISTERED -> target == STARTING || target == STOPPED || target == DISABLED || target == UNINSTALLED || target == DORMANT || target == WAKE || target == SUSPEND || target == RUNNING || target == SUSPENDED
            STOPPED -> target == STARTING || target == REGISTERED || target == DISABLED || target == UNINSTALLED || target == DORMANT
            STARTING -> target == RUNNING || target == STOPPED || target == CRASHED || target == FAILED || target == ACTIVE || target == EVENT || target == SUSPENDED || target == DORMANT
            RUNNING -> target == IDLE || target == SUSPENDING || target == CRASHED || target == STOPPED || target == ACTIVE || target == EVENT || target == SUSPENDED || target == DISABLED || target == UNINSTALLED || target == FAILED
            IDLE -> target == SUSPENDING || target == RUNNING || target == STARTING || target == STOPPED || target == ACTIVE || target == EVENT || target == SUSPEND || target == DORMANT || target == SUSPENDED || target == DISABLED
            SUSPENDING -> target == SUSPENDED || target == RUNNING || target == STARTING || target == STOPPED || target == DORMANT
            SUSPENDED -> target == STARTING || target == REGISTERED || target == STOPPED || target == DISABLED || target == UNINSTALLED || target == DORMANT || target == WAKE
            CRASHED -> target == RESTARTING || target == RESTART_PENDING || target == STOPPED || target == FAILED || target == DISABLED || target == UNINSTALLED || target == SUSPENDED || target == DORMANT
            RESTARTING, RESTART_PENDING -> target == STARTING || target == STOPPED || target == FAILED || target == CRASHED || target == SUSPENDED
            DISABLED -> target == STOPPED || target == REGISTERED || target == UNINSTALLED || target == DORMANT || target == SUSPENDED
            UNINSTALLED -> target == STOPPED || target == NOT_REGISTERED
            FAILED -> target == STARTING || target == STOPPED || target == REGISTERED || target == DISABLED || target == UNINSTALLED

            // Legacy mappings to keep older codebase parts running seamlessly
            DORMANT -> target == WAKE || target == SUSPEND || target == REGISTERED || target == STARTING || target == ACTIVE || target == STOPPED || target == RUNNING || target == IDLE || target == SUSPENDED
            WAKE -> target == EVENT || target == ACTIVE || target == DORMANT || target == SUSPEND || target == RUNNING || target == STARTING
            EVENT -> target == ACTIVE || target == IDLE || target == SUSPEND || target == DORMANT || target == RUNNING
            ACTIVE -> target == IDLE || target == EVENT || target == SUSPEND || target == DORMANT || target == RUNNING || target == SUSPENDED
            SUSPEND -> target == DORMANT || target == WAKE || target == REGISTERED || target == SUSPENDED || target == IDLE
        }
    }
}

enum class WorkerStartupResult {
    SUCCESS,
    FAILED,
    TIMEOUT
}

enum class WorkerEventDeliveryState {
    QUEUED,
    DELIVERING,
    DELIVERED,
    FAILED,
    EXPIRED
}

data class WorkerEventDelivery(
    val eventId: String,
    val extensionId: String,
    val generationId: Int,
    val eventName: String,
    var state: WorkerEventDeliveryState = WorkerEventDeliveryState.QUEUED,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Encapsulates the execution context of an active Service Worker instance.
 */
data class ServiceWorkerExecutionContext(
    val extensionId: String,
    val manifestVersion: Int = 3,
    val workerGenerationId: Int = 1,
    val registrationId: String = UUID.randomUUID().toString(),
    val startTime: Long = System.currentTimeMillis(),
    val wakeReason: String = "INITIAL_REGISTRATION",
    val isPrivate: Boolean = false,
    val privateSessionId: String? = null,
    val enabled: Boolean = true,
    var state: ServiceWorkerState = ServiceWorkerState.REGISTERED
)

data class ServiceWorkerRegistration(
    val extensionId: String,
    val scriptPath: String,
    var state: ServiceWorkerState = ServiceWorkerState.REGISTERED,
    var lastActiveTimestamp: Long = System.currentTimeMillis(),
    val isMV3: Boolean = true,
    val pendingEvents: MutableList<QueuedServiceWorkerEvent> = mutableListOf(),
    var type: String = "classic",
    var enabled: Boolean = true,
    var privateAllowed: Boolean = false,
    var workerGenerationId: Int = 1,
    var registeredAt: Long = System.currentTimeMillis(),
    var lastStartAt: Long = 0L,
    var lastStopAt: Long = 0L,
    var lastEventAt: Long = 0L,
    var crashCount: Int = 0,
    var restartCount: Int = 0,
    var wakeReason: String = "",
    val activeCallbacks: MutableSet<String> = mutableSetOf(),
    var executionContext: ServiceWorkerExecutionContext? = null
)

data class QueuedServiceWorkerEvent(
    val eventId: String,
    val eventName: String,
    val payload: Any?,
    val timestamp: Long = System.currentTimeMillis(),
    var deliveryState: WorkerEventDeliveryState = WorkerEventDeliveryState.QUEUED,
    val generationId: Int = 1
)

