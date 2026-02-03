package com.carnelia.vpn.core.protocols

import android.content.Context
import com.carnelia.vpn.core.VpnErrorCode
import com.carnelia.vpn.core.VpnServerConfig
import com.carnelia.vpn.core.ConnectionState
import com.carnelia.vpn.core.XrayCoreManager
import com.carnelia.vpn.utils.AppLogger
import kotlinx.coroutines.*
import org.json.JSONObject
import tun2socks.Tun2socks
import shadowsocks.Shadowsocks
import de.blinkt.openvpn.core.VpnStatus
import de.blinkt.openvpn.VpnProfile

/**
 * Base VPN Protocol Interface
 */
interface IVpnProtocol {
    
    suspend fun prepare(): VpnErrorCode
    
    suspend fun start(config: VpnServerConfig): VpnErrorCode
    
    suspend fun stop()
    
    fun getConnectionState(): ConnectionState
    
    fun getBytesTransferred(): Pair<Long, Long>
    
    fun onConnectionStateChanged(listener: (ConnectionState) -> Unit)
    
    fun onBytesChanged(listener: (Long, Long) -> Unit)
    
    fun onNetworkInterfaceCreated(fileDescriptor: android.os.ParcelFileDescriptor)
}

class OpenVpnProtocol : IVpnProtocol {
    
    private var connectionState = ConnectionState.DISCONNECTED
    private val stateListeners = mutableListOf<(ConnectionState) -> Unit>()
    
    override suspend fun prepare(): VpnErrorCode = VpnErrorCode.NO_ERROR
    
    override suspend fun start(config: VpnServerConfig): VpnErrorCode {
        updateConnectionState(ConnectionState.CONNECTING)
        try {
            AppLogger.log("OpenVpnProtocol: Starting OpenVPN...")
            
            // Note: In a real implementation using ics-openvpn, 
            // you typically construct a VpnProfile object from the .ovpn configuration string
            // and then call VPNLaunchHelper.startOpenVpn(profile, context).
            // However, ics-openvpn is designed to run as its OWN Service (OpenVPNService).
            // Since WE are the VpnService (V2RayVpnService/CarneliaVpnService), we have a conflict.
            // ics-openvpn supports "Remote Service" mode or embedded mode, but it's complex.
            
            // For now, unless we fully integrate the OpenVPN Service structure, this is a placeholder.
            // But we have added the dependency, so classes are available.
            
            // Example of how to parse:
            val ovpnContent = config.config["ovpn_data"] ?: return VpnErrorCode.CONFIGURATION_ERROR
            // val profile = de.blinkt.openvpn.core.ConfigParser().parse(StringReader(ovpnContent))
            
            // Since we can't easily start it without conflicting with our Xray/Tun2Socks service,
            // we will mark it as not fully supported yet in this hybrid mode.
            
            // To support OpenVPN properly, we would likely need to switch our App's Service
            // to a wrapping service that can delegate to either Tun2Socks OR OpenVPN's native handler via JNI.
            
            throw Exception("OpenVPN Service Integration Pending (Requires Service Re-architecture)")
            
        } catch (e: Exception) {
            AppLogger.error("OpenVpnProtocol", e)
            updateConnectionState(ConnectionState.ERROR)
            return VpnErrorCode.CONNECTION_FAILED
        }
    }
    
    override suspend fun stop() {
        updateConnectionState(ConnectionState.DISCONNECTING)
        // Stop logic
        updateConnectionState(ConnectionState.DISCONNECTED)
    }
    
    override fun getConnectionState(): ConnectionState = connectionState
    
    override fun getBytesTransferred(): Pair<Long, Long> = Pair(0L, 0L)
    
    override fun onConnectionStateChanged(listener: (ConnectionState) -> Unit) {
        stateListeners.add(listener)
    }
    
    override fun onBytesChanged(listener: (Long, Long) -> Unit) {}
    
    override fun onNetworkInterfaceCreated(fileDescriptor: android.os.ParcelFileDescriptor) {}
    
    private fun updateConnectionState(newState: ConnectionState) {
        if (connectionState != newState) {
            connectionState = newState
            stateListeners.forEach { it(newState) }
        }
    }
}

/**
 * Xray Protocol (Process-based)
 * Uses external libxray_core.so process + Tun2Socks (Local bridge)
 */
class XrayVpnProtocol(private val context: Context) : IVpnProtocol {
    
    private var connectionState = ConnectionState.DISCONNECTED
    private var bytesSent = 0L
    private var bytesReceived = 0L
    private var activeTunnel: tun2socks.Tunnel? = null
    
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var isRunning = false
    
    private val stateListeners = mutableListOf<(ConnectionState) -> Unit>()
    private val bytesListeners = mutableListOf<(Long, Long) -> Unit>()
    
    override suspend fun prepare(): VpnErrorCode {
        return VpnErrorCode.NO_ERROR
    }
    
    override suspend fun start(config: VpnServerConfig): VpnErrorCode {
        updateConnectionState(ConnectionState.CONNECTING)
        isRunning = true
        
        try {
            AppLogger.log("XrayVpnProtocol: Starting Xray Core via Process...")
            XrayCoreManager.startCore(context, config)
            
            // Wait for Xray to bind port (10808)
            delay(500)
            
            // Xray is running. We report connected so VpnService creates interface.
            // Then onNetworkInterfaceCreated starts Tun2Socks (to localhost).
            updateConnectionState(ConnectionState.CONNECTED)
            return VpnErrorCode.NO_ERROR
            
        } catch (e: Exception) {
            AppLogger.error("XrayVpnProtocol: Start failed", e)
            XrayCoreManager.stopCore()
            return VpnErrorCode.PROTOCOL_ERROR
        }
    }
    
    override suspend fun stop() {
        isRunning = false
        updateConnectionState(ConnectionState.DISCONNECTING)
        try {
            activeTunnel?.disconnect()
            activeTunnel = null
            XrayCoreManager.stopCore()
        } catch(e: Exception) {
             AppLogger.error("XrayVpnProtocol: Stop error", e)
        }
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
        if (!isRunning) return
        
        scope.launch {
            try {
                AppLogger.log("XrayVpnProtocol: Connecting Tun2Socks to Local Xray Bridge...")
                
                // Config for Tun2Socks -> Localhost Xray Port
                val jsonConfig = JSONObject()
                jsonConfig.put("host", "127.0.0.1")
                jsonConfig.put("port", XrayCoreManager.LOCAL_PORT)
                jsonConfig.put("password", XrayCoreManager.LOCAL_PASSWORD)
                jsonConfig.put("method", XrayCoreManager.LOCAL_METHOD)
                
                // Tun2Socks client
                val client = Shadowsocks.newClientFromJSON(jsonConfig.toString())
                val tunnel = Tun2socks.connectShadowsocksTunnel(fileDescriptor.fd.toLong(), client, true)
                
                activeTunnel = tunnel
                AppLogger.log("XrayVpnProtocol: Tunnel Established!")
                
                // Stats loop
                startStatsLoop()

            } catch (e: Exception) {
                AppLogger.error("XrayVpnProtocol: TUN Bridge Failed", e)
                stop()
            }
        }
    }
    
    private fun startStatsLoop() {
        scope.launch {
             while (isRunning) {
                 try {
                     // Get total traffic as proxy
                     val rx = android.net.TrafficStats.getTotalRxBytes()
                     val tx = android.net.TrafficStats.getTotalTxBytes()
                     if (rx != bytesReceived || tx != bytesSent) {
                        bytesReceived = rx
                        bytesSent = tx
                        bytesListeners.forEach { it(bytesSent, bytesReceived) }
                     }
                 } catch (e: Exception) {}
                 delay(1000)
             }
        }
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
    fun createProtocol(context: Context, protocol: com.carnelia.vpn.core.VpnProtocol): IVpnProtocol {
        return when (protocol) {
            com.carnelia.vpn.core.VpnProtocol.OUTLINE -> OutlineVpnProtocol()
            
            com.carnelia.vpn.core.VpnProtocol.SHADOWSOCKS,
            com.carnelia.vpn.core.VpnProtocol.WIREGUARD,
            com.carnelia.vpn.core.VpnProtocol.VLESS,
            com.carnelia.vpn.core.VpnProtocol.VMESS,
            com.carnelia.vpn.core.VpnProtocol.SOCKS,
            com.carnelia.vpn.core.VpnProtocol.HTTP,
            com.carnelia.vpn.core.VpnProtocol.TROJAN -> XrayVpnProtocol(context)
            
            com.carnelia.vpn.core.VpnProtocol.OPENVPN -> OpenVpnProtocol()
            
            else -> XrayVpnProtocol(context) 
        }
    }
}
