package com.swift.browser.vpnengine.api

import android.content.Context
import android.content.Intent
import com.swift.browser.vpnengine.VpnEngineActivity
import com.swift.browser.vpnengine.VpnEngineDependencyContainer
import com.swift.browser.vpnengine.domain.VpnConnectionState
import kotlinx.coroutines.flow.StateFlow

interface VpnEngineApi {
    val connectionState: StateFlow<VpnConnectionState>
    
    fun init(context: Context)
    fun launchVpnUi(context: Context)
    fun isConnected(): Boolean
}

class VpnEngineApiImpl : VpnEngineApi {
    override val connectionState: StateFlow<VpnConnectionState>
        get() = VpnEngineDependencyContainer.stateManager.connectionState

    override fun init(context: Context) {
        VpnEngineDependencyContainer.initSubsystems(context)
    }

    override fun launchVpnUi(context: Context) {
        val intent = Intent(context, VpnEngineActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    override fun isConnected(): Boolean {
        return connectionState.value == VpnConnectionState.CONNECTED
    }
}

object VpnEngineProvider {
    val api: VpnEngineApi by lazy { VpnEngineApiImpl() }
}
