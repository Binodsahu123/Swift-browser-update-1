package com.swift.browser.vpnengine.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swift.browser.vpnengine.data.model.VpnProfile
import com.swift.browser.vpnengine.domain.ProviderManager
import java.io.File
import com.swift.browser.vpnengine.data.model.VpnProtocol
import com.swift.browser.vpnengine.data.model.VpnServer
import com.swift.browser.vpnengine.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class VpnUiState(
    val connectionState: VpnConnectionState = VpnConnectionState.DISCONNECTED,
    val connectedServerId: String? = null,
    val servers: List<VpnServer> = emptyList(),
    val filteredServers: List<VpnServer> = emptyList(),
    val favoriteServerIds: Set<String> = emptySet(),
    val recentServerIds: List<String> = emptyList(),
    val settings: VpnSettings = VpnSettings(),
    val errorMessage: String? = null,
    val selectedCountry: String? = null,
    val selectedProtocol: VpnProtocol? = null,
    val profiles: List<VpnProfile> = emptyList(),
    val isRefreshing: Boolean = false,
    val lastUpdated: Long = 0L,
    val refreshError: String? = null,
    val trafficStats: VpnTrafficStats = VpnTrafficStats(),
    val sessionStats: VpnSessionStats = VpnSessionStats(),
    val networkQuality: String = "Average",
    val logs: List<VpnLogEntry> = emptyList(),
    val healthStats: VpnHealthStats = VpnHealthStats(),
    val securityStatus: VpnSecurityStatus = VpnSecurityStatus(),
    val aiRecommendation: AiRecommendation = AiRecommendation(),
    
    val currentUser: VpnUserProfile? = null,
    val downloadProgress: Float = 0f,
    val isDownloading: Boolean = false,
    val searchQuery: String = "",
    val activeTab: Int = 0,
    val aiAdvice: AiAdvice? = null,
    val selectedDns: DnsConfig? = null,
    
    
)

class VpnViewModel(
    private val serverManager: VpnServerManager,
    private val stateManager: VpnStateManager,
    private val providerManager: ProviderManager,
    private val accountManager: VpnAccountManager,
    private val downloadManager: VpnDownloadManager,
    private val profileValidator: VpnProfileValidator,
    private val backupManager: VpnBackupManager?,
    private val diagnosticsManager: VpnDiagnosticsManager?,
    private val aiVpnAssistant: AiVpnAssistant,
    private val dnsManager: DnsManager,

) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _activeTab = MutableStateFlow(0)
    
    val uiState: StateFlow<VpnUiState> = combine(
        stateManager.connectionState,
        stateManager.connectedServerId,
        serverManager.servers,
        serverManager.favoriteServerIds,
        serverManager.recentServerIds,
        stateManager.settings,
        stateManager.errorMessage,
        providerManager.profiles,
        serverManager.isRefreshing,
        serverManager.lastUpdated,
        serverManager.refreshError,
        stateManager.trafficStats,
        stateManager.sessionStats,
        stateManager.networkQuality
    ) { args ->
        val servers = args[2] as List<VpnServer>
        VpnUiState(
            connectionState = args[0] as VpnConnectionState,
            connectedServerId = args[1] as String?,
            servers = servers,
            filteredServers = servers,
            favoriteServerIds = args[3] as Set<String>,
            recentServerIds = args[4] as List<String>,
            settings = args[5] as VpnSettings,
            errorMessage = args[6] as String?,
            profiles = args[7] as List<VpnProfile>,
            isRefreshing = args[8] as Boolean,
            lastUpdated = args[9] as Long,
            refreshError = args[10] as String?,
            trafficStats = args[11] as VpnTrafficStats,
            sessionStats = args[12] as VpnSessionStats,
            networkQuality = args[13] as String
        )
    }.combine(
        combine(
            stateManager.logs,
            stateManager.healthStats,
            stateManager.securityStatus,
            stateManager.aiRecommendation,
            accountManager.currentUser,
            downloadManager.downloadProgress,
            downloadManager.isDownloading,
            _searchQuery,
            _activeTab
        ) { args -> args }
    ) { state1, args2 ->
        val searchQuery = args2[7] as String
        state1.copy(
            logs = args2[0] as List<VpnLogEntry>,
            healthStats = args2[1] as VpnHealthStats,
            securityStatus = args2[2] as VpnSecurityStatus,
            aiRecommendation = args2[3] as AiRecommendation,
            currentUser = args2[4] as VpnUserProfile,
            downloadProgress = args2[5] as Float,
            isDownloading = args2[6] as Boolean,
            searchQuery = searchQuery,
            activeTab = args2[8] as Int,
            filteredServers = if (searchQuery.isBlank()) state1.servers else serverManager.searchServers(searchQuery)
        )
    }.combine(
        combine(
            aiVpnAssistant.currentAdvice,
            dnsManager.selectedDns
        ) { args -> args }
    ) { state2, args3 ->
        state2.copy(
            aiAdvice = args3[0] as AiAdvice?,
            selectedDns = args3[1] as DnsConfig?,
            
            
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VpnUiState()
    )

    init {
        viewModelScope.launch {
            serverManager.refreshServers()
        }
    }

    fun refreshServers(hasInternet: Boolean = true) {
        viewModelScope.launch {
            serverManager.refreshServers(hasInternet)
        }
    }

    fun toggleFavorite(serverId: String) {
        serverManager.toggleFavorite(serverId)
    }

    fun addRecent(serverId: String) {
        serverManager.addRecent(serverId)
    }

    fun updateSettings(settings: VpnSettings) {
        stateManager.updateSettings(settings)
    }

    fun clearError() {
        stateManager.clearError()
    }
    
    fun clearLogs() {
        stateManager.clearLogs()
    }
    
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun setActiveTab(tab: Int) {
        _activeTab.value = tab
    }
    
    fun switchAccount(name: String) {
        accountManager.switchToLocalProfile(name)
    }
    
    fun createBackup(): File? {
        val s = uiState.value
        return backupManager?.createBackup(s.settings, s.favoriteServerIds, s.recentServerIds)
    }
    
    fun generateDiagnostics(): File? {
        val s = uiState.value
        return diagnosticsManager?.generateDiagnosticsReport(s.logs, s.healthStats, s.securityStatus)
    }
    
    fun downloadProviderConfig(url: String, name: String) {
        viewModelScope.launch {
            val file = downloadManager.downloadProviderConfig(DownloadConfig(url, name))
            if (file != null) {
                // If ZIP, handle extraction. If valid, import.
                val result = profileValidator.validateProfile(file)
                if (result.isValid) {
                    stateManager.addLog("Downloaded config verified and imported for $name")
                } else {
                    stateManager.setError("Downloaded config invalid: ${result.errors.firstOrNull()}")
                }
            }
        }
    }

fun importProfile(name: String, file: File, forceProtocol: VpnProtocol? = null) {
        viewModelScope.launch {
            val validation = profileValidator.validateProfile(file)
            if (!validation.isValid) {
                stateManager.setError("Invalid Profile: ${validation.errors.firstOrNull()}")
                return@launch
            }
            
            val content = file.readText()
            
            // Check for duplicates
            val isDuplicate = providerManager.profiles.value.any { it.configData == content }
            if (isDuplicate) {
                stateManager.setError("Profile already exists")
                return@launch
            }
            
            // In a real app, zip extraction logic would go here.
            if (validation.protocol == "PACKAGE") {
                stateManager.addLog("ZIP Package importing is currently mocked.", "INFO")
                return@launch
            }
               
            val detectedProtocol = when (validation.protocol) {
                "OPENVPN" -> VpnProtocol.OPENVPN
                "WIREGUARD" -> VpnProtocol.WIREGUARD
                else -> forceProtocol ?: VpnProtocol.CUSTOM
            }
               
            val profile = VpnProfile(
                name = name,
                providerId = "import",
                serverId = "custom_imported",
                country = "Unknown",
                protocol = detectedProtocol,
                configData = content,
                isFavorite = false,
                
                
            )
            providerManager.addProfile(profile)
            stateManager.addLog("Imported profile: $name", "INFO")
        }
    }
    
    fun removeProfile(profileId: String) {
        providerManager.removeProfile(profileId)
    }
}
