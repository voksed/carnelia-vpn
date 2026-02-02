package com.carnelia.vpn.core.protocols

import com.carnelia.vpn.core.VpnErrorCode
import com.carnelia.vpn.core.VpnServerConfig
import com.carnelia.vpn.core.ConnectionState
import com.carnelia.vpn.core.xray.XrayConfigBuilder
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
// import libv2ray.Libv2ray
import com.carnelia.vpn.utils.AppLogger

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
    
    fun onNetworkInterfaceCreated(fileDescriptor: android.os.ParcelFileDescriptor)
}

/**
 * Outline VPN Protocol (Shadowsocks-based)
 * Now handled by XrayVpnProtocol (Shadowsocks support)
 */
// class OutlineVpnProtocol removed - mapped to XrayVpnProtocol

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
            
            AppLogger.error("OpenVpnProtocol", Exception("OpenVPN is not yet implemented in this version."))
            
            // OpenVPN requires .ovpn config
            // val ovpnConfig = config.config["ovpn_data"] ?: return VpnErrorCode.CONFIGURATION_ERROR
            
            updateConnectionState(ConnectionState.ERROR)
            return VpnErrorCode.PROTOCOL_ERROR
            
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
    
    override fun onNetworkInterfaceCreated(fileDescriptor: android.os.ParcelFileDescriptor) {
        // Not used for OpenVPN/Impl
    }
    
    // override fun startTun2Socks(fd: Int) {}
    
    private fun updateConnectionState(newState: ConnectionState) {
        if (connectionState != newState) {
            connectionState = newState
            stateListeners.forEach { it(newState) }
        }
    }
}

/**
 * WireGuard Protocol
 * Now handled by XrayVpnProtocol (WireGuard outbound support)
 */
// class WireGuardProtocol removed - mapped to XrayVpnProtocol


/**
 * Xray Protocol (Disabled for Outline compatibility)
 */
class XrayVpnProtocol : IVpnProtocol {
    
    private val connectionState = ConnectionState.DISCONNECTED
    
    override suspend fun prepare(): VpnErrorCode {
        return VpnErrorCode.NO_ERROR
    }
    
    override suspend fun start(config: VpnServerConfig): VpnErrorCode {
        // Xray disabled to avoid class conflicts with Tun2Socks
        AppLogger.error("XrayVpnProtocol", Exception("Xray protocol is disabled in this build."))
        return VpnErrorCode.PROTOCOL_ERROR
    }
    
    override suspend fun stop() {}
    
    override fun getConnectionState(): ConnectionState = connectionState
    
    override fun getBytesTransferred(): Pair<Long, Long> = Pair(0L, 0L)
    
    override fun onConnectionStateChanged(listener: (ConnectionState) -> Unit) {}
    
    override fun onBytesChanged(listener: (Long, Long) -> Unit) {}

    override fun onNetworkInterfaceCreated(fileDescriptor: android.os.ParcelFileDescriptor) {}
}

/**
 * Factory for creating protocol instances
 */
object ProtocolFactory {
    fun createProtocol(protocol: com.carnelia.vpn.core.VpnProtocol): IVpnProtocol {
        return when (protocol) {
            com.carnelia.vpn.core.VpnProtocol.OUTLINE -> OutlineVpnProtocol()
            
            com.carnelia.vpn.core.VpnProtocol.SHADOWSOCKS,
            com.carnelia.vpn.core.VpnProtocol.WIREGUARD,
            com.carnelia.vpn.core.VpnProtocol.VLESS,
            com.carnelia.vpn.core.VpnProtocol.VMESS,
            com.carnelia.vpn.core.VpnProtocol.TROJAN -> XrayVpnProtocol()
            
            com.carnelia.vpn.core.VpnProtocol.OPENVPN -> OpenVpnProtocol()
            
            else -> XrayVpnProtocol() 
        }
    }
}
