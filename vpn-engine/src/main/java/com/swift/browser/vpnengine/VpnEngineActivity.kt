package com.swift.browser.vpnengine
import com.swift.browser.vpnengine.service.SwiftVpnService

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.swift.browser.vpnengine.data.provider.VpnGateProvider
import com.swift.browser.vpnengine.domain.VpnConnectionState
import com.swift.browser.vpnengine.domain.VpnServerManager
import com.swift.browser.vpnengine.domain.VpnStateManager
import com.swift.browser.vpnengine.domain.ProviderManager
import com.swift.browser.vpnengine.presentation.VpnViewModel
import com.swift.browser.vpnengine.presentation.ui.VpnEngineScreen

object VpnEngineDependencyContainer {
    val providerLayer = VpnGateProvider()
    val serverManager = VpnServerManager(providerLayer)
    val stateManager = VpnStateManager()
    val providerManager = ProviderManager().apply { registerProvider(com.swift.browser.vpnengine.data.provider.OpenVpnAdapter()); registerProvider(com.swift.browser.vpnengine.data.provider.WireGuardAdapter()) }
    val accountManager = com.swift.browser.vpnengine.domain.VpnAccountManager()
    val downloadManager = com.swift.browser.vpnengine.domain.VpnDownloadManager()
    val profileValidator = com.swift.browser.vpnengine.domain.VpnProfileValidator()
    
    // Lazy initialized because they require Context
    var batteryOptimizer: com.swift.browser.vpnengine.domain.BatteryOptimizer? = null
    var networkChangeDetector: com.swift.browser.vpnengine.domain.NetworkChangeDetector? = null
    var smartAlertManager: com.swift.browser.vpnengine.domain.SmartAlertManager? = null
    var backupManager: com.swift.browser.vpnengine.domain.VpnBackupManager? = null
    var diagnosticsManager: com.swift.browser.vpnengine.domain.VpnDiagnosticsManager? = null
    
    val aiVpnAssistant = com.swift.browser.vpnengine.domain.AiVpnAssistant()
    val smartAutoSwitchManager = com.swift.browser.vpnengine.domain.SmartAutoSwitchManager()
    val dnsManager = com.swift.browser.vpnengine.domain.DnsManager()
    
    
    

    
    fun initSubsystems(context: Context) {
        if (batteryOptimizer == null) {
            val appCtx = context.applicationContext
            batteryOptimizer = com.swift.browser.vpnengine.domain.BatteryOptimizer(appCtx)
            networkChangeDetector = com.swift.browser.vpnengine.domain.NetworkChangeDetector(appCtx)
            smartAlertManager = com.swift.browser.vpnengine.domain.SmartAlertManager(appCtx)
            backupManager = com.swift.browser.vpnengine.domain.VpnBackupManager(appCtx)
            diagnosticsManager = com.swift.browser.vpnengine.domain.VpnDiagnosticsManager(appCtx)
            
            networkChangeDetector?.startListening()
            smartAlertManager?.startMonitoring()
            // We can run benchmark once on startup
            
        }
    }
}

class VpnEngineActivity : ComponentActivity() {
    private lateinit var viewModel: VpnViewModel

    private val vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            // Permission granted, start VPN
            val selectedServer = viewModel.uiState.value.connectedServerId ?: return@registerForActivityResult
            val server = viewModel.uiState.value.servers.find { it.id == selectedServer } ?: return@registerForActivityResult
            val intent = Intent(this, SwiftVpnService::class.java).apply {
                action = SwiftVpnService.ACTION_CONNECT
                putExtra(SwiftVpnService.EXTRA_SERVER_ID, server.id)
                putExtra(SwiftVpnService.EXTRA_PROTOCOL, server.protocol.name)
            }
            startService(intent)
        } else {
            Toast.makeText(this, "VPN Permission Denied", Toast.LENGTH_SHORT).show()
            VpnEngineDependencyContainer.stateManager.setConnectionState(VpnConnectionState.DISCONNECTED)
        }
    }

    private val vpnStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val stateStr = intent?.getStringExtra(SwiftVpnService.EXTRA_STATE) ?: return
            val serverId = intent.getStringExtra(SwiftVpnService.EXTRA_SERVER_ID)
            
            val state = when (stateStr) {
                "CONNECTING" -> VpnConnectionState.CONNECTING
                "CONNECTED" -> VpnConnectionState.CONNECTED
                "DISCONNECTING" -> VpnConnectionState.DISCONNECTING
                "FAILED" -> VpnConnectionState.FAILED
                else -> VpnConnectionState.DISCONNECTED
            }
            VpnEngineDependencyContainer.stateManager.setConnectionState(state, serverId)
        }
    }

override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        VpnEngineDependencyContainer.initSubsystems(this)
        
        viewModel = VpnViewModel(
            VpnEngineDependencyContainer.serverManager, 
            VpnEngineDependencyContainer.stateManager,
            VpnEngineDependencyContainer.providerManager,
            VpnEngineDependencyContainer.accountManager,
            VpnEngineDependencyContainer.downloadManager,
            VpnEngineDependencyContainer.profileValidator,
            VpnEngineDependencyContainer.backupManager,
            VpnEngineDependencyContainer.diagnosticsManager,
            VpnEngineDependencyContainer.aiVpnAssistant,
            VpnEngineDependencyContainer.dnsManager,
            
            
            
        )
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(vpnStateReceiver, IntentFilter(SwiftVpnService.ACTION_VPN_STATE_CHANGED), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(vpnStateReceiver, IntentFilter(SwiftVpnService.ACTION_VPN_STATE_CHANGED))
        }

        setContent {
            MaterialTheme(colorScheme = dynamicLightColorScheme(LocalContext.current)) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VpnEngineScreen(
                        viewModel = viewModel,
                        onBack = { finish() },
                        onConnectRequest = { serverId ->
                            // Update state to connecting early
                            VpnEngineDependencyContainer.stateManager.setConnectionState(VpnConnectionState.CONNECTING, serverId)
                            val intent = VpnService.prepare(this@VpnEngineActivity)
                            if (intent != null) {
                                vpnPermissionLauncher.launch(intent)
                            } else {
                                // Already granted
                                val server = viewModel.uiState.value.servers.find { it.id == serverId }
                                if (server != null) {
                                    val startIntent = Intent(this@VpnEngineActivity, SwiftVpnService::class.java).apply {
                                        action = SwiftVpnService.ACTION_CONNECT
                                        putExtra(SwiftVpnService.EXTRA_SERVER_ID, serverId)
                                        putExtra(SwiftVpnService.EXTRA_PROTOCOL, server.protocol.name)
                                    }
                                    startService(startIntent)
                                }
                            }
                        },
                        onDisconnectRequest = {
                            VpnEngineDependencyContainer.stateManager.setConnectionState(VpnConnectionState.DISCONNECTING)
                            val startIntent = Intent(this@VpnEngineActivity, SwiftVpnService::class.java).apply {
                                action = SwiftVpnService.ACTION_DISCONNECT
                            }
                            startService(startIntent)
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(vpnStateReceiver)
    }
}
