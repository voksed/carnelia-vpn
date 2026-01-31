package com.carnelia.vpn.core

import kotlinx.coroutines.*
import com.carnelia.vpn.core.protocols.ProtocolFactory
import com.carnelia.vpn.core.protocols.IVpnProtocol

/**
 * Central VPN Manager
 * Coordinates all VPN operations
 */
class VpnManager(private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())) {
    
    private var currentProtocol: IVpnProtocol? = null
    private var currentConfig: VpnServerConfig? = null
    
    private var connectionState = ConnectionState.DISCONNECTED
    private var stats = VpnStats()
    
    private val stateListeners = mutableListOf<(ConnectionState) -> Unit>()
    private val statsListeners = mutableListOf<(VpnStats) -> Unit>()
    private val errorListeners = mutableListOf<(VpnErrorCode) -> Unit>()
    
    /**
     * Connect to VPN server
     */
    fun connect(config: VpnServerConfig) {
        coroutineScope.launch {
            try {
                updateConnectionState(ConnectionState.PREPARING)
                
                // Create protocol instance
                currentProtocol = ProtocolFactory.createProtocol(config.protocol)
                currentConfig = config
                
                // Prepare protocol
                val prepareResult = currentProtocol?.prepare()
                if (prepareResult != VpnErrorCode.NO_ERROR) {
                    notifyError(prepareResult ?: VpnErrorCode.UNKNOWN_ERROR)
                    updateConnectionState(ConnectionState.ERROR)
                    return@launch
                }
                
                // Setup listeners
                setupProtocolListeners()
                
                // Start connection
                val startResult = currentProtocol?.start(config)
                if (startResult != VpnErrorCode.NO_ERROR) {
                    notifyError(startResult ?: VpnErrorCode.UNKNOWN_ERROR)
                    updateConnectionState(ConnectionState.ERROR)
                    return@launch
                }
                
                // Update state
                updateConnectionState(currentProtocol?.getConnectionState() ?: ConnectionState.CONNECTED)
                
            } catch (e: Exception) {
                e.printStackTrace()
                notifyError(VpnErrorCode.UNKNOWN_ERROR)
                updateConnectionState(ConnectionState.ERROR)
            }
        }
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
    fun onError(listener: (VpnErrorCode) -> Unit) {
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
        }
    }
    
    /**
     * Notify error
     */
    private fun notifyError(error: VpnErrorCode) {
        stats = stats.copy(lastError = error)
        errorListeners.forEach { it(error) }
    }
    
    /**
     * Cleanup resources
     */
    fun destroy() {
        coroutineScope.cancel()
    }
}
