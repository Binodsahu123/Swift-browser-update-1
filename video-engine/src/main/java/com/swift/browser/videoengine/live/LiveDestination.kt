package com.swift.browser.videoengine.live

enum class LiveDestinationType {
    RTMP,
    RTMPS,
    CUSTOM,
    PRESET
}

enum class LiveTransportProtocol {
    RTMP,
    RTMPS
}

enum class DestinationStatus {
    DESTINATION_VALID,
    DESTINATION_INVALID,
    TLS_REQUIRED,
    CREDENTIALS_REQUIRED,
    UNSUPPORTED_PROTOCOL,
    DEVICE_LIMITED,
    READY,
    NOT_READY
}

data class LiveDestination(
    val id: String,
    val displayName: String,
    val type: LiveDestinationType,
    val protocolType: LiveTransportProtocol,
    val host: String,
    val port: Int,
    val application: String,
    val streamPath: String = "",
    val streamKeyRequired: Boolean = true,
    val usernameRequired: Boolean = false,
    val passwordRequired: Boolean = false,
    val tlsRequired: Boolean = false,
    val supportsAudio: Boolean = true,
    val supportsVideo: Boolean = true,
    val supportsScreen: Boolean = true,
    val recommendedWidth: Int = 1280,
    val recommendedHeight: Int = 720,
    val recommendedFps: Int = 30,
    val recommendedVideoBitrate: Int = 2500_000,
    val recommendedAudioBitrate: Int = 128_000,
    val recommendedKeyframeInterval: Int = 2,
    val videoCodec: String = "H264",
    val audioCodec: String = "AAC",
    val metadataMode: String = "FLV",
    val maxBitrate: Int = 6000_000,
    val minBitrate: Int = 1000_000
) {
    // --- Legacy / Compatibility Properties & Methods ---
    val destinationId: String get() = id
    val serverUrl: String get() = host
    val requiresTls: Boolean get() = tlsRequired
    val maxWidth: Int get() = recommendedWidth
    val maxHeight: Int get() = recommendedHeight
    val maxFps: Int get() = recommendedFps
    val minimumBitrate: Int get() = minBitrate
    val maximumBitrate: Int get() = maxBitrate
    val metadataRequirements: Map<String, String> get() = emptyMap()
    val streamKey: String get() = ""

    val protocol: LiveDestinationType get() = when (type) {
        LiveDestinationType.CUSTOM -> LiveDestinationType.CUSTOM
        else -> if (tlsRequired || protocolType == LiveTransportProtocol.RTMPS) LiveDestinationType.RTMPS else LiveDestinationType.RTMP
    }

    constructor(
        destinationId: String,
        displayName: String,
        protocol: LiveDestinationType,
        serverUrl: String,
        port: Int,
        application: String,
        streamKey: String,
        requiresTls: Boolean,
        supportsVideo: Boolean = true,
        supportsAudio: Boolean = true,
        maxWidth: Int = 1920,
        maxHeight: Int = 1080,
        maxFps: Int = 30,
        recommendedVideoCodec: String = "H264",
        recommendedAudioCodec: String = "AAC",
        minimumBitrate: Int = 1000_000,
        maximumBitrate: Int = 4000_000,
        metadataRequirements: Map<String, String> = emptyMap()
    ) : this(
        id = destinationId,
        displayName = displayName,
        type = if (destinationId.lowercase() == "custom") LiveDestinationType.CUSTOM else LiveDestinationType.PRESET,
        protocolType = if (protocol == LiveDestinationType.RTMPS || requiresTls) LiveTransportProtocol.RTMPS else LiveTransportProtocol.RTMP,
        host = serverUrl,
        port = port,
        application = application,
        streamPath = "",
        streamKeyRequired = true,
        usernameRequired = false,
        passwordRequired = false,
        tlsRequired = requiresTls || protocol == LiveDestinationType.RTMPS,
        supportsAudio = supportsAudio,
        supportsVideo = supportsVideo,
        supportsScreen = true,
        recommendedWidth = maxWidth,
        recommendedHeight = maxHeight,
        recommendedFps = maxFps,
        recommendedVideoBitrate = (minimumBitrate + maximumBitrate) / 2,
        recommendedAudioBitrate = 128_000,
        recommendedKeyframeInterval = 2,
        videoCodec = recommendedVideoCodec,
        audioCodec = recommendedAudioCodec,
        metadataMode = "FLV",
        maxBitrate = maximumBitrate,
        minBitrate = minimumBitrate
    )

    fun toLiveStreamConfig(
        inputStreamKey: String = "",
        width: Int = 1280,
        height: Int = 720,
        fps: Int = 30,
        videoBitrate: Int = 2500_000,
        audioBitrate: Int = 128_000
    ): LiveStreamConfig {
        val streamingProtocol = if (protocolType == LiveTransportProtocol.RTMPS || tlsRequired) StreamingProtocol.RTMPS else StreamingProtocol.RTMP
        val fullServerUrl = if (application.isNotEmpty()) "$host/$application" else host
        val fullStreamUrl = RtmpUrlParser.buildUrl(
            serverUrl = fullServerUrl,
            port = port,
            protocol = streamingProtocol,
            tlsRequired = tlsRequired,
            streamKey = ""
        )
        
        return LiveStreamConfig(
            streamUrl = fullStreamUrl,
            streamKey = inputStreamKey,
            width = width.coerceIn(240, recommendedWidth),
            height = height.coerceIn(160, recommendedHeight),
            fps = fps.coerceIn(10, recommendedFps),
            videoBitrate = videoBitrate.coerceIn(minBitrate, maxBitrate),
            audioBitrate = audioBitrate,
            keyframeInterval = recommendedKeyframeInterval,
            audioSampleRate = 44100,
            audioChannels = 2
        )
    }
}

object LiveDestinationRegistry {
    private val customDestinations = java.util.concurrent.ConcurrentHashMap<String, LiveDestination>()

    fun register(destination: LiveDestination) {
        customDestinations[destination.id.lowercase()] = destination
    }

    fun unregister(destinationId: String) {
        customDestinations.remove(destinationId.lowercase())
    }

    fun get(destinationId: String): LiveDestination? {
        val lower = destinationId.lowercase()
        return customDestinations[lower] ?: LiveDestinationProfileRegistry.get(lower)?.toLiveDestination()
    }

    fun getAll(): List<LiveDestination> {
        val profileDestinations = LiveDestinationProfileRegistry.getAll().map { it.toLiveDestination() }
        val customs = customDestinations.values.filter { c -> profileDestinations.none { it.id.equals(c.id, ignoreCase = true) } }
        return profileDestinations + customs
    }

    fun clear() {
        customDestinations.clear()
        LiveDestinationProfileRegistry.clear()
    }

    fun registerDefaultPresets() {
        customDestinations.clear()
        LiveDestinationProfileRegistry.registerDefaultProfiles()
    }
}
