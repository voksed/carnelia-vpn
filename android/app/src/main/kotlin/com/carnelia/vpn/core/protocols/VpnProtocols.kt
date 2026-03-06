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
    private val bytesListeners = mutableListOf<(Long, Long) -> Unit>()
    
    private var bytesSent = 0L
    private var bytesReceived = 0L

    // Listener for library events
    private val vpnStatusListener = object : de.blinkt.openvpn.core.VpnStatus.StateListener {
        override fun updateState(state: String?, logmessage: String?, localizedResId: Int, level: de.blinkt.openvpn.core.ConnectionStatus?, intent: android.content.Intent?) {
            val newState = when (level) {
                de.blinkt.openvpn.core.ConnectionStatus.LEVEL_CONNECTED -> ConnectionState.CONNECTED
                de.blinkt.openvpn.core.ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET,
                de.blinkt.openvpn.core.ConnectionStatus.LEVEL_CONNECTING_SERVER_REPLIED -> ConnectionState.CONNECTING
                de.blinkt.openvpn.core.ConnectionStatus.LEVEL_AUTH_FAILED,
                de.blinkt.openvpn.core.ConnectionStatus.LEVEL_NONETWORK -> ConnectionState.ERROR
                else -> ConnectionState.DISCONNECTED
            }
            updateConnectionState(newState)
        }

        override fun setConnectedVPN(uuid: String?) {}
    }

    private val vpnByteListener = object : de.blinkt.openvpn.core.VpnStatus.ByteCountListener {
        override fun updateByteCount(inBytes: Long, outBytes: Long, diffIn: Long, diffOut: Long) {
             bytesReceived = inBytes
             bytesSent = outBytes
             bytesListeners.forEach { it(bytesSent, bytesReceived) }
        }
    }

    override suspend fun prepare(): VpnErrorCode = VpnErrorCode.NO_ERROR
    
    override suspend fun start(config: VpnServerConfig): VpnErrorCode {
        updateConnectionState(ConnectionState.CONNECTING)
        try {
            AppLogger.log("OpenVpnProtocol: Starting OpenVPN UI...")
            
            // Register listeners
            de.blinkt.openvpn.core.VpnStatus.addStateListener(vpnStatusListener)
            de.blinkt.openvpn.core.VpnStatus.addByteCountListener(vpnByteListener)

            // Launch the helper which starts the activity
            com.carnelia.vpn.utils.OpenVpnHelper.startVpn(com.carnelia.vpn.CarheliaApplication.instance, config)
            
            return VpnErrorCode.NO_ERROR
            
        } catch (e: Exception) {
            AppLogger.error("OpenVpnProtocol", e)
            updateConnectionState(ConnectionState.ERROR)
            return VpnErrorCode.CONNECTION_FAILED
        }
    }
    
    override suspend fun stop() {
        updateConnectionState(ConnectionState.DISCONNECTING)
        de.blinkt.openvpn.core.VpnStatus.removeStateListener(vpnStatusListener)
        de.blinkt.openvpn.core.VpnStatus.removeByteCountListener(vpnByteListener)
        
        // Try to stop service
        try {
            val intent = android.content.Intent(com.carnelia.vpn.CarheliaApplication.instance, de.blinkt.openvpn.core.OpenVPNService::class.java)
            intent.action = de.blinkt.openvpn.core.OpenVPNService.DISCONNECT_VPN
            com.carnelia.vpn.CarheliaApplication.instance.startService(intent)
        } catch (e: Exception) {
            AppLogger.error("OpenVpnProtocol: Stop failed", e)
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
            // Check for Tor Mode
            if (config.config["tor_mode"] == "true") {
                AppLogger.log("XrayVpnProtocol: Starting Tor Service...")
                com.carnelia.vpn.core.TorManager.startTor(context)
                
                // Wait for Tor to bootstrap (Max 60s)
                try {
                    withTimeout(60000) {
                        while (!com.carnelia.vpn.core.TorManager.isConnected()) {
                            delay(1000)
                            // Optionally report detailed status if possible via callback
                        }
                    }
                    AppLogger.log("XrayVpnProtocol: Tor Connected!")
                } catch (e: TimeoutCancellationException) {
                    AppLogger.error("Tor bootstrap timed out, proceeding anyway (might be slow)...")
                }
            }

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
            com.carnelia.vpn.core.TorManager.stopTor()
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
            com.carnelia.vpn.core.TorManager.stopTor()
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
