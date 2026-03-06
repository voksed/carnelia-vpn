package com.carnelia.vpn.core

import java.io.Serializable
import androidx.annotation.Keep

/**
 * VPN Protocol Support
 */
@Keep
enum class VpnProtocol {
    OUTLINE,
    OPENVPN,
    WIREGUARD,
    AMNEZIA_WG,
    VLESS,
    VMESS,
    TROJAN,
    SHADOWSOCKS,
    SOCKS,
    HTTP,
    IKEV2,
    CLOAK
}

/**
 * VPN Connection State
 */
enum class ConnectionState {
    UNKNOWN,
    DISCONNECTED,
    PREPARING,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    RECONNECTING,
    ERROR
}

/**
 * VPN Error Codes
 */
enum class VpnErrorCode(val code: Int) {
    NO_ERROR(0),
    CONFIGURATION_ERROR(1),
    PERMISSION_DENIED(2),
    CONNECTION_FAILED(3),
    TIMEOUT(4),
    PROTOCOL_ERROR(5),
    UNKNOWN_ERROR(255)
}

/**
 * VPN Server Configuration
 */
@Keep
data class VpnServerConfig(
    val id: String,
    val name: String,
    val protocol: VpnProtocol,
    val host: String,
    val port: Int,
    val config: Map<String, String>, // Protocol-specific config
    val username: String? = null,
    val password: String? = null,
    val country: String? = null,
    val flag: String? = null,
    val subscriptionId: String? = null
) : Serializable

/**
 * VPN Statistics
 */
data class VpnStats(
    val bytesSent: Long = 0,
    val bytesReceived: Long = 0,
    val connectionTime: Long = 0, // milliseconds
    val isConnected: Boolean = false,
    val lastError: VpnErrorCode? = null
)
