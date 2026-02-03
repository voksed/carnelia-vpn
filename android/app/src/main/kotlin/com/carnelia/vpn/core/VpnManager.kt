package com.carnelia.vpn.core

import kotlinx.coroutines.*
import com.carnelia.vpn.core.protocols.ProtocolFactory
import com.carnelia.vpn.core.protocols.IVpnProtocol

import com.carnelia.vpn.utils.PrefsManager
import android.content.Context

/**
 * Central VPN Manager
 * Coordinates all VPN operations
 */
class VpnManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())
) {

    
    private var currentProtocol: IVpnProtocol? = null
    private var currentConfig: VpnServerConfig? = null
    
    private var connectionState = ConnectionState.DISCONNECTED
    private var stats = VpnStats()
    
    private val stateListeners = mutableListOf<(ConnectionState) -> Unit>()
    private val statsListeners = mutableListOf<(VpnStats) -> Unit>()
    private val errorListeners = mutableListOf<(String) -> Unit>()
    
    /**
     * Connect to VPN server
     */
    fun connect(config: VpnServerConfig) {
        coroutineScope.launch {
            try {
                updateConnectionState(ConnectionState.PREPARING)
                
                // Inject Bypass RU setting
                val mutableConfig = config.config.toMutableMap()
                
                // Feature Injection
                if (PrefsManager.isBypassRuEnabled(context)) {
                    mutableConfig["bypass_ru"] = "true"
                }
                if (PrefsManager.isFragmentationEnabled(context)) {
                    mutableConfig["frag_enabled"] = "true"
                    mutableConfig["frag_packets"] = PrefsManager.getFragmentPackets(context)
                    mutableConfig["frag_length"] = PrefsManager.getFragmentLength(context)
                    mutableConfig["frag_interval"] = PrefsManager.getFragmentInterval(context)
                }
                
                // DNS Injection logic
                mutableConfig["dns_server"] = PrefsManager.getDnsServer(context)
                
                val modifiedConfig = config.copy(config = mutableConfig)
                
                // Create protocol instance
                currentProtocol = ProtocolFactory.createProtocol(context, config.protocol)
                currentConfig = modifiedConfig
                
                // Prepare protocol
                val prepareResult = currentProtocol?.prepare()
                if (prepareResult != VpnErrorCode.NO_ERROR) {
                    notifyError("Prepare failed: ${prepareResult?.name}")
                    updateConnectionState(ConnectionState.ERROR)
                    return@launch
                }
                
                // Setup listeners
                setupProtocolListeners()
                
                // Start connection
                val startResult = currentProtocol?.start(modifiedConfig)
                if (startResult != VpnErrorCode.NO_ERROR) {
                    notifyError("Start failed: ${startResult?.name}")
                    updateConnectionState(ConnectionState.ERROR)
                    return@launch
                }
                
                // Update state
                updateConnectionState(currentProtocol?.getConnectionState() ?: ConnectionState.CONNECTED)
                
            } catch (e: Exception) {
                e.printStackTrace()
                notifyError(e.message ?: "Unknown fatal error")
                updateConnectionState(ConnectionState.ERROR)
            }
        }
    }
    
    /**
     * Called when TUN interface is ready
     */
    fun onInterfaceEstablished(pfd: android.os.ParcelFileDescriptor) {
        currentProtocol?.onNetworkInterfaceCreated(pfd)
    }

    /**
     * Disconnect from VPN
     */
    fun disconnect() {
        coroutineScope.launch {
            try {
                updateConnectionState(ConnectionState.DISCONNECTING)
                currentProtocol?.stop()
                updateConnectionState(ConnectionState.DISCONNECTED)
            } catch (e: Exception) {
                e.printStackTrace()
                updateConnectionState(ConnectionState.ERROR)
            }
        }
    }
    
    /**
     * Get current connection state
     */
    fun getConnectionState(): ConnectionState = connectionState
    
    /**
     * Get current statistics
     */
    fun getStats(): VpnStats = stats
    
    /**
     * Register state change listener
     */
    fun onStateChanged(listener: (ConnectionState) -> Unit) {
        stateListeners.add(listener)
    }
    
    /**
     * Register stats change listener
     */
    fun onStatsChanged(listener: (VpnStats) -> Unit) {
        statsListeners.add(listener)
    }
    
    /**
     * Register error listener
     */
    fun onError(listener: (String) -> Unit) {
        errorListeners.add(listener)
    }
    
    /**
     * Setup protocol listeners
     */
    private fun setupProtocolListeners() {
        currentProtocol?.onConnectionStateChanged { state ->
            updateConnectionState(state)
        }
        
        currentProtocol?.onBytesChanged { sent, received ->
            stats = stats.copy(
                bytesSent = sent,
                bytesReceived = received
            )
            statsListeners.forEach { it(stats) }
            VpnGlobalState.updateStats(stats)
        }
    }
    
    /**
     * Update connection state internally
     */
    private fun updateConnectionState(newState: ConnectionState) {
        if (connectionState != newState) {
            connectionState = newState
            stats = stats.copy(
                isConnected = newState == ConnectionState.CONNECTED
            )
            stateListeners.forEach { it(newState) }
            statsListeners.forEach { it(stats) }
            VpnGlobalState.updateState(newState) // Update Global State
        }
    }
    
    /**
     * Notify error
     */
    private fun notifyError(error: String) {
        // stats = stats.copy(lastError = error) // Stats stores VpnErrorCode enum, so we can't put string there easily without changing Stats class. 
        // For now just notify listeners.
        errorListeners.forEach { it(error) }
    }
    
    /**
     * Cleanup resources
     */
    fun destroy() {
        coroutineScope.cancel()
    }
}
