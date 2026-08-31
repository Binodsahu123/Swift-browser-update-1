package com.swift.browser.extensionengine

import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks in-flight network requests and lifecycle stages.
 */
class WebRequestRegistry {
    enum class RequestStage {
        BEFORE_REQUEST,
        BEFORE_SEND_HEADERS,
        SEND_HEADERS,
        HEADERS_RECEIVED,
        RESPONSE_STARTED,
        BEFORE_REDIRECT,
        COMPLETED,
        ERROR_OCCURRED
    }

    data class TrackedRequest(
        val context: ExtensionNetworkRequestContext,
        var currentStage: RequestStage = RequestStage.BEFORE_REQUEST,
        val startTime: Long = System.currentTimeMillis(),
        var finishTime: Long? = null,
        var error: String? = null,
        var redirectUrl: String? = null
    )

    private val inFlightRequests = ConcurrentHashMap<String, TrackedRequest>()

    fun trackRequest(context: ExtensionNetworkRequestContext): TrackedRequest {
        val tracked = TrackedRequest(context)
        inFlightRequests[context.requestId] = tracked
        return tracked
    }

    fun getRequest(requestId: String): TrackedRequest? {
        return inFlightRequests[requestId]
    }

    fun updateStage(requestId: String, stage: RequestStage) {
        inFlightRequests[requestId]?.currentStage = stage
    }

    fun completeRequest(requestId: String) {
        val req = inFlightRequests.remove(requestId)
        req?.finishTime = System.currentTimeMillis()
        req?.currentStage = RequestStage.COMPLETED
    }

    fun errorRequest(requestId: String, error: String) {
        val req = inFlightRequests.remove(requestId)
        req?.finishTime = System.currentTimeMillis()
        req?.error = error
        req?.currentStage = RequestStage.ERROR_OCCURRED
    }

    fun getActiveRequestsCount(): Int {
        return inFlightRequests.size
    }

    fun clear() {
        inFlightRequests.clear()
    }
}
