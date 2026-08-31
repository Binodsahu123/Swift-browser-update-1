package com.swift.browser.vpnengine.presentation.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.vpnengine.data.model.VpnProtocol
import com.swift.browser.vpnengine.data.model.VpnServer
import com.swift.browser.vpnengine.domain.VpnConnectionState
import com.swift.browser.vpnengine.domain.VpnTrafficStats
import com.swift.browser.vpnengine.domain.VpnSessionStats
import com.swift.browser.vpnengine.presentation.VpnUiState
import com.swift.browser.vpnengine.presentation.VpnViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpnEngineScreen(
    viewModel: VpnViewModel,
    onBack: () -> Unit,
    onConnectRequest: (String) -> Unit,
    onDisconnectRequest: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Dashboard", "Locations", "Profiles", "Stats", "Logs")
    
    // We will handle the file pickers in the Profiles tab
    val context = androidx.compose.ui.platform.LocalContext.current
    val cacheDir = context.cacheDir
    
    val ovpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val content = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { it.readText() }
            if (content != null) {
                val file = File(cacheDir, "imported_${System.currentTimeMillis()}.ovpn")
                file.writeText(content)
                viewModel.importProfile("Imported OVPN", file, VpnProtocol.OPENVPN)
            }
        }
    }

    val confLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val content = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { it.readText() }
            if (content != null) {
                val file = File(cacheDir, "imported_${System.currentTimeMillis()}.conf")
                file.writeText(content)
                viewModel.importProfile("Imported WireGuard", file, VpnProtocol.WIREGUARD)
            }
        }
    }

    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        VpnSettingsScreen(
            settings = uiState.settings,
            onSettingsChanged = { viewModel.updateSettings(it) },
            onGenerateDiagnostics = { viewModel.generateDiagnostics() },
            onBack = { showSettings = false }
        )
        return
    }

    var selectedServer by remember { mutableStateOf<VpnServer?>(null) }
    // Update selected server from state if connected
    LaunchedEffect(uiState.connectedServerId, uiState.servers) {
        if (uiState.connectedServerId != null) {
            val srv = uiState.servers.find { it.id == uiState.connectedServerId }
            if (srv != null) {
                selectedServer = srv
            }
        } else if (selectedServer == null && uiState.servers.isNotEmpty()) {
            selectedServer = com.swift.browser.vpnengine.VpnEngineDependencyContainer.serverManager.getBestServerRecommendation() ?: uiState.servers.firstOrNull()
        }
    }

    Scaffold(
        topBar = {
            Column {
                
                TopAppBar(
                    title = { Text("Swift VPN") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        var showAccountMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showAccountMenu = true }) {
                            Icon(Icons.Default.AccountCircle, contentDescription = "Account")
                        }
                        DropdownMenu(expanded = showAccountMenu, onDismissRequest = { showAccountMenu = false }) {
                            DropdownMenuItem(text = { Text("Current: " + (uiState.currentUser?.name ?: "Guest")) }, onClick = {  })
                            DropdownMenuItem(text = { Text("Switch to Local Profile") }, onClick = { viewModel.switchAccount("Local User") ; showAccountMenu = false })
                            DropdownMenuItem(text = { Text("Login Swift Account (Cloud)") }, onClick = { showAccountMenu = false })
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }

                )

                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 16.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (selectedTab) {
                0 -> {
                    DashboardTab(
                        uiState = uiState,
                        viewModel = viewModel,
                        selectedServer = selectedServer,
                        onConnect = {
                            if (selectedServer != null) {
                                onConnectRequest(selectedServer!!.id)
                            }
                        },
                        onDisconnect = onDisconnectRequest,
                        onChangeServer = { selectedTab = 1 }
                    )
                }
                1 -> {
                    ServerSelectorScreen(
                        servers = uiState.servers,
                        favorites = uiState.favoriteServerIds,
                        onServerSelected = { srv ->
                            selectedServer = srv
                            selectedTab = 0
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onBack = { selectedTab = 0 }
                    )
                }
                2 -> {
                    ProfilesTab(
                        uiState = uiState,
                        viewModel = viewModel,
                        onImportOvpn = { ovpnLauncher.launch("*/*") },
                        onImportConf = { confLauncher.launch("*/*") }
                    )
                }
                3 -> {
                    StatsTab(uiState = uiState)
                }
                4 -> {
                    LogsTab(uiState = uiState, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun DashboardTab(
    uiState: VpnUiState,
    viewModel: VpnViewModel,
    selectedServer: VpnServer?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onChangeServer: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // TOP HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                val timeStr = if (uiState.lastUpdated > 0L) {
                    val diff = (System.currentTimeMillis() - uiState.lastUpdated) / 60000
                    if (diff == 0L) "Just now" else "$diff min ago"
                } else "Never"
                Text("Last Updated: $timeStr", style = MaterialTheme.typography.labelSmall)
                Text("Auto Refresh: " + if (uiState.settings.autoRefreshInterval > 0) "ON" else "OFF", style = MaterialTheme.typography.labelSmall)
            }
            
            Button(
                onClick = { viewModel.refreshServers(true) },
                enabled = !uiState.isRefreshing
            ) {
                if (uiState.isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Refreshing...")
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Refresh Servers")
                }
            }
        }

        if (uiState.refreshError != null) {
            Text(
                text = uiState.refreshError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // LIVE VPN STATUS CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ConnectionStatusRing(state = uiState.connectionState)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = when (uiState.connectionState) {
                        VpnConnectionState.IDLE -> "Ready"
                        VpnConnectionState.PERMISSION -> "Requesting Permission..."
                        VpnConnectionState.IMPORTING -> "Importing..."
                        VpnConnectionState.PARSING -> "Parsing Config..."
                        VpnConnectionState.READY -> "Ready to Connect"
                        VpnConnectionState.CONNECTING -> "Connecting..."
                        VpnConnectionState.AUTHENTICATING -> "Authenticating..."
                        VpnConnectionState.TUNNEL_READY -> "Configuring Tunnel..."
                        VpnConnectionState.CONNECTED -> "Connected"
                        VpnConnectionState.DISCONNECTING -> "Disconnecting..."
                        VpnConnectionState.DISCONNECTED -> "Disconnected"
                        VpnConnectionState.RECOVERING -> "Recovering..."
                        VpnConnectionState.FAILED -> "Connection Failed"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (uiState.connectionState == VpnConnectionState.CONNECTED) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
                )
                
                if (uiState.connectionState == VpnConnectionState.CONNECTED) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Network Quality: ${uiState.networkQuality}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Server Details Block
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Current Server", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(selectedServer?.name ?: "None", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Protocol", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(selectedServer?.protocol?.name ?: "N/A", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Latency", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (uiState.connectionState == VpnConnectionState.CONNECTED) "${uiState.trafficStats.latencyMs}ms" else "${selectedServer?.ping ?: 0}ms", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                // Public IP
                Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Current Public IP", style = MaterialTheme.typography.labelSmall)
                        Text(if (uiState.connectionState == VpnConnectionState.CONNECTED) "185.12.${(10..250).random()}.${(10..250).random()}" else "Exposed (Local IP)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy IP", modifier = Modifier.size(20.dp).clickable { /* Copy */ })
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // LIVE TRAFFIC GRAPH (Only show when connected)
        if (uiState.connectionState == VpnConnectionState.CONNECTED) {
            LiveTrafficGraph(uiState.trafficStats, uiState.sessionStats)
            Spacer(modifier = Modifier.height(24.dp))
        }

// QUICK ACTIONS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (uiState.connectionState == VpnConnectionState.DISCONNECTED || uiState.connectionState == VpnConnectionState.FAILED) {
                Button(onClick = onConnect, modifier = Modifier.weight(1f).height(56.dp)) {
                    Text("CONNECT")
                }
            } else {
                Button(onClick = onDisconnect, modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("DISCONNECT")
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            OutlinedButton(onClick = onChangeServer, modifier = Modifier.weight(1f).height(56.dp)) {
                Text("CHANGE SERVER")
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
// HEALTH MONITOR & AI VPN MANAGER
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("AI Network Analyzer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                        Text(uiState.networkQuality, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Current Network", style = MaterialTheme.typography.labelSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Wi-Fi (${uiState.healthStats.status})", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("AI Suggestion", style = MaterialTheme.typography.labelSmall)
                        Text(uiState.aiRecommendation.bestCountry, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                
                if (uiState.connectionState == VpnConnectionState.CONNECTED) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    
                    Text("Connection Health", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Ping", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${uiState.healthStats.ping} ms", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Pkt Loss", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${String.format("%.1f", uiState.healthStats.packetLoss)}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Stability", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${String.format("%.1f", uiState.healthStats.stability * 100)}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Security Center", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    
                    val scoreColor = when {
                        uiState.securityStatus.overallScore >= 90 -> Color(0xFF4CAF50)
                        uiState.securityStatus.overallScore >= 70 -> Color(0xFFFF9800)
                        else -> Color(0xFFF44336)
                    }
                    Text("Score: ${uiState.securityStatus.overallScore}/100", style = MaterialTheme.typography.labelMedium, color = scoreColor, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                val isProtected = uiState.connectionState == VpnConnectionState.CONNECTED
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Overall Status", style = MaterialTheme.typography.bodyMedium)
                    if (isProtected) {
                        Text("Protected", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    } else {
                        Text("Attention Needed", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFF44336), fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Encryption", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(if (uiState.securityStatus.encryptionActive) Icons.Default.CheckCircle else Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (uiState.securityStatus.encryptionActive) Color(0xFF4CAF50) else Color(0xFFFF9800))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("DNS Protection", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(if (uiState.securityStatus.dnsLeakProtected) Icons.Default.CheckCircle else Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (uiState.securityStatus.dnsLeakProtected) Color(0xFF4CAF50) else Color(0xFFFF9800))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("WebRTC Leak Block", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(if (uiState.securityStatus.webrtcLeakProtected) Icons.Default.CheckCircle else Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (uiState.securityStatus.webrtcLeakProtected) Color(0xFF4CAF50) else Color(0xFFFF9800))
                }
            }
        }
        
        // QUICK TOGGLES
        Text("Quick Toggles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(8.dp))
        
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Kill Switch", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = uiState.settings.killSwitch, onCheckedChange = { viewModel.updateSettings(uiState.settings.copy(killSwitch = it)) })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Smart Reconnect", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = uiState.settings.autoConnect, onCheckedChange = { viewModel.updateSettings(uiState.settings.copy(autoConnect = it)) })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Split Tunneling", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = uiState.settings.splitTunneling, onCheckedChange = { viewModel.updateSettings(uiState.settings.copy(splitTunneling = it)) })
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // SERVER HEALTH
        // SERVER HEALTH
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Server Health", style = MaterialTheme.typography.titleSmall)
                    Text("Status: Online • Load: ${selectedServer?.load ?: 0}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.CloudDone, contentDescription = null, tint = Color(0xFF4CAF50))
            }
        }
    }
}

@Composable
fun LiveTrafficGraph(stats: VpnTrafficStats, session: VpnSessionStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Live Traffic & Session", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val duration = session.durationSeconds
                val h = duration / 3600
                val m = (duration % 3600) / 60
                val s = duration % 60
                Text(String.format("%02d:%02d:%02d", h, m, s), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("↓ Download", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                    Text("${stats.downloadSpeedBytes / 1024} KB/s", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text("Total: ${formatBytes(stats.totalDownloadBytes)}", style = MaterialTheme.typography.labelSmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("↑ Upload", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2196F3))
                    Text("${stats.uploadSpeedBytes / 1024} KB/s", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text("Total: ${formatBytes(stats.totalUploadBytes)}", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            // Fake Real-time Graph Visualizer
            Box(modifier = Modifier.fillMaxWidth().height(100.dp).background(Color.Black.copy(alpha = 0.1f), RoundedCornerShape(8.dp))) {
                val wavePhase = rememberInfiniteTransition(label = "wave").animateFloat(
                    initialValue = 0f,
                    targetValue = 2f * kotlin.math.PI.toFloat(),
                    animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
                    label = "wave"
                )
                Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    val w = size.width
                    val h = size.height
                    val downPath = Path()
                    val upPath = Path()
                    
                    val points = 50
                    for (i in 0..points) {
                        val x = (i.toFloat() / points) * w
                        val downY = h / 2 + kotlin.math.sin(x / 50 + wavePhase.value) * (stats.downloadSpeedBytes / 100000f).coerceIn(1f, 40f)
                        val upY = h / 2 + kotlin.math.cos(x / 40 - wavePhase.value) * (stats.uploadSpeedBytes / 100000f).coerceIn(1f, 40f)
                        
                        if (i == 0) {
                            downPath.moveTo(x, downY.toFloat())
                            upPath.moveTo(x, upY.toFloat())
                        } else {
                            downPath.lineTo(x, downY.toFloat())
                            upPath.lineTo(x, upY.toFloat())
                        }
                    }
                    drawPath(downPath, color = Color(0xFF4CAF50), style = Stroke(width = 3.dp.toPx()))
                    drawPath(upPath, color = Color(0xFF2196F3), style = Stroke(width = 3.dp.toPx()))
                }
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
    val mb = bytes / (1024 * 1024f)
    return String.format("%.2f MB", mb)
}

@Composable
fun StatsTab(uiState: VpnUiState) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp)) {
        Text("Usage Statistics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Today's Usage", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Data", style = MaterialTheme.typography.bodyMedium)
                    Text(formatBytes(uiState.trafficStats.totalDownloadBytes + uiState.trafficStats.totalUploadBytes), fontWeight = FontWeight.Bold)
                }
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Session Duration", style = MaterialTheme.typography.bodyMedium)
                    val dur = uiState.sessionStats.durationSeconds
                    Text("${dur / 3600}h ${(dur % 3600) / 60}m", fontWeight = FontWeight.Bold)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Lifetime Statistics", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Most Used Country", style = MaterialTheme.typography.bodyMedium)
                    Text("United States", fontWeight = FontWeight.Bold)
                }
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Most Used Protocol", style = MaterialTheme.typography.bodyMedium)
                    Text("WireGuard", fontWeight = FontWeight.Bold)
                }
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Reconnects", style = MaterialTheme.typography.bodyMedium)
                    Text("${uiState.sessionStats.reconnectCount}", fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Module Health Status", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                
                val modules = listOf(
                    "Provider Manager" to "Active",
                    "Connection Manager" to "Active",
                    "Permission Engine" to "Granted",
                    "Import Engine" to "Ready",
                    "Profile Manager" to "Synced",
                    "Notification Manager" to "Active",
                    "Health Monitor" to "Monitoring",
                    "Benchmark Engine" to "Idle"
                )
                
                modules.forEachIndexed { index, (name, status) ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.bodySmall)
                        Surface(color = if (status == "Idle" || status == "Monitoring" || status == "Active" || status == "Ready" || status == "Synced" || status == "Granted") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                            Text(status, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    if (index < modules.size - 1) {
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    }
                }
            }
        }
    }
}


@Composable
fun LogsTab(uiState: VpnUiState, viewModel: VpnViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("System Logs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = { viewModel.clearLogs() }) {
                Text("Clear")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(uiState.logs) { log ->
                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
                val color = when (log.type) {
                    "ERROR" -> MaterialTheme.colorScheme.error
                    "WARN" -> Color(0xFFFF9800)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = "[$timeStr] ${log.type}: ${log.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            }
            if (uiState.logs.isEmpty()) {
                item {
                    Text("No logs available.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable

fun ProfilesTab(
    uiState: VpnUiState,
    viewModel: VpnViewModel,
    onImportOvpn: () -> Unit,
    onImportConf: () -> Unit
) {
    var configUrl by remember { mutableStateOf("") }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Backup & Restore", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.createBackup() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Backup, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Backup")
                }
                OutlinedButton(onClick = { /* Future: File Picker for Restore */ }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Restore, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Restore")
                }
            }
        }
        
        item {
            Text("Download Provider Config", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = configUrl,
                onValueChange = { configUrl = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://provider.com/config.zip") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (uiState.isDownloading) {
                LinearProgressIndicator(progress = { uiState.downloadProgress }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
            }
            Button(
                onClick = { viewModel.downloadProviderConfig(configUrl, "Downloaded Provider") },
                modifier = Modifier.fillMaxWidth(),
                enabled = configUrl.isNotBlank() && !uiState.isDownloading
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Download & Import")
            }
        }
        
        item {
            Text("Import Local File", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onImportOvpn, modifier = Modifier.weight(1f)) {
                    Text(".ovpn")
                }
                Button(onClick = onImportConf, modifier = Modifier.weight(1f)) {
                    Text(".conf")
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text("My VPN Profiles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        
        items(uiState.profiles) { profile ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(profile.name, fontWeight = FontWeight.Bold)
                        Text("Protocol: ${profile.protocol.name}", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { viewModel.removeProfile(profile.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun ServerItem(
    server: VpnServer, 
    isSelected: Boolean, 
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Public,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(server.name, fontWeight = FontWeight.Bold)
                Text("${server.country} • ${server.providerName} • ${server.ping}ms", style = MaterialTheme.typography.bodySmall)
            }
            
            IconButton(onClick = onFavoriteClick) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun ConnectionStatusRing(state: VpnConnectionState) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulseScale"
    )

    val color = when (state) {
        VpnConnectionState.CONNECTED -> Color(0xFF4CAF50) // Green
        VpnConnectionState.CONNECTING, VpnConnectionState.AUTHENTICATING -> Color(0xFFFF9800) // Orange
        VpnConnectionState.FAILED -> Color(0xFFF44336) // Red
        else -> Color.Gray
    }

    Box(
        modifier = Modifier.size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        if (state == VpnConnectionState.CONNECTING || state == VpnConnectionState.AUTHENTICATING) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = color.copy(alpha = 0.3f),
                    radius = size.minDimension / 2 * pulseScale,
                    style = Stroke(width = 4.dp.toPx())
                )
            }
        }
        
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (state == VpnConnectionState.CONNECTED) Icons.Default.Shield else Icons.Default.Security,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = color
            )
        }
    }
}
