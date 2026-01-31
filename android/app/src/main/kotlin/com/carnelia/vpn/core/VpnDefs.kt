package com.carnelia.vpn.core

/**
 * VPN Protocol Support
 */
enum class VpnProtocol {
    OUTLINE,
    OPENVPN,
    WIREGUARD,
    IKEV2,
    CLOAK,
    XRAY
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

import java.io.Serializable

/**
 * VPN Server Configuration
 */
data class VpnServerConfig(
    val id: String,
    val name: String,
    val protocol: VpnProtocol,
    val host: String,
    val port: Int,
    val config: Map<String, String>, // Protocol-specific config
    val country: String? = null,
    val flag: String? = null
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
