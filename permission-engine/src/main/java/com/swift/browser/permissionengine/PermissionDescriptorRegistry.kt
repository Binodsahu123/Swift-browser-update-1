package com.swift.browser.permissionengine

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class PersistenceMode {
    PERSISTENT,
    SESSION_ONLY,
    EPHEMERAL
}

enum class RequestHandlingMode {
    USER_PROMPT,
    POLICY_ONLY,
    PLATFORM_MANAGED,
    UNSUPPORTED,
    NATIVE_BRIDGE_REQUIRED
}

object PermissionIconResolver {
    fun getIcon(iconKey: String?): ImageVector {
        return when (iconKey?.lowercase()?.trim()) {
            "videocam" -> Icons.Default.Videocam
            "mic" -> Icons.Default.Mic
            "location" -> Icons.Default.LocationOn
            "notifications" -> Icons.Default.Notifications
            "launch" -> Icons.AutoMirrored.Filled.Launch
            "download" -> Icons.Default.Download
            "content_paste" -> Icons.Default.ContentPaste
            "lock" -> Icons.Default.Lock
            "folder" -> Icons.Default.Folder
            "perm_media" -> Icons.Default.PermMedia
            "music_note" -> Icons.Default.MusicNote
            "fullscreen" -> Icons.Default.Fullscreen
            "play_circle" -> Icons.Default.PlayCircle
            "sensors" -> Icons.Default.Sensors
            "bluetooth" -> Icons.Default.Bluetooth
            "usb" -> Icons.Default.Usb
            "nfc" -> Icons.Default.Contactless
            "payment" -> Icons.Default.Payment
            "screen_share" -> Icons.AutoMirrored.Filled.ScreenShare
            "wifi" -> Icons.Default.Wifi
            "developer_board" -> Icons.Default.DeveloperBoard
            else -> Icons.Default.Security
        }
    }
}

data class PermissionDescriptor(
    val permissionType: String,
    val capabilityId: String = permissionType,
    val displayName: String = permissionType,
    val shortDescription: String = "",
    val description: String = shortDescription,
    val userPromptText: String = "",
    val iconKey: String = "security",
    val riskLevel: String = "Medium", // "Low", "Medium", "High"
    val requestHandlingMode: RequestHandlingMode = RequestHandlingMode.USER_PROMPT,
    val promptBehavior: String = if (requestHandlingMode == RequestHandlingMode.USER_PROMPT) "PROMPT" else "POLICY_CHECK",
    val requiresUserPrompt: Boolean = (requestHandlingMode == RequestHandlingMode.USER_PROMPT),
    val allowOnceAvailable: Boolean = true,
    val allowAlwaysAvailable: Boolean = true,
    val blockAvailable: Boolean = true,
    val persistenceMode: PersistenceMode = PersistenceMode.PERSISTENT,
    val persistenceBehavior: String = persistenceMode.name,
    val incognitoBehavior: String = "SESSION_ONLY",
    val androidPermissions: List<String> = emptyList(),
    val webViewResources: List<String> = emptyList(),
    val supportStatus: CapabilitySupportStatus = CapabilitySupportStatus.SUPPORTED_WITH_PERMISSION,
    val requiresNativeBridge: Boolean = (requestHandlingMode == RequestHandlingMode.NATIVE_BRIDGE_REQUIRED),
    val requiresWebViewSupport: Boolean = (supportStatus != CapabilitySupportStatus.UNSUPPORTED_BY_WEBVIEW),
    val hardwareRequirements: List<String> = emptyList(),
    val hardwareFeature: String? = hardwareRequirements.firstOrNull(),
    val requiresHardware: Boolean = hardwareRequirements.isNotEmpty() || hardwareFeature != null,
    val requiresSecureOrigin: Boolean = true,
    val secureOriginRequirement: Boolean = requiresSecureOrigin,
    val requiresUserGesture: Boolean = false,
    val userGestureRequirement: Boolean = requiresUserGesture,
    val requiresTopLevelFrame: Boolean = false,
    val topLevelFrameRequirement: Boolean = requiresTopLevelFrame,
    val requiresAndroidRuntimePermission: Boolean = androidPermissions.isNotEmpty(),
    val webApiSource: String = "WebView API"
)

object PermissionDescriptorRegistry {
    private val descriptors = mutableMapOf<String, PermissionDescriptor>()
    private val resourceToTypeMap = mutableMapOf<String, String>()

    init {
        registerInitialDescriptors()
    }

    private fun registerInitialDescriptors() {
        // 1. CAMERA
        register(
            PermissionDescriptor(
                permissionType = "CAMERA",
                capabilityId = "CAMERA",
                displayName = "Camera",
                shortDescription = "Access camera device for real-time video streaming or capturing photos",
                userPromptText = "wants to use your camera.",
                iconKey = "videocam",
                webApiSource = "WebRTC / Media Capture API (navigator.mediaDevices.getUserMedia)",
                webViewResources = listOf("android.webkit.resource.VIDEO_CAPTURE", "CAMERA"),
                androidPermissions = listOf(Manifest.permission.CAMERA),
                hardwareRequirements = listOf(PackageManager.FEATURE_CAMERA_ANY),
                requiresSecureOrigin = true,
                requiresUserGesture = true,
                requiresTopLevelFrame = true,
                riskLevel = "High",
                requestHandlingMode = RequestHandlingMode.USER_PROMPT,
                supportStatus = CapabilitySupportStatus.SUPPORTED_WITH_PERMISSION,
                requiresWebViewSupport = true,
                allowOnceAvailable = true,
                allowAlwaysAvailable = true,
                blockAvailable = true,
                persistenceMode = PersistenceMode.PERSISTENT,
                incognitoBehavior = "SESSION_ONLY"
            )
        )
        // 2. MICROPHONE
        register(
            PermissionDescriptor(
                permissionType = "MICROPHONE",
                capabilityId = "MICROPHONE",
                displayName = "Microphone",
                shortDescription = "Access microphone device for recording audio or voice communication",
                userPromptText = "wants to use your microphone.",
                iconKey = "mic",
                webApiSource = "WebRTC / Audio Capture API (navigator.mediaDevices.getUserMedia)",
                webViewResources = listOf("android.webkit.resource.AUDIO_CAPTURE", "MICROPHONE"),
                androidPermissions = listOf(Manifest.permission.RECORD_AUDIO),
                hardwareRequirements = listOf(PackageManager.FEATURE_MICROPHONE),
                requiresSecureOrigin = true,
                requiresUserGesture = true,
                requiresTopLevelFrame = true,
                riskLevel = "High",
                requestHandlingMode = RequestHandlingMode.USER_PROMPT,
                supportStatus = CapabilitySupportStatus.SUPPORTED_WITH_PERMISSION,
                requiresWebViewSupport = true,
                allowOnceAvailable = true,
                allowAlwaysAvailable = true,
                blockAvailable = true,
                persistenceMode = PersistenceMode.PERSISTENT,
                incognitoBehavior = "SESSION_ONLY"
            )
        )
        // 3. CAMERA_MICROPHONE
        register(
            PermissionDescriptor(
                permissionType = "CAMERA_MICROPHONE",
                capabilityId = "CAMERA_MICROPHONE",
                displayName = "Camera & Microphone",
                shortDescription = "Combined access to camera video capture and microphone audio recording",
                userPromptText = "wants to use your camera and microphone.",
                iconKey = "videocam",
                webApiSource = "WebRTC getUserMedia ({video: true, audio: true})",
                webViewResources = listOf("android.webkit.resource.VIDEO_CAPTURE", "android.webkit.resource.AUDIO_CAPTURE", "CAMERA_MICROPHONE"),
                androidPermissions = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                hardwareRequirements = listOf(PackageManager.FEATURE_CAMERA_ANY, PackageManager.FEATURE_MICROPHONE),
                requiresSecureOrigin = true,
                requiresUserGesture = true,
                requiresTopLevelFrame = true,
                riskLevel = "High",
                requestHandlingMode = RequestHandlingMode.USER_PROMPT,
                supportStatus = CapabilitySupportStatus.SUPPORTED_WITH_PERMISSION,
                requiresWebViewSupport = true,
                allowOnceAvailable = true,
                allowAlwaysAvailable = true,
                blockAvailable = true,
                persistenceMode = PersistenceMode.PERSISTENT,
                incognitoBehavior = "SESSION_ONLY"
            )
        )
        // 4. SPEECH_RECOGNITION
        register(
            PermissionDescriptor(
                permissionType = "SPEECH_RECOGNITION",
                capabilityId = "SPEECH_RECOGNITION",
                displayName = "Speech Recognition",
                shortDescription = "Convert voice and speech into text input via Web Speech API",
                userPromptText = "wants to recognize your speech.",
                iconKey = "mic",
                webApiSource = "Web Speech API (webkitSpeechRecognition)",
                webViewResources = listOf("SPEECH_RECOGNITION", "webkitSpeechRecognition"),
                androidPermissions = listOf(Manifest.permission.RECORD_AUDIO),
                hardwareRequirements = listOf(PackageManager.FEATURE_MICROPHONE),
                requiresSecureOrigin = true,
                requiresUserGesture = true,
                requiresTopLevelFrame = true,
                riskLevel = "High",
                requestHandlingMode = RequestHandlingMode.NATIVE_BRIDGE_REQUIRED,
                supportStatus = CapabilitySupportStatus.REQUIRES_NATIVE_BRIDGE,
                requiresNativeBridge = true,
                requiresWebViewSupport = false,
                allowOnceAvailable = true,
                allowAlwaysAvailable = true,
                blockAvailable = true,
                persistenceMode = PersistenceMode.PERSISTENT,
                incognitoBehavior = "SESSION_ONLY"
            )
        )
        // 5. LOCATION
        register(
            PermissionDescriptor(
                permissionType = "LOCATION",
                capabilityId = "LOCATION",
                displayName = "Location",
                shortDescription = "Access device geographic position using GPS and network providers",
                userPromptText = "wants to access your location.",
                iconKey = "location",
                webApiSource = "W3C Geolocation API (navigator.geolocation)",
                webViewResources = listOf("LOCATION"),
                androidPermissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                hardwareRequirements = listOf(PackageManager.FEATURE_LOCATION),
                requiresSecureOrigin = true,
                requiresUserGesture = true,
                requiresTopLevelFrame = true,
                riskLevel = "High",
                requestHandlingMode = RequestHandlingMode.USER_PROMPT,
                supportStatus = CapabilitySupportStatus.SUPPORTED_WITH_PERMISSION,
                requiresWebViewSupport = true,
                allowOnceAvailable = true,
                allowAlwaysAvailable = true,
                blockAvailable = true,
                persistenceMode = PersistenceMode.PERSISTENT,
                incognitoBehavior = "SESSION_ONLY"
            )
        )
        // 6. NOTIFICATIONS
        register(
            PermissionDescriptor(
                permissionType = "NOTIFICATIONS",
                capabilityId = "NOTIFICATIONS",
                displayName = "Notifications",
                shortDescription = "Display system notifications and alert banners to the user",
                userPromptText = "wants to send you notifications.",
                iconKey = "notifications",
                webApiSource = "Notifications API (Notification.requestPermission)",
                webViewResources = listOf("NOTIFICATIONS"),
                androidPermissions = listOf("android.permission.POST_NOTIFICATIONS"),
                hardwareRequirements = emptyList(),
                requiresSecureOrigin = true,
                requiresUserGesture = false,
                requiresTopLevelFrame = false,
                riskLevel = "Medium",
                requestHandlingMode = RequestHandlingMode.USER_PROMPT,
                supportStatus = CapabilitySupportStatus.SUPPORTED_WITH_PERMISSION,
                requiresWebViewSupport = true,
                allowOnceAvailable = false,
                allowAlwaysAvailable = true,
                blockAvailable = true,
                persistenceMode = PersistenceMode.PERSISTENT,
                incognitoBehavior = "SESSION_ONLY"
            )
        )
        // 7. MIDI
        register(
            PermissionDescriptor(
                permissionType = "MIDI",
                capabilityId = "MIDI",
                displayName = "MIDI Access",
                shortDescription = "Access external MIDI musical instruments and system exclusive messages",
                userPromptText = "wants to access MIDI devices.",
                iconKey = "music_note",
                webApiSource = "Web MIDI API (navigator.requestMIDIAccess)",
                webViewResources = listOf("android.webkit.resource.MIDI_SYSEX", "MIDI"),
                androidPermissions = emptyList(),
                hardwareRequirements = listOf(PackageManager.FEATURE_MIDI),
                requiresSecureOrigin = true,
                requiresUserGesture = false,
                requiresTopLevelFrame = false,
                riskLevel = "Low",
                requestHandlingMode = RequestHandlingMode.USER_PROMPT,
                supportStatus = CapabilitySupportStatus.SUPPORTED_WITH_PERMISSION,
                requiresWebViewSupport = true,
                allowOnceAvailable = true,
                allowAlwaysAvailable = true,
                blockAvailable = true,
                persistenceMode = PersistenceMode.PERSISTENT,
                incognitoBehavior = "SESSION_ONLY"
            )
        )
        // 8. PROTECTED_MEDIA
        register(
            PermissionDescriptor(
                permissionType = "PROTECTED_MEDIA",
                capabilityId = "PROTECTED_MEDIA",
                displayName = "Protected Content Identifier",
                shortDescription = "Access device DRM hardware credentials for encrypted media playback",
                userPromptText = "wants to play protected media content.",
                iconKey = "lock",
                webApiSource = "Encrypted Media Extensions (EME)",
                webViewResources = listOf(
                    "android.webkit.resource.PROTECTED_MEDIA_ID",
                    "android.webkit.resource.PROTECTED_MEDIA_ID_CONTAINER",
                    "PROTECTED_MEDIA"
                ),
                androidPermissions = emptyList(),
                hardwareRequirements = emptyList(),
                requiresSecureOrigin = true,
                requiresUserGesture = false,
                requiresTopLevelFrame = false,
                riskLevel = "Medium",
                requestHandlingMode = RequestHandlingMode.USER_PROMPT,
                supportStatus = CapabilitySupportStatus.SUPPORTED_WITH_PERMISSION,
                requiresWebViewSupport = true,
                allowOnceAvailable = true,
                allowAlwaysAvailable = true,
                blockAvailable = true,
                persistenceMode = PersistenceMode.PERSISTENT,
                incognitoBehavior = "SESSION_ONLY"
            )
        )
        // 9. WEBRTC
        register(
            PermissionDescriptor(
                permissionType = "WEBRTC",
                capabilityId = "WEBRTC",
                displayName = "WebRTC PeerConnection",
                shortDescription = "Establish real-time peer-to-peer audio and video communication",
                userPromptText = "wants to establish a WebRTC connection.",
                iconKey = "perm_media",
                webApiSource = "WebRTC PeerConnection / RTCPeerConnection",
                webViewResources = listOf("WEBRTC"),
                androidPermissions = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                hardwareRequirements = listOf(PackageManager.FEATURE_CAMERA_ANY, PackageManager.FEATURE_MICROPHONE),
                requiresSecureOrigin = true,
                requiresUserGesture = false,
                requiresTopLevelFrame = false,
                riskLevel = "High",
                requestHandlingMode = RequestHandlingMode.USER_PROMPT,
                supportStatus = CapabilitySupportStatus.SUPPORTED_WITH_PERMISSION,
                requiresWebViewSupport = true,
                allowOnceAvailable = true,
                allowAlwaysAvailable = true,
                blockAvailable = true,
                persistenceMode = PersistenceMode.PERSISTENT,
                incognitoBehavior = "SESSION_ONLY"
            )
        )
        // 10. MEDIA_DEVICES
        register(
            PermissionDescriptor(
                permissionType = "MEDIA_DEVICES",
                capabilityId = "MEDIA_DEVICES",
                displayName = "Media Device Enumeration",
                shortDescription = "Enumerate connected audio and video input and output hardware devices",
                userPromptText = "wants to access connected media devices.",
                iconKey = "perm_media",
                webApiSource = "navigator.mediaDevices.enumerateDevices()",
                webViewResources = listOf("MEDIA_DEVICES"),
                androidPermissions = emptyList(),
                hardwareRequirements = emptyList(),
                requiresSecureOrigin = true,
                requiresUserGesture = false,
                requiresTopLevelFrame = false,
                riskLevel = "Low",
                requestHandlingMode = RequestHandlingMode.POLICY_ONLY,
                supportStatus = CapabilitySupportStatus.SUPPORTED_WITH_PERMISSION,
                requiresWebViewSupport = true,
                allowOnceAvailable = false,
                allowAlwaysAvailable = true,
                blockAvailable = true,
                persistenceMode = PersistenceMode.PERSISTENT,
                incognitoBehavior = "SESSION_ONLY"
            )
        )
        // 11. MEDIA_RECORDER
        register(
            PermissionDescriptor(
                permissionType = "MEDIA_RECORDER",
                capabilityId = "MEDIA_RECORDER",
                displayName = "Media Stream Recording",
                shortDescription = "Record streams of media in the browser",
                userPromptText = "wants to record media.",
                iconKey = "perm_media",
                webApiSource = "MediaRecorder API",
                webViewResources = listOf("MEDIA_RECORDER"),
                androidPermissions = emptyList(),
                hardwareRequirements = emptyList(),
                requiresSecureOrigin = true,
                requiresUserGesture = false,
                requiresTopLevelFrame = false,
                riskLevel = "Low",
                requestHandlingMode = RequestHandlingMode.POLICY_ONLY,
                supportStatus = CapabilitySupportStatus.SUPPORTED,
                requiresWebViewSupport = true,
                allowOnceAvailable = false,
                allowAlwaysAvailable = true,
                blockAvailable = true,
                persistenceMode = PersistenceMode.PERSISTENT,
                incognitoBehavior = "SESSION_ONLY"
            )
        )
        // 12. FILE_UPLOAD
        register(
            PermissionDescriptor(
                permissionType = "FILE_UPLOAD",
                capabilityId = "FILE_UPLOAD",
                displayName = "File Selection",
                shortDescription = "Choose files from device storage for upload",
                userPromptText = "wants to select files.",
                iconKey = "folder",
                webApiSource = "HTML5 File Input (<input type='file'>)",
                webViewResources = listOf("FILE_UPLOAD"),
                androidPermissions = emptyList(),
                hardwareRequirements = emptyList(),
                requiresSecureOrigin = false,
                requiresUserGesture = false,
                requiresTopLevelFrame = false,
                riskLevel = "Low",
                requestHandlingMode = RequestHandlingMode.PLATFORM_MANAGED,
                supportStatus = CapabilitySupportStatus.SUPPORTED_WITH_POLICY,
                requiresWebViewSupport = true,
                allowOnceAvailable = false,
                allowAlwaysAvailable = true,
                blockAvailable = true,
                persistenceMode = PersistenceMode.PERSISTENT,
                incognitoBehavior = "SESSION_ONLY"
            )
        )
        // 13. FILE_MULTIPLE
        register(
            PermissionDescriptor(
                permissionType = "FILE_MULTIPLE",
                capabilityId = "FILE_MULTIPLE",
                displayName = "Multiple File Selection",
                shortDescription = "Select multiple files simultaneously from storage",
                userPromptText = "wants to select multiple files.",
                iconKey = "folder",
                webApiSource = "HTML5 File Input (multiple)",
                webViewResources = listOf("FILE_MULTIPLE"),
                androidPermissions = emptyList(),
                hardwareRequirements = emptyList(),
                requiresSecureOrigin = false,
                requiresUserGesture = false,
                requiresTopLevelFrame = false,
                riskLevel = "Low",
                requestHandlingMode = RequestHandlingMode.PLATFORM_MANAGED,
                supportStatus = CapabilitySupportStatus.SUPPORTED_WITH_POLICY,
                requiresWebViewSupport = true,
                allowOnceAvailable = false,
                allowAlwaysAvailable = true,
                blockAvailable = true,
                persistenceMode = PersistenceMode.PERSISTENT,
                incognitoBehavior = "SESSION_ONLY"
            )
        )
        // 14. FILE_CAMERA_CAPTURE
        register(
            PermissionDescriptor(
                permissionType = "FILE_CAMERA_CAPTURE",
                capabilityId = "FILE_CAMERA_CAPTURE",
                displayName = "Camera Capture via File Input",
                shortDescription = "Take photos directly from camera when prompted by file input",
                userPromptText = "wants to capture photo via file input.",
                iconKey = "videocam",
                webApiSource = "HTML5 File Input (capture=camera)",
                webViewResources = listOf("FILE_CAMERA_CAPTURE"),
                androidPermissions = listOf(Manifest.permission.CAMERA),
                hardwareRequirements = listOf(PackageManager.FEATURE_CAMERA_ANY),
                requiresSecureOrigin = false,
                requiresUserGesture = true,
                requiresTopLevelFrame = false,
                riskLevel = "High",
                requestHandlingMode = RequestHandlingMode.USER_PROMPT,
                supportStatus = CapabilitySupportStatus.SUPPORTED_WITH_PERMISSION,
                requiresWebViewSupport = true,
                allowOnceAvailable = true,
                allowAlwaysAvailable = true,
                blockAvailable = true,
                persistenceMode = PersistenceMode.PERSISTENT,
                incognitoBehavior = "SESSION_ONLY"
            )
        )
        // 15. FILE_AUDIO_CAPTURE
        register(
            PermissionDescriptor(
                permissionType = "FILE_AUDIO_CAPTURE",
                capabilityId = "FILE_AUDIO_CAPTURE",
                displayName = "Audio Capture via File Input",
                shortDescription = "Record audio clips directly from microphone when prompted by file input",
                userPromptText = "wants to record audio via file input.",
                iconKey = "mic",
                webApiSource = "HTML5 File Input (capture=microphone)",
                webViewResources = listOf("FILE_AUDIO_CAPTURE"),
                androidPermissions = listOf(Manifest.permission.RECORD_AUDIO),
                hardwareRequirements = listOf(PackageManager.FEATURE_MICROPHONE),
                requiresSecureOrigin = false,
                requiresUserGesture = true,
                requiresTopLevelFrame = false,
                riskLevel = "High",
                requestHandlingMode = RequestHandlingMode.USER_PROMPT,
                supportStatus = CapabilitySupportStatus.SUPPORTED_WITH_PERMISSION,
                requiresWebViewSupport = true,
                allowOnceAvailable = true,
                allowAlwaysAvailable = true,
                blockAvailable = true,
                persistenceMode = PersistenceMode.PERSISTENT,
                incognitoBehavior = "SESSION_ONLY"
            )
        )
        // 16. CLIPBOARD
        register(
            PermissionDescriptor(
                permissionType = "CLIPBOARD",
                capabilityId = "CLIPBOARD",
                displayName = "Clipboard Access",
                shortDescription = "Access system clipboard data",
                userPromptText = "wants to access your clipboard.",
                iconKey = "content_paste",
                webApiSource = "Async Clipboard API",
                webViewResources = listOf("CLIPBOARD"),
                androidPermissions = emptyList(),
                hardwareRequirements = emptyList(),
                requiresSecureOrigin = true,
                requiresUserGesture = true,
                requiresTopLevelFrame = false,
                riskLevel = "Medium",
                requestHandlingMode = RequestHandlingMode.POLICY_ONLY,
                supportStatus = CapabilitySupportStatus.SUPPORTED_WITH_PERMISSION,
                requiresWebViewSupport = true,
                allowOnceAvailable = false,
                allowAlwaysAvailable = true,
                blockAvailable = true,
                persistenceMode = PersistenceMode.PERSISTENT,
                incognitoBehavior = "SESSION_ONLY"
            )
        )
        // 17. CLIPBOARD_READ
        register(
            PermissionDescriptor(
                permissionType = "CLIPBOARD_READ",
                capabilityId = "CLIPBOARD_READ",
                displayName = "Clipboard Read",
                shortDescription = "Read text and data from system clipboard",
                userPromptText = "wants to read from your clipboard.",
                iconKey = "content_paste",
                webApiSource = "Async Clipboard API (navigator.clipboard.readText)",
                webViewResources = listOf("CLIPBOARD_READ"),
                androidPermissions = emptyList(),
                hardwareRequirements = emptyList(),
                requiresSecureOrigin = true,
                requiresUserGesture = true,
                requiresTopLevelFrame = false,
                riskLevel = "High",
                requestHandlingMode = RequestHandlingMode.USER_PROMPT,
                supportStatus = CapabilitySupportStatus.SUPPORTED_WITH_PERMISSION,
                requiresWebViewSupport = true,
                allowOnceAvailable = true,
                allowAlwaysAvailable = true,
                blockAvailable = true,
                persistenceMode = PersistenceMode.PERSISTENT,
                incognitoBehavior = "SESSION_ONLY"
            )
        )
        // 18. CLIPBOARD_WRITE
        register(
            PermissionDescriptor(
                permissionType = "CLIPBOARD_WRITE",
                capabilityId = "CLIPBOARD_WRITE",
                displayName = "Clipboard Write",
                shortDescription = "Copy text and data to system clipboard",
                userPromptText = "wants to write to your clipboard.",
                iconKey = "content_paste",
                webApiSource = "Async Clipboard API (navigator.clipboard.writeText)",
                webViewResources = listOf("CLIPBOARD_WRITE"),
                androidPermissions = emptyList(),
                hardwareRequirements = emptyList(),
                requiresSecureOrigin = true,
                requiresUserGesture = true,
                requiresTopLevelFrame = false,
                riskLevel = "Low",
                requestHandlingMode = RequestHandlingMode.POLICY_ONLY,
                supportStatus = CapabilitySupportStatus.SUPPORTED_WITH_POLICY,
                requiresWebViewSupport = true,
                allowOnceAvailable = false,
                allowAlwaysAvailable = true,
                blockAvailable = true,
                persistenceMode = PersistenceMode.PERSISTENT,
                incognitoBehavior = "SESSION_ONLY"
            )
        )
        // 19. POPUPS
        register(
            PermissionDescriptor(
                permissionType = "POPUPS",
                capabilityId = "POPUPS",
                displayName = "Pop-up Windows",
                shortDescription = "Open new windows or tabs automatically",
                userPromptText = "wants to open pop-up windows.",
                iconKey = "launch",
                webApiSource = "Window.open()",
                webViewResources = listOf("POPUPS"),
                androidPermissions = emptyList(),
                hardwareRequirements = emptyList(),
                requiresSecureOrigin = false,
                requiresUserGesture = true,
                requiresTopLevelFrame = false,
                riskLevel = "Low",
                requestHandlingMode = RequestHandlingMode.POLICY_ONLY,
                supportStatus = CapabilitySupportStatus.SUPPORTED_WITH_POLICY,
                requiresWebViewSupport = true,
                allowOnceAvailable = false,
                allowAlwaysAvailable = true,
                blockAvailable = true,
                persistenceMode = PersistenceMode.PERSISTENT,
                incognitoBehavior = "SESSION_ONLY"
            )
        )
        // 20. DOWNLOADS
        register(
            PermissionDescriptor(
                permissionType = "DOWNLOADS",
                capabilityId = "DOWNLOADS",
                displayName = "Downloads",
                shortDescription = "Download files to device storage",
                userPromptText = "wants to download files.",
                iconKey = "download",
                webApiSource = "HTML5 Download Link",
                webViewResources = listOf("DOWNLOADS"),
                androidPermissions = emptyList(),
                hardwareRequirements = emptyList(),
                requiresSecureOrigin = false,
                requiresUserGesture = false,
                requiresTopLevelFrame = false,
                riskLevel = "Low",
                requestHandlingMode = RequestHandlingMode.POLICY_ONLY,
                supportStatus = CapabilitySupportStatus.SUPPORTED_WITH_POLICY,
                requiresWebViewSupport = true,
                allowOnceAvailable = false,
                allowAlwaysAvailable = true,
                blockAvailable = true,
                persistenceMode = PersistenceMode.PERSISTENT,
                incognitoBehavior = "SESSION_ONLY"
            )
        )
        // 21. FULLSCREEN
        register(
            PermissionDescriptor(
                permissionType = "FULLSCREEN",
                capabilityId = "FULLSCREEN",
                displayName = "Fullscreen Display",
                shortDescription = "Expand web content to cover full display screen",
                userPromptText = "wants to display in fullscreen.",
                iconKey = "fullscreen",
                webApiSource = "Element.requestFullscreen()",
                webViewResources = listOf("FULLSCREEN"),
                androidPermissions = emptyList(),
                hardwareRequirements = emptyList(),
                requiresSecureOrigin = false,
                requiresUserGesture = true,
                requiresTopLevelFrame = false,
                riskLevel = "Low",
                requestHandlingMode = RequestHandlingMode.POLICY_ONLY,
                supportStatus = CapabilitySupportStatus.SUPPORTED_WITH_POLICY,
                requiresWebViewSupport = true,
                allowOnceAvailable = false,
                allowAlwaysAvailable = true,
                blockAvailable = true,
                persistenceMode = PersistenceMode.PERSISTENT,
                incognitoBehavior = "SESSION_ONLY"
            )
        )
        // 22. AUTOPLAY
        register(
            PermissionDescriptor(
                permissionType = "AUTOPLAY",
                capabilityId = "AUTOPLAY",
                displayName = "Media Autoplay Policy",
                shortDescription = "Automatically play audio or video media without interaction",
                userPromptText = "wants to automatically play media.",
                iconKey = "play_circle",
                webApiSource = "HTML5 Audio/Video Autoplay Policy",
                webViewResources = listOf("AUTOPLAY"),
                androidPermissions = emptyList(),
                hardwareRequirements = emptyList(),
                requiresSecureOrigin = false,
                requiresUserGesture = false,
                requiresTopLevelFrame = false,
                riskLevel = "Low",
                requestHandlingMode = RequestHandlingMode.POLICY_ONLY,
                supportStatus = CapabilitySupportStatus.SUPPORTED_WITH_POLICY,
                requiresWebViewSupport = true,
                allowOnceAvailable = false,
                allowAlwaysAvailable = true,
                blockAvailable = true,
                persistenceMode = PersistenceMode.PERSISTENT,
                incognitoBehavior = "SESSION_ONLY"
            )
        )
        // 23. SENSORS
        register(
            PermissionDescriptor(
                permissionType = "SENSORS",
                capabilityId = "SENSORS",
                displayName = "Device Sensors",
                shortDescription = "Access motion, accelerometer, and orientation hardware sensors",
                userPromptText = "wants to access motion and orientation sensors.",
                iconKey = "sensors",
                webApiSource = "Generic Sensor API / DeviceOrientationEvent",
                webViewResources = listOf("SENSORS"),
                androidPermissions = emptyList(),
                hardwareRequirements = listOf(PackageManager.FEATURE_SENSOR_ACCELEROMETER),
                requiresSecureOrigin = true,
                requiresUserGesture = false,
                requiresTopLevelFrame = false,
                riskLevel = "Low",
                requestHandlingMode = RequestHandlingMode.POLICY_ONLY,
                supportStatus = CapabilitySupportStatus.SUPPORTED,
                requiresWebViewSupport = true,
                allowOnceAvailable = false,
                allowAlwaysAvailable = true,
                blockAvailable = true,
                persistenceMode = PersistenceMode.PERSISTENT,
                incognitoBehavior = "SESSION_ONLY"
            )
        )
        // 24. BLUETOOTH
        register(
            PermissionDescriptor(
                permissionType = "BLUETOOTH",
                capabilityId = "BLUETOOTH",
                displayName = "Web Bluetooth",
                shortDescription = "Connect to nearby Bluetooth Low Energy devices",
                userPromptText = "wants to connect to Bluetooth devices.",
                iconKey = "bluetooth",
                webApiSource = "navigator.bluetooth",
                webViewResources = listOf("BLUETOOTH"),
                androidPermissions = emptyList(),
                hardwareRequirements = emptyList(),
                requiresSecureOrigin = true,
                requiresUserGesture = true,
                requiresTopLevelFrame = true,
                riskLevel = "High",
                requestHandlingMode = RequestHandlingMode.UNSUPPORTED,
                supportStatus = CapabilitySupportStatus.UNSUPPORTED_BY_WEBVIEW,
                requiresWebViewSupport = false,
                allowOnceAvailable = false,
                allowAlwaysAvailable = false,
                blockAvailable = true,
                persistenceMode = PersistenceMode.PERSISTENT,
                incognitoBehavior = "SESSION_ONLY"
            )
        )
        // 25. USB
        register(
            PermissionDescriptor(
                permissionType = "USB",
                capabilityId = "USB",
                displayName = "WebUSB",
                shortDescription = "Access connected USB devices directly from web app",
                userPromptText = "wants to access USB devices.",
                iconKey = "usb",
                webApiSource = "navigator.usb",
                webViewResources = listOf("USB"),
                androidPermissions = emptyList(),
                hardwareRequirements = emptyList(),
                requiresSecureOrigin = true,
                requiresUserGesture = true,
                requiresTopLevelFrame = true,
                riskLevel = "High",
                requestHandlingMode = RequestHandlingMode.UNSUPPORTED,
                supportStatus = CapabilitySupportStatus.UNSUPPORTED_BY_WEBVIEW,
                requiresWebViewSupport = false,
                allowOnceAvailable = false,
                allowAlwaysAvailable = false,
                blockAvailable = true,
                persistenceMode = PersistenceMode.PERSISTENT,
                incognitoBehavior = "SESSION_ONLY"
            )
        )
        // 26. NFC
        register(
            PermissionDescriptor(
                permissionType = "NFC",
                capabilityId = "NFC",
                displayName = "Web NFC",
                shortDescription = "Read and write Near Field Communication (NFC) tags",
                userPromptText = "wants to access NFC tags.",
                iconKey = "nfc",
                webApiSource = "NDEFReader / navigator.nfc",
                webViewResources = listOf("NFC"),
                androidPermissions = emptyList(),
                hardwareRequirements = emptyList(),
                requiresSecureOrigin = true,
                requiresUserGesture = true,
                requiresTopLevelFrame = true,
                riskLevel = "High",
                requestHandlingMode = RequestHandlingMode.UNSUPPORTED,
                supportStatus = CapabilitySupportStatus.UNSUPPORTED_BY_WEBVIEW,
                requiresWebViewSupport = false,
                allowOnceAvailable = false,
                allowAlwaysAvailable = false,
                blockAvailable = true,
                persistenceMode = PersistenceMode.PERSISTENT,
                incognitoBehavior = "SESSION_ONLY"
            )
        )
        // 27. PAYMENT
        register(
            PermissionDescriptor(
                permissionType = "PAYMENT",
                capabilityId = "PAYMENT",
                displayName = "Payment Request",
                shortDescription = "Process web payments through payment request flow",
                userPromptText = "wants to initiate a payment request.",
                iconKey = "payment",
                webApiSource = "PaymentRequest API",
                webViewResources = listOf("PAYMENT"),
                androidPermissions = emptyList(),
                hardwareRequirements = emptyList(),
                requiresSecureOrigin = true,
                requiresUserGesture = true,
                requiresTopLevelFrame = true,
                riskLevel = "High",
                requestHandlingMode = RequestHandlingMode.NATIVE_BRIDGE_REQUIRED,
                supportStatus = CapabilitySupportStatus.REQUIRES_NATIVE_BRIDGE,
                requiresNativeBridge = true,
                requiresWebViewSupport = false,
                allowOnceAvailable = true,
                allowAlwaysAvailable = true,
                blockAvailable = true,
                persistenceMode = PersistenceMode.PERSISTENT,
                incognitoBehavior = "SESSION_ONLY"
            )
        )
        // 28. SCREEN_CAPTURE
        register(
            PermissionDescriptor(
                permissionType = "SCREEN_CAPTURE",
                capabilityId = "SCREEN_CAPTURE",
                displayName = "Screen Sharing",
                shortDescription = "Capture and stream device screen display content",
                userPromptText = "wants to record or share your screen.",
                iconKey = "screen_share",
                webApiSource = "navigator.mediaDevices.getDisplayMedia()",
                webViewResources = listOf("android.webkit.resource.DISPLAY_CAPTURE", "SCREEN_CAPTURE"),
                androidPermissions = emptyList(),
                hardwareRequirements = emptyList(),
                requiresSecureOrigin = true,
                requiresUserGesture = true,
                requiresTopLevelFrame = true,
                riskLevel = "High",
                requestHandlingMode = RequestHandlingMode.NATIVE_BRIDGE_REQUIRED,
                supportStatus = CapabilitySupportStatus.REQUIRES_NATIVE_BRIDGE,
                requiresNativeBridge = true,
                requiresWebViewSupport = true,
                allowOnceAvailable = true,
                allowAlwaysAvailable = true,
                blockAvailable = true,
                persistenceMode = PersistenceMode.PERSISTENT,
                incognitoBehavior = "SESSION_ONLY"
            )
        )
        // 29. LOCAL_NETWORK
        register(
            PermissionDescriptor(
                permissionType = "LOCAL_NETWORK",
                capabilityId = "LOCAL_NETWORK",
                displayName = "Local Network Access",
                shortDescription = "Access local network hosts and private IP addresses",
                userPromptText = "wants to access local network devices.",
                iconKey = "wifi",
                webApiSource = "Private Network Access API",
                webViewResources = listOf("LOCAL_NETWORK"),
                androidPermissions = emptyList(),
                hardwareRequirements = emptyList(),
                requiresSecureOrigin = true,
                requiresUserGesture = false,
                requiresTopLevelFrame = false,
                riskLevel = "High",
                requestHandlingMode = RequestHandlingMode.UNSUPPORTED,
                supportStatus = CapabilitySupportStatus.UNSUPPORTED_BY_WEBVIEW,
                requiresWebViewSupport = false,
                allowOnceAvailable = false,
                allowAlwaysAvailable = false,
                blockAvailable = true,
                persistenceMode = PersistenceMode.PERSISTENT,
                incognitoBehavior = "SESSION_ONLY"
            )
        )
        // 30. SERIAL_HID
        register(
            PermissionDescriptor(
                permissionType = "SERIAL_HID",
                capabilityId = "SERIAL_HID",
                displayName = "Web Serial & WebHID",
                shortDescription = "Communicate with serial devices and Human Interface Devices",
                userPromptText = "wants to access Web Serial or WebHID devices.",
                iconKey = "developer_board",
                webApiSource = "navigator.serial / navigator.hid",
                webViewResources = listOf("SERIAL_HID"),
                androidPermissions = emptyList(),
                hardwareRequirements = emptyList(),
                requiresSecureOrigin = true,
                requiresUserGesture = true,
                requiresTopLevelFrame = true,
                riskLevel = "High",
                requestHandlingMode = RequestHandlingMode.UNSUPPORTED,
                supportStatus = CapabilitySupportStatus.UNSUPPORTED_BY_WEBVIEW,
                requiresWebViewSupport = false,
                allowOnceAvailable = false,
                allowAlwaysAvailable = false,
                blockAvailable = true,
                persistenceMode = PersistenceMode.PERSISTENT,
                incognitoBehavior = "SESSION_ONLY"
            )
        )
        // 31. NOTIFICATION_ACTIONS
        register(
            PermissionDescriptor(
                permissionType = "NOTIFICATION_ACTIONS",
                capabilityId = "NOTIFICATION_ACTIONS",
                displayName = "Notification Actions",
                shortDescription = "Handle notification action buttons and service worker events",
                userPromptText = "wants to handle notification action buttons.",
                iconKey = "notifications",
                webApiSource = "ServiceWorkerRegistration.showNotification()",
                webViewResources = listOf("NOTIFICATION_ACTIONS"),
                androidPermissions = emptyList(),
                hardwareRequirements = emptyList(),
                requiresSecureOrigin = true,
                requiresUserGesture = false,
                requiresTopLevelFrame = false,
                riskLevel = "Low",
                requestHandlingMode = RequestHandlingMode.POLICY_ONLY,
                supportStatus = CapabilitySupportStatus.SUPPORTED_WITH_POLICY,
                requiresWebViewSupport = true,
                allowOnceAvailable = false,
                allowAlwaysAvailable = true,
                blockAvailable = true,
                persistenceMode = PersistenceMode.PERSISTENT,
                incognitoBehavior = "SESSION_ONLY"
            )
        )
        // 32. STORAGE
        register(
            PermissionDescriptor(
                permissionType = "STORAGE",
                capabilityId = "STORAGE",
                displayName = "Web Storage",
                shortDescription = "Access client web storage including IndexedDB, LocalStorage, and OPFS",
                userPromptText = "wants to access web storage.",
                iconKey = "folder",
                webApiSource = "IndexedDB / Web Storage / OPFS",
                webViewResources = listOf("STORAGE", "STORAGE_FILESYSTEM"),
                androidPermissions = emptyList(),
                hardwareRequirements = emptyList(),
                requiresSecureOrigin = false,
                requiresUserGesture = false,
                requiresTopLevelFrame = false,
                riskLevel = "Medium",
                requestHandlingMode = RequestHandlingMode.POLICY_ONLY,
                supportStatus = CapabilitySupportStatus.SUPPORTED_WITH_POLICY,
                requiresWebViewSupport = true,
                allowOnceAvailable = false,
                allowAlwaysAvailable = true,
                blockAvailable = true,
                persistenceMode = PersistenceMode.PERSISTENT,
                incognitoBehavior = "SESSION_ONLY"
            )
        )
        // 33. BACKGROUND_MEDIA
        register(
            PermissionDescriptor(
                permissionType = "BACKGROUND_MEDIA",
                capabilityId = "BACKGROUND_MEDIA",
                displayName = "Background Media Recording",
                shortDescription = "Record media streams while app is backgrounded or inactive",
                userPromptText = "wants to record media in the background.",
                iconKey = "videocam",
                webApiSource = "Background MediaStream Recording",
                webViewResources = listOf("BACKGROUND_MEDIA"),
                androidPermissions = emptyList(),
                hardwareRequirements = emptyList(),
                requiresSecureOrigin = true,
                requiresUserGesture = false,
                requiresTopLevelFrame = false,
                riskLevel = "High",
                requestHandlingMode = RequestHandlingMode.UNSUPPORTED,
                supportStatus = CapabilitySupportStatus.UNSUPPORTED_BY_ANDROID,
                requiresWebViewSupport = false,
                allowOnceAvailable = false,
                allowAlwaysAvailable = false,
                blockAvailable = true,
                persistenceMode = PersistenceMode.PERSISTENT,
                incognitoBehavior = "SESSION_ONLY"
            )
        )
    }

    @Synchronized
    fun register(descriptor: PermissionDescriptor) {
        descriptors[descriptor.permissionType.uppercase()] = descriptor
        descriptors[descriptor.capabilityId.uppercase()] = descriptor
        descriptor.webViewResources.forEach { res ->
            if (!resourceToTypeMap.containsKey(res)) {
                resourceToTypeMap[res] = descriptor.permissionType.uppercase()
            }
        }
    }

    @Synchronized
    fun getAllDescriptors(): List<PermissionDescriptor> {
        return descriptors.values.distinctBy { it.capabilityId.uppercase() }
    }

    @Synchronized
    fun getAllCapabilityIds(): List<String> {
        return descriptors.values.map { it.capabilityId.uppercase() }.distinct()
    }

    @Synchronized
    fun getDescriptor(permissionType: String): PermissionDescriptor? {
        return descriptors[permissionType.uppercase()]
    }

    @Synchronized
    fun getDescriptorForResource(resource: String): PermissionDescriptor? {
        val type = resourceToTypeMap[resource] ?: return null
        return descriptors[type]
    }

    @Synchronized
    fun mapResourceToPermissionType(resource: String): String {
        return resourceToTypeMap[resource] ?: "UNKNOWN_RESOURCE"
    }

    @Synchronized
    fun getAndroidPermissionsForType(permissionType: String): List<String> {
        return descriptors[permissionType.uppercase()]?.androidPermissions ?: emptyList()
    }

    @Synchronized
    fun getAndroidPermissionsForResources(resources: List<String>): List<String> {
        val perms = mutableSetOf<String>()
        resources.forEach { res ->
            getDescriptorForResource(res)?.androidPermissions?.let { perms.addAll(it) }
        }
        return perms.toList()
    }

    @Synchronized
    fun resolveCapabilitySupport(capabilityIdOrResource: String): CapabilitySupportStatus {
        val descriptor = getDescriptor(capabilityIdOrResource) ?: getDescriptorForResource(capabilityIdOrResource)
        return descriptor?.supportStatus ?: CapabilitySupportStatus.UNSUPPORTED_BY_WEBVIEW
    }

    @Synchronized
    fun getDisplayNames(permissionType: String, resources: List<String> = emptyList()): List<String> {
        if (permissionType.equals("CAMERA_AND_MICROPHONE", ignoreCase = true)) {
            return listOf(
                getDescriptor("CAMERA")?.displayName ?: "Camera",
                getDescriptor("MICROPHONE")?.displayName ?: "Microphone"
            )
        }

        val types = if (resources.isNotEmpty()) {
            resources.map { mapResourceToPermissionType(it) }.distinct()
        } else {
            listOf(permissionType.uppercase())
        }

        return types.map { t ->
            getDescriptor(t)?.displayName
                ?: t.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }.distinct()
    }
}
