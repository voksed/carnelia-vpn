package com.carnelia.vpn.data

import com.carnelia.vpn.core.VpnServerConfig

data class Subscription(
    val id: String, // Unique ID (UUID)
    val name: String, // Custom name (e.g., "My Office VPN")
    val url: String, // Source URL
    val lastUpdated: Long = 0, // Timestamp
    val autoUpdate: Boolean = true,
    val serverCount: Int = 0 // Cached count
)