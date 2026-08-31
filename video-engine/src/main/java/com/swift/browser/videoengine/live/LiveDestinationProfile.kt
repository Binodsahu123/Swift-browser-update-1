package com.swift.browser.videoengine.live

enum class StreamingProtocol {
    RTMP,
    RTMPS
}

enum class StreamKeyRequirement {
    REQUIRED,
    OPTIONAL,
    NONE
}

enum class AuthenticationType {
    NONE,
    BASIC,
    DIGEST,
    OAUTH
}

data class VideoRequirements(
    val maxWidth: Int = 1920,
    val maxHeight: Int = 1080,
    val maxFps: Int = 60,
    val minBitrate: Int = 1000_000,
    val maxBitrate: Int = 10000_000,
    val recommendedVideoCodec: String = "H264",
    val codecConstraints: Map<String, String> = emptyMap()
)

data class AudioRequirements(
    val recommendedAudioCodec: String = "AAC",
    val requiredSampleRate: Int = 44100,
    val requiredChannels: Int = 2,
    val recommendedBitrate: Int = 128_000,
    val codecConstraints: Map<String, String> = emptyMap()
)

data class LiveStreamCredentials(
    val streamKey: String,
    val username: String? = null,
    val password: String? = null,
    val token: String? = null
) {
    override fun toString(): String {
        return "LiveStreamCredentials(streamKey=[REDACTED], username=[REDACTED], password=[REDACTED], token=[REDACTED])"
    }
}

data class LiveDestinationProfile(
    val id: String,
    val displayName: String,
    val streamingProtocol: StreamingProtocol,
    val defaultServer: String,
    val defaultApplication: String,
    val streamKeyRequirement: StreamKeyRequirement = StreamKeyRequirement.REQUIRED,
    val recommendedWidth: Int = 1280,
    val recommendedHeight: Int = 720,
    val recommendedBitrate: Int = 2500_000,
    val recommendedFps: Int = 30,
    val audioRequirements: AudioRequirements = AudioRequirements(),
    val videoRequirements: VideoRequirements = VideoRequirements(),
    val requiresTls: Boolean = false,
    val metadataRequirements: Map<String, String> = emptyMap(),
    val authenticationType: AuthenticationType = AuthenticationType.NONE
) {
    val protocol: StreamingProtocol get() = streamingProtocol

    fun toLiveDestination(): LiveDestination {
        return LiveDestination(
            id = id,
            displayName = displayName,
            type = if (id.lowercase() == "custom") LiveDestinationType.CUSTOM else LiveDestinationType.PRESET,
            protocolType = if (streamingProtocol == StreamingProtocol.RTMPS || requiresTls) LiveTransportProtocol.RTMPS else LiveTransportProtocol.RTMP,
            host = defaultServer,
            port = if (streamingProtocol == StreamingProtocol.RTMPS || requiresTls) 443 else 1935,
            application = defaultApplication,
            streamPath = "",
            streamKeyRequired = streamKeyRequirement == StreamKeyRequirement.REQUIRED,
            usernameRequired = authenticationType != AuthenticationType.NONE,
            passwordRequired = authenticationType != AuthenticationType.NONE,
            tlsRequired = requiresTls || streamingProtocol == StreamingProtocol.RTMPS,
            supportsAudio = true,
            supportsVideo = true,
            supportsScreen = true,
            recommendedWidth = recommendedWidth,
            recommendedHeight = recommendedHeight,
            recommendedFps = recommendedFps,
            recommendedVideoBitrate = recommendedBitrate,
            recommendedAudioBitrate = audioRequirements.recommendedBitrate,
            recommendedKeyframeInterval = 2,
            videoCodec = videoRequirements.recommendedVideoCodec,
            audioCodec = audioRequirements.recommendedAudioCodec,
            metadataMode = "FLV",
            maxBitrate = videoRequirements.maxBitrate,
            minBitrate = videoRequirements.minBitrate
        )
    }

    fun toLiveStreamConfig(
        inputStreamKey: String = "",
        width: Int = 1280,
        height: Int = 720,
        fps: Int = 30,
        videoBitrate: Int = 2500_000,
        audioBitrate: Int = 128_000
    ): LiveStreamConfig {
        return toLiveDestination().toLiveStreamConfig(
            inputStreamKey = inputStreamKey,
            width = width,
            height = height,
            fps = fps,
            videoBitrate = videoBitrate,
            audioBitrate = audioBitrate
        )
    }
}

object LiveDestinationProfileRegistry {
    private val profiles = java.util.concurrent.ConcurrentHashMap<String, LiveDestinationProfile>()

    init {
        registerDefaultProfiles()
    }

    fun register(profile: LiveDestinationProfile) {
        profiles[profile.id.lowercase()] = profile
    }

    fun unregister(id: String) {
        profiles.remove(id.lowercase())
    }

    fun get(id: String): LiveDestinationProfile? {
        return profiles[id.lowercase()]
    }

    fun getAll(): List<LiveDestinationProfile> {
        return profiles.values.toList()
    }

    fun clear() {
        profiles.clear()
    }

    fun registerDefaultProfiles() {
        profiles.clear()

        register(
            LiveDestinationProfile(
                id = "youtube",
                displayName = "YouTube Live",
                streamingProtocol = StreamingProtocol.RTMPS,
                defaultServer = "a.rtmp.youtube.com",
                defaultApplication = "live2",
                streamKeyRequirement = StreamKeyRequirement.REQUIRED,
                recommendedWidth = 3840,
                recommendedHeight = 2160,
                recommendedBitrate = 4500_000,
                recommendedFps = 60,
                audioRequirements = AudioRequirements(
                    recommendedAudioCodec = "AAC",
                    requiredSampleRate = 44100,
                    requiredChannels = 2,
                    recommendedBitrate = 128_000
                ),
                videoRequirements = VideoRequirements(
                    maxWidth = 3840,
                    maxHeight = 2160,
                    maxFps = 60,
                    minBitrate = 1500_000,
                    maxBitrate = 8500_000,
                    recommendedVideoCodec = "H264"
                ),
                requiresTls = true,
                metadataRequirements = mapOf("backupUrl" to "b.rtmp.youtube.com"),
                authenticationType = AuthenticationType.NONE
            )
        )

        register(
            LiveDestinationProfile(
                id = "facebook",
                displayName = "Facebook Live",
                streamingProtocol = StreamingProtocol.RTMPS,
                defaultServer = "live-api-s.facebook.com",
                defaultApplication = "rtmp",
                streamKeyRequirement = StreamKeyRequirement.REQUIRED,
                recommendedWidth = 1280,
                recommendedHeight = 720,
                recommendedBitrate = 2500_000,
                recommendedFps = 30,
                audioRequirements = AudioRequirements(
                    recommendedAudioCodec = "AAC",
                    requiredSampleRate = 44100,
                    requiredChannels = 2,
                    recommendedBitrate = 128_000
                ),
                videoRequirements = VideoRequirements(
                    maxWidth = 1920,
                    maxHeight = 1080,
                    maxFps = 30,
                    minBitrate = 1000_000,
                    maxBitrate = 4000_000,
                    recommendedVideoCodec = "H264"
                ),
                requiresTls = true,
                authenticationType = AuthenticationType.NONE
            )
        )

        register(
            LiveDestinationProfile(
                id = "twitch",
                displayName = "Twitch",
                streamingProtocol = StreamingProtocol.RTMP,
                defaultServer = "live.twitch.tv",
                defaultApplication = "app",
                streamKeyRequirement = StreamKeyRequirement.REQUIRED,
                recommendedWidth = 1920,
                recommendedHeight = 1080,
                recommendedBitrate = 4500_000,
                recommendedFps = 60,
                audioRequirements = AudioRequirements(
                    recommendedAudioCodec = "AAC",
                    requiredSampleRate = 44100,
                    requiredChannels = 2,
                    recommendedBitrate = 128_000
                ),
                videoRequirements = VideoRequirements(
                    maxWidth = 1920,
                    maxHeight = 1080,
                    maxFps = 60,
                    minBitrate = 3000_000,
                    maxBitrate = 6000_000,
                    recommendedVideoCodec = "H264"
                ),
                requiresTls = false,
                authenticationType = AuthenticationType.NONE
            )
        )

        register(
            LiveDestinationProfile(
                id = "custom",
                displayName = "Custom RTMP",
                streamingProtocol = StreamingProtocol.RTMP,
                defaultServer = "",
                defaultApplication = "",
                streamKeyRequirement = StreamKeyRequirement.OPTIONAL,
                recommendedWidth = 1280,
                recommendedHeight = 720,
                recommendedBitrate = 2500_000,
                recommendedFps = 30,
                audioRequirements = AudioRequirements(
                    recommendedAudioCodec = "AAC",
                    requiredSampleRate = 44100,
                    requiredChannels = 2,
                    recommendedBitrate = 128_000
                ),
                videoRequirements = VideoRequirements(
                    maxWidth = 3840,
                    maxHeight = 2160,
                    maxFps = 60,
                    minBitrate = 200_000,
                    maxBitrate = 20_000_000,
                    recommendedVideoCodec = "H264"
                ),
                requiresTls = false,
                authenticationType = AuthenticationType.NONE
            )
        )
    }
}

sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val code: String, val message: String) : ValidationResult()
}

object LiveDestinationValidator {
    fun validate(
        destination: LiveDestination,
        credentials: LiveStreamCredentials,
        width: Int,
        height: Int,
        fps: Int,
        videoBitrate: Int
    ): ValidationResult {
        if (destination.streamKeyRequired && credentials.streamKey.isBlank()) {
            return ValidationResult.Error("MISSING_STREAM_KEY", "Stream key is required for ${destination.displayName}")
        }

        if (destination.host.isBlank()) {
            return ValidationResult.Error("INVALID_URL", "Server URL/Host cannot be empty")
        }

        if (destination.port < 0 || destination.port > 65535) {
            return ValidationResult.Error("INVALID_PORT", "Invalid port number. Must be between 0 and 65535")
        }

        if (destination.application.isBlank()) {
            return ValidationResult.Error("INVALID_APPLICATION", "Application path cannot be empty")
        }

        if (destination.tlsRequired && destination.protocolType != LiveTransportProtocol.RTMPS) {
            return ValidationResult.Error("TLS_REQUIRED", "TLS/SSL is required for ${destination.displayName}")
        }

        if (videoBitrate < destination.minBitrate || videoBitrate > destination.maxBitrate) {
            return ValidationResult.Error(
                "UNSUPPORTED_CODEC_CONFIG",
                "Bitrate $videoBitrate is out of bounds [${destination.minBitrate}, ${destination.maxBitrate}] for ${destination.displayName}"
            )
        }

        if (width <= 0 || width > destination.recommendedWidth) {
            return ValidationResult.Error(
                "UNSUPPORTED_CODEC_CONFIG",
                "Resolution width $width exceeds maximum allowed for ${destination.displayName}"
            )
        }

        if (height <= 0 || height > destination.recommendedHeight) {
            return ValidationResult.Error(
                "UNSUPPORTED_CODEC_CONFIG",
                "Resolution height $height exceeds maximum allowed for ${destination.displayName}"
            )
        }

        if (fps <= 0 || fps > destination.recommendedFps) {
            return ValidationResult.Error(
                "UNSUPPORTED_CODEC_CONFIG",
                "FPS $fps exceeds maximum allowed for ${destination.displayName}"
            )
        }

        return ValidationResult.Success
    }

    fun validate(
        profile: LiveDestinationProfile,
        serverUrl: String,
        streamKey: String,
        port: Int,
        application: String,
        width: Int,
        height: Int,
        fps: Int,
        videoBitrate: Int
    ): ValidationResult {
        val lowerUrl = serverUrl.trim().lowercase()
        if (lowerUrl.isEmpty() || lowerUrl == "rtmp://" || lowerUrl == "rtmps://") {
            return ValidationResult.Error("INVALID_URL", "Server URL/Host cannot be empty or invalid")
        }

        if (lowerUrl.contains("://") && !lowerUrl.startsWith("rtmp://") && !lowerUrl.startsWith("rtmps://")) {
            return ValidationResult.Error("UNSUPPORTED_PROTOCOL", "Unsupported protocol in URL")
        }

        val isUrlSecure = lowerUrl.startsWith("rtmps://")
        val isUrlInsecure = lowerUrl.startsWith("rtmp://")
        val profileRequiresTls = profile.requiresTls || profile.streamingProtocol == StreamingProtocol.RTMPS

        if (profileRequiresTls && isUrlInsecure) {
            return ValidationResult.Error("TLS_MISMATCH", "TLS is required by profile, but insecure rtmp:// URL was provided")
        }

        var cleanHost = serverUrl.trim()
        if (cleanHost.startsWith("rtmp://", ignoreCase = true)) {
            cleanHost = cleanHost.substring(7)
        } else if (cleanHost.startsWith("rtmps://", ignoreCase = true)) {
            cleanHost = cleanHost.substring(8)
        }
        cleanHost = cleanHost.substringBefore("/").substringBefore(":")

        val dest = LiveDestination(
            id = profile.id,
            displayName = profile.displayName,
            type = if (profile.id.lowercase() == "custom") LiveDestinationType.CUSTOM else LiveDestinationType.PRESET,
            protocolType = if (isUrlSecure || profileRequiresTls) LiveTransportProtocol.RTMPS else LiveTransportProtocol.RTMP,
            host = cleanHost,
            port = port,
            application = application,
            streamKeyRequired = profile.streamKeyRequirement == StreamKeyRequirement.REQUIRED,
            tlsRequired = profileRequiresTls || profile.streamingProtocol == StreamingProtocol.RTMPS,
            recommendedWidth = profile.recommendedWidth,
            recommendedHeight = profile.recommendedHeight,
            recommendedFps = profile.recommendedFps,
            recommendedVideoBitrate = profile.recommendedBitrate,
            maxBitrate = profile.videoRequirements.maxBitrate,
            minBitrate = profile.videoRequirements.minBitrate
        )
        val creds = LiveStreamCredentials(streamKey)
        return validate(dest, creds, width, height, fps, videoBitrate)
    }
}
