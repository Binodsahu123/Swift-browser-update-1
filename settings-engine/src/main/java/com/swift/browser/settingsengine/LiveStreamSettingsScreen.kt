package com.swift.browser.settingsengine

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.videoengine.live.*
import kotlinx.coroutines.launch

import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveStreamSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Load available destination profiles from registry
    val profiles = remember { LiveDestinationProfileRegistry.getAll().sortedBy { if (it.id == "custom") 1 else 0 } }
    var selectedDestinationIndex by remember { mutableStateOf(0) }
    var expandedDestination by remember { mutableStateOf(false) }

    val activeProfile = remember(selectedDestinationIndex) {
        profiles.getOrNull(selectedDestinationIndex) ?: profiles.first()
    }

    // State bindings
    var serverUrl by remember { mutableStateOf("") }
    var portString by remember { mutableStateOf("443") }
    var streamKeyString by remember { mutableStateOf("") }
    var isRtmps by remember { mutableStateOf(true) }
    var isTlsRequired by remember { mutableStateOf(true) }
    var showStreamKey by remember { mutableStateOf(false) }

    // Broadcaster States
    var videoSourceType by remember { mutableStateOf("CAMERA") }
    var audioSourceType by remember { mutableStateOf("MICROPHONE") }
    val engineState by LiveStreamingEngine.engineState.collectAsState()
    var stats by remember { mutableStateOf<LiveStreamStats?>(null) }
    var startTimeMs by remember { mutableStateOf(0L) }

    // Validation Status
    var destinationStatus by remember { mutableStateOf(DestinationStatus.NOT_READY) }
    var statusMessage by remember { mutableStateOf("Please configure stream settings.") }
    var encodingPlan by remember { mutableStateOf<LiveEncodingPlan?>(null) }

    // Auto update fields when changing preset destination
    val onDestinationChanged: (Int) -> Unit = { index ->
        selectedDestinationIndex = index
        val profile = profiles.getOrNull(index) ?: profiles.first()
        if (profile.id != "custom") {
            serverUrl = profile.defaultServer
            if (profile.defaultApplication.isNotEmpty()) {
                serverUrl += "/" + profile.defaultApplication
            }
            portString = if (profile.streamingProtocol == StreamingProtocol.RTMPS || profile.requiresTls) "443" else "1935"
            isRtmps = profile.streamingProtocol == StreamingProtocol.RTMPS
            isTlsRequired = profile.requiresTls
        } else {
            serverUrl = ""
            portString = "1935"
            isRtmps = false
            isTlsRequired = false
        }
    }

    val mediaProjectionManager = remember {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    fun runStreamingSession(mediaProjection: android.media.projection.MediaProjection? = null) {
        val portInt = portString.toIntOrNull() ?: 1935
        val endpoint = LiveStreamEndpoint(
            serverUrl = serverUrl,
            port = portInt,
            protocol = if (isRtmps) StreamProtocol.RTMPS else StreamProtocol.RTMP,
            tlsRequired = isTlsRequired
        )
        val fullUrl = endpoint.buildFullUrl("")
        val config = LiveStreamConfig(
            streamUrl = fullUrl,
            streamKey = streamKeyString,
            width = 1280,
            height = 720,
            fps = 30
        )
        try {
            LiveStreamingEngine.startStream(
                context = context,
                config = config,
                videoSourceType = videoSourceType,
                audioSourceType = audioSourceType,
                mediaProjection = mediaProjection
            )
            Toast.makeText(context, "Starting Live Stream...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("LiveStreamSettings", "Start stream failed", e)
            Toast.makeText(context, "Failed to initialize stream: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val mediaProjection = mediaProjectionManager.getMediaProjection(result.resultCode, result.data!!)
            if (mediaProjection != null) {
                startTimeMs = System.currentTimeMillis()
                runStreamingSession(mediaProjection)
            } else {
                Toast.makeText(context, "Failed to get MediaProjection session", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Screen capture consent denied", Toast.LENGTH_SHORT).show()
        }
    }

    fun startBroadcast() {
        if (destinationStatus != DestinationStatus.READY && destinationStatus != DestinationStatus.DEVICE_LIMITED) {
            Toast.makeText(context, "Validation check not passed: $statusMessage", Toast.LENGTH_LONG).show()
            return
        }

        val portInt = portString.toIntOrNull() ?: 1935
        val endpoint = LiveStreamEndpoint(
            serverUrl = serverUrl,
            port = portInt,
            protocol = if (isRtmps) StreamProtocol.RTMPS else StreamProtocol.RTMP,
            tlsRequired = isTlsRequired
        )
        try {
            LiveStreamCredentialStore.saveCredentials(
                context = context,
                endpoint = endpoint,
                streamKey = streamKeyString.toCharArray()
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to securely save credentials: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        val permissionsNeeded = mutableListOf<String>()
        if (videoSourceType == "CAMERA") {
            permissionsNeeded.add(android.Manifest.permission.CAMERA)
        }
        if (audioSourceType == "MICROPHONE") {
            permissionsNeeded.add(android.Manifest.permission.RECORD_AUDIO)
        }

        if (permissionsNeeded.isNotEmpty()) {
            com.swift.browser.permissionengine.AndroidRuntimePermissionManager.requestAndroidPermissions(
                context = context,
                requestId = "orion_live_stream_auth",
                permissions = permissionsNeeded
            ) { result ->
                if (result.granted) {
                    if (videoSourceType == "SCREEN") {
                        screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                    } else {
                        startTimeMs = System.currentTimeMillis()
                        runStreamingSession()
                    }
                } else {
                    Toast.makeText(context, "Required Camera/Microphone permissions were not granted", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            if (videoSourceType == "SCREEN") {
                screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
            } else {
                startTimeMs = System.currentTimeMillis()
                runStreamingSession()
            }
        }
    }

    LaunchedEffect(engineState) {
        if (engineState == LiveStreamState.STREAMING) {
            while (true) {
                stats = LiveStreamingEngine.getStreamStats()
                kotlinx.coroutines.delay(1000)
            }
        } else {
            stats = null
            startTimeMs = 0L
        }
    }

    // Diagnostics states
    var isTestingTls by remember { mutableStateOf(false) }
    var tlsTestResult by remember { mutableStateOf<String?>(null) }
    var isTestingCertRejection by remember { mutableStateOf(false) }
    var certRejectionResult by remember { mutableStateOf<String?>(null) }
    var isTestingHostnameRejection by remember { mutableStateOf(false) }
    var hostnameRejectionResult by remember { mutableStateOf<String?>(null) }

    // Load existing secure credentials from KeyStore
    LaunchedEffect(Unit) {
        val creds = LiveStreamCredentialStore.getCredentials(context)
        if (creds != null) {
            val endpoint = creds.first
            val key = creds.second
            serverUrl = endpoint.serverUrl
            portString = endpoint.port.toString()
            isRtmps = endpoint.protocol == StreamProtocol.RTMPS
            isTlsRequired = endpoint.tlsRequired

            if (key != null) {
                streamKeyString = key.concatToString()
            }

            // Determine destination matching preset
            val matchIdx = profiles.indexOfFirst { it.defaultServer == endpoint.serverUrl }
            selectedDestinationIndex = if (matchIdx != -1) matchIdx else profiles.indexOfFirst { it.id == "custom" }
        } else {
            // Apply default first preset
            onDestinationChanged(0)
        }
    }

    // Auto-update live validation
    LaunchedEffect(selectedDestinationIndex, serverUrl, portString, streamKeyString, isRtmps, isTlsRequired) {
        val portInt = portString.toIntOrNull() ?: 1935
        
        // Sanitize Host & Application
        var hostPart = serverUrl.trim()
        if (hostPart.startsWith("rtmp://", ignoreCase = true)) {
            hostPart = hostPart.substring(7)
        } else if (hostPart.startsWith("rtmps://", ignoreCase = true)) {
            hostPart = hostPart.substring(8)
        }
        val appPart = hostPart.substringAfter("/", "live")
        hostPart = hostPart.substringBefore("/").substringBefore(":")

        val baseDest = activeProfile.toLiveDestination()
        val dest = baseDest.copy(
            protocolType = if (isRtmps) LiveTransportProtocol.RTMPS else LiveTransportProtocol.RTMP,
            host = hostPart,
            port = portInt,
            application = appPart,
            tlsRequired = isTlsRequired
        )

        val credentials = LiveStreamCredentials(streamKeyString)
        val result = LiveDestinationValidator.validate(dest, credentials, 1280, 720, 30, 2500_000)

        val plan = LiveEncodingPlanGenerator.generatePlan(dest, 1280, 720, 30, 2500_000)
        encodingPlan = plan

        if (result is ValidationResult.Error) {
            destinationStatus = when (result.code) {
                "MISSING_STREAM_KEY" -> DestinationStatus.CREDENTIALS_REQUIRED
                "TLS_REQUIRED", "TLS_MISMATCH" -> DestinationStatus.TLS_REQUIRED
                "UNSUPPORTED_PROTOCOL" -> DestinationStatus.UNSUPPORTED_PROTOCOL
                else -> DestinationStatus.DESTINATION_INVALID
            }
            statusMessage = result.message
        } else {
            if (plan.reasonForDowngrade.startsWith("LIMITED")) {
                destinationStatus = DestinationStatus.DEVICE_LIMITED
                statusMessage = plan.reasonForDowngrade
            } else {
                destinationStatus = DestinationStatus.READY
                statusMessage = "Destination validated successfully."
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Secure Streaming Ingest", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = {
                        LiveStreamCredentialStore.wipeMemory()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LiveTv,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Column {
                    Text(
                        text = "Broadcast Server Settings",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Configure generic live stream destinations securely",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Status Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when (destinationStatus) {
                        DestinationStatus.READY -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        DestinationStatus.DEVICE_LIMITED -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (destinationStatus) {
                            DestinationStatus.READY -> Icons.Default.CheckCircle
                            DestinationStatus.DEVICE_LIMITED -> Icons.Default.Info
                            else -> Icons.Default.Warning
                        },
                        contentDescription = null,
                        tint = when (destinationStatus) {
                            DestinationStatus.READY -> MaterialTheme.colorScheme.primary
                            DestinationStatus.DEVICE_LIMITED -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                    Column {
                        Text(
                            text = "Status: ${destinationStatus.name}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = statusMessage,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Card 1: Server Configurations
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Endpoint Ingest Protocol",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Dynamic Profiles Dropdown Selector
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expandedDestination = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(activeProfile.displayName)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }

                        DropdownMenu(
                            expanded = expandedDestination,
                            onDismissRequest = { expandedDestination = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            profiles.forEachIndexed { idx, item ->
                                DropdownMenuItem(
                                    text = { Text(item.displayName) },
                                    onClick = {
                                        onDestinationChanged(idx)
                                        expandedDestination = false
                                    }
                                )
                            }
                        }
                    }

                    // Server URL OutlinedTextField
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { if (activeProfile.id == "custom") serverUrl = it },
                        label = { Text("Server URL or Host") },
                        placeholder = { Text("e.g. live.twitch.tv/app") },
                        enabled = activeProfile.id == "custom",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Row of Port and Secure TLS protocol
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = portString,
                            onValueChange = { if (activeProfile.id == "custom") portString = it },
                            label = { Text("Port") },
                            enabled = activeProfile.id == "custom",
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1.5f)
                                .align(Alignment.CenterVertically)
                        ) {
                            Text("Use RTMPS (Secure)", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Switch(
                                checked = isRtmps,
                                onCheckedChange = {
                                    if (activeProfile.id == "custom") {
                                        isRtmps = it
                                        isTlsRequired = it
                                        portString = if (it) "443" else "1935"
                                    }
                                },
                                enabled = activeProfile.id == "custom"
                            )
                        }
                    }

                    // Display recommendations
                    if (activeProfile.id != "custom") {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = "Recommended Settings:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "• Resolution: ${activeProfile.recommendedWidth}x${activeProfile.recommendedHeight}",
                                fontSize = 12.sp
                            )
                            Text(
                                text = "• Frame Rate: ${activeProfile.recommendedFps} fps",
                                fontSize = 12.sp
                            )
                            Text(
                                text = "• Video Bitrate: ${activeProfile.recommendedBitrate / 1000} kbps",
                                fontSize = 12.sp
                            )
                            Text(
                                text = "• Audio Bitrate: ${activeProfile.audioRequirements.recommendedBitrate / 1000} kbps",
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Card 2: Confidential Credentials (Stream Key)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Ingest Stream Key",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = streamKeyString,
                        onValueChange = { streamKeyString = it },
                        label = { Text("Stream Key") },
                        visualTransformation = if (showStreamKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showStreamKey = !showStreamKey }) {
                                Icon(
                                    imageVector = if (showStreamKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle stream key visibility"
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "This stream key is stored encrypted within the hardware Keystore and wiped immediately from active runtime memory upon ending streaming sessions.",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Card 3: Live Diagnostics and Security Handshakes
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "TLS Handshake & Security Diagnostics",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isTestingTls = true
                                tlsTestResult = "Initiating TLS handshakes..."
                                var hostToTest = serverUrl.trim()
                                if (hostToTest.startsWith("rtmp://", ignoreCase = true)) hostToTest = hostToTest.substring(7)
                                if (hostToTest.startsWith("rtmps://", ignoreCase = true)) hostToTest = hostToTest.substring(8)
                                hostToTest = hostToTest.substringBefore("/")
                                val portInt = portString.toIntOrNull() ?: 443
                                val result = StreamEndpointTester.testTlsHandshake(hostToTest, portInt)
                                isTestingTls = false
                                tlsTestResult = when (result) {
                                    is TestResult.Success -> "✅ Pass: ${result.message}"
                                    is TestResult.Failure -> "❌ Failed: ${result.message}"
                                }

                                isTestingCertRejection = true
                                certRejectionResult = "Testing cert rejection..."
                                val certResult = StreamEndpointTester.testInvalidCertRejection()
                                isTestingCertRejection = false
                                certRejectionResult = when (certResult) {
                                    is TestResult.Success -> "✅ Pass: ${certResult.message}"
                                    is TestResult.Failure -> "⚠️ Error: ${certResult.message}"
                                }

                                isTestingHostnameRejection = true
                                hostnameRejectionResult = "Testing hostname validation..."
                                val hostResult = StreamEndpointTester.testWrongHostnameRejection()
                                isTestingHostnameRejection = false
                                hostnameRejectionResult = when (hostResult) {
                                    is TestResult.Success -> "✅ Pass: ${hostResult.message}"
                                    is TestResult.Failure -> "⚠️ Error: ${hostResult.message}"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Perform Handshake & Security Audit")
                    }

                    if (tlsTestResult != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("1. Handshake to Target", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(tlsTestResult!!, fontSize = 12.sp)
                            
                            certRejectionResult?.let {
                                Text("2. Invalid Certificate Rejection", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(it, fontSize = 12.sp)
                            }

                            hostnameRejectionResult?.let {
                                Text("3. Hostname Verification Rejection", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(it, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Card 4: Stream Controls & Status
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Native Stream Broadcaster",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Video Source", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = videoSourceType == "CAMERA",
                                    onClick = { if (engineState == LiveStreamState.IDLE) videoSourceType = "CAMERA" },
                                    enabled = engineState == LiveStreamState.IDLE
                                )
                                Text("Camera", fontSize = 13.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = videoSourceType == "SCREEN",
                                    onClick = { if (engineState == LiveStreamState.IDLE) videoSourceType = "SCREEN" },
                                    enabled = engineState == LiveStreamState.IDLE
                                )
                                Text("Screen Share", fontSize = 13.sp)
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Audio Source", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = audioSourceType == "MICROPHONE",
                                    onClick = { if (engineState == LiveStreamState.IDLE) audioSourceType = "MICROPHONE" },
                                    enabled = engineState == LiveStreamState.IDLE
                                )
                                Text("Mic On", fontSize = 13.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = audioSourceType == "NO_AUDIO",
                                    onClick = { if (engineState == LiveStreamState.IDLE) audioSourceType = "NO_AUDIO" },
                                    enabled = engineState == LiveStreamState.IDLE
                                )
                                Text("Muted", fontSize = 13.sp)
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Status:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(
                            text = engineState.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = when (engineState) {
                                LiveStreamState.STREAMING -> MaterialTheme.colorScheme.primary
                                LiveStreamState.RECONNECTING -> MaterialTheme.colorScheme.error
                                LiveStreamState.FAILED -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    stats?.let { s ->
                        val duration = if (startTimeMs > 0L) System.currentTimeMillis() - startTimeMs else 0L
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Duration: ${formatDuration(duration)}", fontSize = 12.sp)
                                Text("Video Frame Rate: ${s.fps} fps", fontSize = 12.sp)
                                Text("Active Bitrate: ${s.bitrateKbps} kbps", fontSize = 12.sp)
                                Text("Data Sent: ${s.totalBytesSent / 1024L} KB (Dropped: ${s.droppedFrames})", fontSize = 12.sp)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (engineState == LiveStreamState.IDLE || engineState == LiveStreamState.STOPPED || engineState == LiveStreamState.FAILED) {
                            Button(
                                onClick = { startBroadcast() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Start Broadcast")
                            }
                        } else {
                            Button(
                                onClick = { LiveStreamingEngine.stopStream() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Stop Broadcast")
                            }
                        }
                    }
                }
            }

            // Save and Clear action bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        LiveStreamCredentialStore.clearCredentials(context)
                        streamKeyString = ""
                        Toast.makeText(context, "Credentials cleared and wiped securely.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear")
                }

                Button(
                    onClick = {
                        val portInt = portString.toIntOrNull() ?: 1935
                        val endpoint = LiveStreamEndpoint(
                            serverUrl = serverUrl,
                            port = portInt,
                            protocol = if (isRtmps) StreamProtocol.RTMPS else StreamProtocol.RTMP,
                            tlsRequired = isTlsRequired
                        )
                        LiveStreamCredentialStore.saveCredentials(
                            context = context,
                            endpoint = endpoint,
                            streamKey = streamKeyString.toCharArray()
                        )
                        Toast.makeText(context, "Streaming Credentials Securely Saved!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Ingest")
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val sec = (ms / 1000) % 60
    val min = (ms / (1000 * 60)) % 60
    val hr = (ms / (1000 * 60 * 60))
    return if (hr > 0) {
        String.format("%d:%02d:%02d", hr, min, sec)
    } else {
        String.format("%02d:%02d", min, sec)
    }
}
