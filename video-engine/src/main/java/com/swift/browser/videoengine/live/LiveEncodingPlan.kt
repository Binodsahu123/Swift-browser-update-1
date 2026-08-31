package com.swift.browser.videoengine.live

data class LiveEncodingPlan(
    val requestedWidth: Int,
    val requestedHeight: Int,
    val requestedFps: Int,
    val requestedVideoBitrate: Int,
    val selectedWidth: Int,
    val selectedHeight: Int,
    val selectedFps: Int,
    val selectedVideoBitrate: Int,
    val actualWidth: Int,
    val actualHeight: Int,
    val actualFps: Int,
    val actualVideoBitrate: Int,
    val reasonForDowngrade: String
) {
    override fun toString(): String {
        return "LiveEncodingPlan(requested=${requestedWidth}x${requestedHeight}@${requestedFps}fps@${requestedVideoBitrate/1000}kbps, selected=${selectedWidth}x${selectedHeight}@${selectedFps}fps@${selectedVideoBitrate/1000}kbps, actual=${actualWidth}x${actualHeight}@${actualFps}fps@${actualVideoBitrate/1000}kbps, reason='$reasonForDowngrade')"
    }
}

object LiveEncodingPlanGenerator {
    fun generatePlan(
        destination: LiveDestination,
        requestedWidth: Int,
        requestedHeight: Int,
        requestedFps: Int,
        requestedVideoBitrate: Int
    ): LiveEncodingPlan {
        val selectedWidth = requestedWidth.coerceAtMost(destination.recommendedWidth)
        val selectedHeight = requestedHeight.coerceAtMost(destination.recommendedHeight)
        val selectedFps = requestedFps.coerceAtMost(destination.recommendedFps)
        val selectedVideoBitrate = requestedVideoBitrate.coerceIn(destination.minBitrate, destination.maxBitrate)

        // For actual capabilities, we'd clamp them if they exceed the absolute limits
        val actualWidth = selectedWidth
        val actualHeight = selectedHeight
        val actualFps = selectedFps
        val actualVideoBitrate = selectedVideoBitrate

        var reason = "REQUESTED"
        if (selectedWidth < requestedWidth || selectedHeight < requestedHeight) {
            reason = "LIMITED: Resolution downgraded to match destination recommended bounds"
        } else if (selectedFps < requestedFps) {
            reason = "LIMITED: FPS capped to match destination recommended bounds"
        } else if (selectedVideoBitrate < requestedVideoBitrate) {
            reason = "LIMITED: Bitrate capped to match destination max bounds"
        } else {
            reason = "SELECTED"
        }

        return LiveEncodingPlan(
            requestedWidth = requestedWidth,
            requestedHeight = requestedHeight,
            requestedFps = requestedFps,
            requestedVideoBitrate = requestedVideoBitrate,
            selectedWidth = selectedWidth,
            selectedHeight = selectedHeight,
            selectedFps = selectedFps,
            selectedVideoBitrate = selectedVideoBitrate,
            actualWidth = actualWidth,
            actualHeight = actualHeight,
            actualFps = actualFps,
            actualVideoBitrate = actualVideoBitrate,
            reasonForDowngrade = reason
        )
    }
}
