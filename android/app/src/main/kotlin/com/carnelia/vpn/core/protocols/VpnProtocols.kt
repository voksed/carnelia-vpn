package com.carnelia.vpn.core.protocols

import com.carnelia.vpn.core.VpnErrorCode
import com.carnelia.vpn.core.VpnServerConfig
import com.carnelia.vpn.core.ConnectionState

/**
 * Base VPN Protocol Interface
 */
interface IVpnProtocol {
    
    suspend fun prepare(): VpnErrorCode
    
    suspend fun start(config: VpnServerConfig): VpnErrorCode
    
    suspend fun stop()
    
    fun getConnectionState(): ConnectionState
    
    fun getBytesTransferred(): Pair<Long, Long> // (sent, received)
    
    fun onConnectionStateChanged(listener: (ConnectionState) -> Unit)
    
    fun onBytesChanged(listener: (Long, Long) -> Unit)
}

/**
 * Outline VPN Protocol (Shadowsocks-based)
 * Lightweight, fast, works in restricted networks
 */
class OutlineVpnProtocol : IVpnProtocol {
    
    private var connectionState = ConnectionState.DISCONNECTED
    private var bytesSent = 0L
    private var bytesReceived = 0L
    
    private val stateListeners = mutableListOf<(ConnectionState) -> Unit>()
    private val bytesListeners = mutableListOf<(Long, Long) -> Unit>()
    
    override suspend fun prepare(): VpnErrorCode {
        return VpnErrorCode.NO_ERROR
    }
    
    override suspend fun start(config: VpnServerConfig): VpnErrorCode {
        try {
            updateConnectionState(ConnectionState.CONNECTING)
            
            // Outline uses Shadowsocks protocol
            // config.config should contain: "method", "password"
            val method = config.config["method"] ?: "chacha20-ietf-poly1305"
            val password = config.config["password"] ?: return VpnErrorCode.CONFIGURATION_ERROR
            
            // In production: connect to Outline server using liboutline
            // For now: simulate connection
            simulateConnection()
            
            updateConnectionState(ConnectionState.CONNECTED)
            return VpnErrorCode.NO_ERROR
            
        } catch (e: Exception) {
            updateConnectionState(ConnectionState.ERROR)
            return VpnErrorCode.CONNECTION_FAILED
        }
    }
    
    override suspend fun stop() {
        updateConnectionState(ConnectionState.DISCONNECTING)
        // Cleanup
        updateConnectionState(ConnectionState.DISCONNECTED)
    }
    
    override fun getConnectionState(): ConnectionState = connectionState
    
    override fun getBytesTransferred(): Pair<Long, Long> = Pair(bytesSent, bytesReceived)
    
    override fun onConnectionStateChanged(listener: (ConnectionState) -> Unit) {
        stateListeners.add(listener)
    }
    
    override fun onBytesChanged(listener: (Long, Long) -> Unit) {
        bytesListeners.add(listener)
    }
    
    private fun updateConnectionState(newState: ConnectionState) {
        if (connectionState != newState) {
            connectionState = newState
            stateListeners.forEach { it(newState) }
        }
    }
    
    private fun simulateConnection() {
        // Simulate network activity
        bytesSent += 1024
        bytesReceived += 2048
        bytesListeners.forEach { it(bytesSent, bytesReceived) }
    }
}

/**
 * OpenVPN Protocol
 * Traditional, widely compatible, reliable
 */
class OpenVpnProtocol : IVpnProtocol {
    
    private var connectionState = ConnectionState.DISCONNECTED
    private var bytesSent = 0L
    private var bytesReceived = 0L
    
    private val stateListeners = mutableListOf<(ConnectionState) -> Unit>()
    private val bytesListeners = mutableListOf<(Long, Long) -> Unit>()
    
    override suspend fun prepare(): VpnErrorCode {
        return VpnErrorCode.NO_ERROR
    }
    
    override suspend fun start(config: VpnServerConfig): VpnErrorCode {
        try {
            updateConnectionState(ConnectionState.CONNECTING)
            
            // OpenVPN requires .ovpn config
            val ovpnConfig = config.config["ovpn_data"] ?: return VpnErrorCode.CONFIGURATION_ERROR
            
            // In production: spawn openvpn process via Android VPN Service
            // For now: simulate
            
            updateConnectionState(ConnectionState.CONNECTED)
            return VpnErrorCode.NO_ERROR
            
        } catch (e: Exception) {
            updateConnectionState(ConnectionState.ERROR)
            return VpnErrorCode.CONNECTION_FAILED
        }
    }
    
    override suspend fun stop() {
        updateConnectionState(ConnectionState.DISCONNECTING)
        // Cleanup
        updateConnectionState(ConnectionState.DISCONNECTED)
    }
    
    override fun getConnectionState(): ConnectionState = connectionState
    
    override fun getBytesTransferred(): Pair<Long, Long> = Pair(bytesSent, bytesReceived)
    
    override fun onConnectionStateChanged(listener: (ConnectionState) -> Unit) {
        stateListeners.add(listener)
    }
    
    override fun onBytesChanged(listener: (Long, Long) -> Unit) {
        bytesListeners.add(listener)
    }
    
    private fun updateConnectionState(newState: ConnectionState) {
        if (connectionState != newState) {
            connectionState = newState
            stateListeners.forEach { it(newState) }
        }
    }
}

/**
 * WireGuard Protocol
 * Modern, fast, secure
 */
class WireGuardProtocol : IVpnProtocol {
    
    private var connectionState = ConnectionState.DISCONNECTED
    private var bytesSent = 0L
    private var bytesReceived = 0L
    
    private val stateListeners = mutableListOf<(ConnectionState) -> Unit>()
    private val bytesListeners = mutableListOf<(Long, Long) -> Unit>()
    
    override suspend fun prepare(): VpnErrorCode {
        return VpnErrorCode.NO_ERROR
    }
    
    override suspend fun start(config: VpnServerConfig): VpnErrorCode {
        try {
            updateConnectionState(ConnectionState.CONNECTING)
            
            // WireGuard config
            val privateKey = config.config["private_key"] ?: return VpnErrorCode.CONFIGURATION_ERROR
            val address = config.config["address"] ?: "10.0.0.2/32"
            val dns = config.config["dns"] ?: "8.8.8.8"
            
            // In production: use WireGuard Android library
            
            updateConnectionState(ConnectionState.CONNECTED)
            return VpnErrorCode.NO_ERROR
            
        } catch (e: Exception) {
            updateConnectionState(ConnectionState.ERROR)
            return VpnErrorCode.CONNECTION_FAILED
        }
    }
    
    override suspend fun stop() {
        updateConnectionState(ConnectionState.DISCONNECTING)
        updateConnectionState(ConnectionState.DISCONNECTED)
    }
    
    override fun getConnectionState(): ConnectionState = connectionState
    
    override fun getBytesTransferred(): Pair<Long, Long> = Pair(bytesSent, bytesReceived)
    
    override fun onConnectionStateChanged(listener: (ConnectionState) -> Unit) {
        stateListeners.add(listener)
    }
    
    override fun onBytesChanged(listener: (Long, Long) -> Unit) {
        bytesListeners.add(listener)
    }
    
    private fun updateConnectionState(newState: ConnectionState) {
        if (connectionState != newState) {
            connectionState = newState
            stateListeners.forEach { it(newState) }
        }
    }
}

/**
 * Factory for creating protocol instances
 */
object ProtocolFactory {
    fun createProtocol(protocol: com.carnelia.vpn.core.VpnProtocol): IVpnProtocol {
        return when (protocol) {
            com.carnelia.vpn.core.VpnProtocol.OUTLINE -> OutlineVpnProtocol()
            com.carnelia.vpn.core.VpnProtocol.OPENVPN -> OpenVpnProtocol()
            com.carnelia.vpn.core.VpnProtocol.WIREGUARD -> WireGuardProtocol()
            else -> OutlineVpnProtocol() // Default to Outline
        }
    }
}
