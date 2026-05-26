package com.carnelia.vpn.utils

import android.content.Context
import android.content.SharedPreferences

object PrefsManager {
    private const val PREFS_NAME = "carnelia_prefs"
    private const val KEY_BATTERY_DIALOG_SHOWN = "battery_dialog_shown"
        fun isBatteryDialogShown(context: Context): Boolean = getPrefs(context).getBoolean(KEY_BATTERY_DIALOG_SHOWN, false)
        fun setBatteryDialogShown(context: Context, shown: Boolean = true) = getPrefs(context).edit().putBoolean(KEY_BATTERY_DIALOG_SHOWN, shown).apply()
    private const val KEY_SPLIT_TUNNELING = "split_tunneling_enabled"
    private const val KEY_SPLIT_TUNNEL_MODE = "split_tunneling_mode" // "allow" or "disallow"
    private const val KEY_SELECTED_APPS = "selected_apps_packages"
    private const val KEY_TOR_BRIDGES = "tor_bridges_custom"
    private const val KEY_GEO_ASSETS_INSTALLED = "geo_assets_ready"
    private const val KEY_BYPASS_RU = "bypass_ru_enabled"
    private const val KEY_AUTO_CONNECT = "auto_connect_enabled" // General switch
    private const val KEY_AUTO_CONNECT_WIFI = "auto_connect_wifi"
    private const val KEY_AUTO_CONNECT_MOBILE = "auto_connect_mobile"
    private const val KEY_TOR_ENABLED = "tor_enabled"
    private const val KEY_USE_INTERNAL_TOR = "use_internal_tor"
    private const val KEY_FRAGMENTATION_ENABLED = "frag_enabled"
    private const val KEY_FRAGMENTATION_MODE = "frag_mode" // "light", "balanced", "aggressive"
    private const val KEY_FRAGMENT_PACKETS = "frag_packets"
    private const val KEY_FRAGMENT_LENGTH = "frag_length"
    private const val KEY_FRAGMENT_INTERVAL = "frag_interval"
    private const val KEY_KILL_SWITCH = "kill_switch_enabled"
    private const val KEY_DNS_SERVER = "dns_server_ip"
    private const val KEY_THEME_COLOR = "theme_accent_color"
    private const val KEY_THEME_INDEX = "theme_index"
    private const val KEY_SECRET_THEME_UNLOCKED = "secret_theme_unlocked"
    private const val KEY_NET_SHIELD = "net_shield_enabled"
    private const val KEY_STEALTH_MODE = "stealth_mode_v2"
    private const val KEY_SECURE_KEYS = "secure_key_check"
    private const val KEY_TOR_SOCKS_PORT = "tor_socks_port"
    private const val KEY_TOR_HTTP_PORT = "tor_http_port"
    private const val KEY_TOR_USE_BRIDGES = "tor_use_bridges"
    private const val KEY_TOR_BRIDGE_TYPE = "tor_bridge_type"
    private const val KEY_I2P_ENABLED = "i2p_enabled"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // New Features
    fun isNetShieldEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_NET_SHIELD, true) // Default On
    fun setNetShieldEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_NET_SHIELD, enabled).apply()

    fun isStealthModeEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_STEALTH_MODE, true) // Default On
    fun setStealthModeEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_STEALTH_MODE, enabled).apply()

    fun isSecureKeyCheckEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_SECURE_KEYS, true) // Default On
    fun setSecureKeyCheckEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_SECURE_KEYS, enabled).apply()

    fun isSplitTunnelingEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SPLIT_TUNNELING, false)
    }

    fun setSplitTunnelingEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SPLIT_TUNNELING, enabled).apply()
    }

    fun getSplitTunnelMode(context: Context): String {
        return getPrefs(context).getString(KEY_SPLIT_TUNNEL_MODE, "allow") ?: "allow"
    }

    fun setSplitTunnelMode(context: Context, mode: String) {
        getPrefs(context).edit().putString(KEY_SPLIT_TUNNEL_MODE, mode).apply()
    }
    
    fun isBypassRuEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_BYPASS_RU, false)
    }

    fun setBypassRuEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BYPASS_RU, enabled).apply()
    }
    
    // Zapret / Advanced Tunnel Settings
    private const val KEY_MUX_ENABLED = "mux_enabled"
    private const val KEY_MUX_TCP = "mux_concurrency_tcp" // New
    private const val KEY_MUX_UDP = "mux_concurrency_udp" // New
    private const val KEY_MUX_QUIC = "mux_quic_mode" // New
    private const val KEY_PREFERRED_IP = "preferred_ip_type" // auto, ipv4, ipv6
    private const val KEY_ALLOW_LAN = "allow_lan_connection"
    private const val KEY_APP_AUTO_START = "app_auto_start"

    fun isMuxEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_MUX_ENABLED, false)
    fun setMuxEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_MUX_ENABLED, enabled).apply()

    fun getMuxTcpConcurrency(context: Context): Int = getPrefs(context).getInt(KEY_MUX_TCP, 8)
    fun setMuxTcpConcurrency(context: Context, value: Int) = getPrefs(context).edit().putInt(KEY_MUX_TCP, value).apply()

    fun getMuxUdpConcurrency(context: Context): Int = getPrefs(context).getInt(KEY_MUX_UDP, 8)
    fun setMuxUdpConcurrency(context: Context, value: Int) = getPrefs(context).edit().putInt(KEY_MUX_UDP, value).apply()

    fun getMuxQuicMode(context: Context): String = getPrefs(context).getString(KEY_MUX_QUIC, "reject") ?: "reject"
    fun setMuxQuicMode(context: Context, value: String) = getPrefs(context).edit().putString(KEY_MUX_QUIC, value).apply()

    fun getPreferredIpType(context: Context): String = getPrefs(context).getString(KEY_PREFERRED_IP, "auto") ?: "auto"
    fun setPreferredIpType(context: Context, type: String) = getPrefs(context).edit().putString(KEY_PREFERRED_IP, type).apply()

    fun isAllowLanEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_ALLOW_LAN, false)
    fun setAllowLanEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_ALLOW_LAN, enabled).apply()
    
    fun isAppAutoStartEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_APP_AUTO_START, false)
    fun setAppAutoStartEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_APP_AUTO_START, enabled).apply()

    fun isAutoConnectEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_CONNECT, false)
    }

    fun setAutoConnectEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_CONNECT, enabled).apply()
    }

    fun isAutoConnectWifiEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_CONNECT_WIFI, false)
    }

    fun setAutoConnectWifiEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_CONNECT_WIFI, enabled).apply()
    }

    fun isAutoConnectMobileEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_CONNECT_MOBILE, false)
    }

    fun setAutoConnectMobileEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_CONNECT_MOBILE, enabled).apply()
    }

    fun isFragmentationEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_FRAGMENTATION_ENABLED, false)
    }

    fun setFragmentationEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_FRAGMENTATION_ENABLED, enabled).apply()
    }
    
    fun getFragmentationMode(context: Context): String {
        return getPrefs(context).getString(KEY_FRAGMENTATION_MODE, "balanced") ?: "balanced"
    }
    
    fun setFragmentationMode(context: Context, mode: String) {
        val editor = getPrefs(context).edit()
        editor.putString(KEY_FRAGMENTATION_MODE, mode)
        
        when (mode) {
            "light" -> {
                editor.putString(KEY_FRAGMENT_PACKETS, "1-1")
                editor.putString(KEY_FRAGMENT_LENGTH, "500-1000")
                editor.putString(KEY_FRAGMENT_INTERVAL, "1-2")
            }
            "balanced" -> {
                editor.putString(KEY_FRAGMENT_PACKETS, "1-2")
                editor.putString(KEY_FRAGMENT_LENGTH, "100-200")
                editor.putString(KEY_FRAGMENT_INTERVAL, "10-20")
            }
            "aggressive" -> {
                editor.putString(KEY_FRAGMENT_PACKETS, "2-5")
                editor.putString(KEY_FRAGMENT_LENGTH, "40-80")
                editor.putString(KEY_FRAGMENT_INTERVAL, "30-50")
            }
            // "custom" -> Do nothing, preserve existing custom values
        }
        editor.apply()
    }
    
    fun getFragmentPackets(context: Context): String {
        return getPrefs(context).getString(KEY_FRAGMENT_PACKETS, "1-2") ?: "1-2"
    }

    fun setFragmentPackets(context: Context, value: String) {
        getPrefs(context).edit().putString(KEY_FRAGMENT_PACKETS, value).apply()
    }

    fun getFragmentLength(context: Context): String {
        return getPrefs(context).getString(KEY_FRAGMENT_LENGTH, "100-200") ?: "100-200"
    }

    fun setFragmentLength(context: Context, value: String) {
        getPrefs(context).edit().putString(KEY_FRAGMENT_LENGTH, value).apply()
    }

    fun getFragmentInterval(context: Context): String {
        return getPrefs(context).getString(KEY_FRAGMENT_INTERVAL, "10-20") ?: "10-20"
    }

    fun setFragmentInterval(context: Context, value: String) {
        getPrefs(context).edit().putString(KEY_FRAGMENT_INTERVAL, value).apply()
    }

    fun isKillSwitchEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_KILL_SWITCH, false)
    }

    fun setKillSwitchEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_KILL_SWITCH, enabled).apply()
    }

    private const val KEY_FALLBACK_ENABLED = "fallback_enabled"
    fun isFallbackEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_FALLBACK_ENABLED, true)
    fun setFallbackEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_FALLBACK_ENABLED, enabled).apply()

    private const val KEY_DOUBLE_TUNNEL_ENABLED = "double_tunnel_enabled"
    private const val KEY_DOUBLE_TUNNEL_SERVER_ID = "double_tunnel_server_id"
    fun isDoubleTunnelEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_DOUBLE_TUNNEL_ENABLED, false)
    fun setDoubleTunnelEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_DOUBLE_TUNNEL_ENABLED, enabled).apply()
    fun getDoubleTunnelServerId(context: Context): String = getPrefs(context).getString(KEY_DOUBLE_TUNNEL_SERVER_ID, "") ?: ""
    fun setDoubleTunnelServerId(context: Context, id: String) = getPrefs(context).edit().putString(KEY_DOUBLE_TUNNEL_SERVER_ID, id).apply()

    private const val KEY_NOISE_MODE_ENABLED = "noise_mode_enabled"
    private const val KEY_NOISE_MODE_INTENSITY = "noise_mode_intensity"
    fun isNoiseModeEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_NOISE_MODE_ENABLED, false)
    fun setNoiseModeEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_NOISE_MODE_ENABLED, enabled).apply()
    fun getNoiseModeIntensity(context: Context): String = getPrefs(context).getString(KEY_NOISE_MODE_INTENSITY, "low") ?: "low"
    fun setNoiseModeIntensity(context: Context, intensity: String) = getPrefs(context).edit().putString(KEY_NOISE_MODE_INTENSITY, intensity).apply()
    
    fun getDnsServer(context: Context): String {
        return getPrefs(context).getString(KEY_DNS_SERVER, "1.1.1.1") ?: "1.1.1.1"
    }

    fun setDnsServer(context: Context, dns: String) {
        getPrefs(context).edit().putString(KEY_DNS_SERVER, dns).apply()
    }
    
    fun getThemeColor(context: Context): Long {
        // Default Red: 0xFFE53935
        return getPrefs(context).getLong(KEY_THEME_COLOR, 0xFFE53935)
    }

    fun setThemeColor(context: Context, color: Long) {
        getPrefs(context).edit().putLong(KEY_THEME_COLOR, color).apply()
    }

    fun getThemeIndex(context: Context): Int {
        return getPrefs(context).getInt(KEY_THEME_INDEX, 0) // Default to CLASSIC_RED (index 0)
    }

    fun setThemeIndex(context: Context, index: Int) {
        getPrefs(context).edit().putInt(KEY_THEME_INDEX, index).apply()
    }

    fun isSecretThemeUnlocked(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SECRET_THEME_UNLOCKED, false)
    }

    fun unlockSecretTheme(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_SECRET_THEME_UNLOCKED, true).apply()
    }
    
    // Miner Game
    private const val KEY_MINER_SCORE = "miner_score"
    fun getMinerScore(context: Context): Long = getPrefs(context).getLong(KEY_MINER_SCORE, 0L)
    fun setMinerScore(context: Context, score: Long) = getPrefs(context).edit().putLong(KEY_MINER_SCORE, score).apply()

    fun getSelectedApps(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_SELECTED_APPS, emptySet()) ?: emptySet()
    }

    fun setSelectedApps(context: Context, packages: Set<String>) {
        getPrefs(context).edit().putStringSet(KEY_SELECTED_APPS, packages).apply()
    }
    

    
    fun areGeoAssetsInstalled(context: Context): Boolean {
        // Simple check if preference is set, or better, check file existence (do outside)
        // This pref just marks if we attempted install
        return getPrefs(context).getBoolean(KEY_GEO_ASSETS_INSTALLED, false)
    }

    fun setGeoAssetsInstalled(context: Context, installed: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_GEO_ASSETS_INSTALLED, installed).apply()
    }

    // ==================== GPS Spoof ====================
    private const val KEY_GEO_LAT = "geo_spoof_lat"
    private const val KEY_GEO_LON = "geo_spoof_lon"
    private const val KEY_GEO_MOVE = "geo_spoof_move"
    private const val KEY_GEO_SPEED = "geo_spoof_speed"
    private const val KEY_GEO_BEARING = "geo_spoof_bearing"

    fun getGeoLat(context: Context): Double = java.lang.Double.longBitsToDouble(getPrefs(context).getLong(KEY_GEO_LAT, java.lang.Double.doubleToLongBits(48.8566)))
    fun getGeoLon(context: Context): Double = java.lang.Double.longBitsToDouble(getPrefs(context).getLong(KEY_GEO_LON, java.lang.Double.doubleToLongBits(2.3522)))
    fun isGeoMovementEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_GEO_MOVE, false)
    fun getGeoSpeed(context: Context): Float = getPrefs(context).getFloat(KEY_GEO_SPEED, 1.4f)
    fun getGeoBearing(context: Context): Float = getPrefs(context).getFloat(KEY_GEO_BEARING, 0f)

    fun setGeoCoords(context: Context, lat: Double, lon: Double) {
        getPrefs(context).edit()
            .putLong(KEY_GEO_LAT, java.lang.Double.doubleToLongBits(lat))
            .putLong(KEY_GEO_LON, java.lang.Double.doubleToLongBits(lon))
            .apply()
    }

    fun setGeoMovement(context: Context, enabled: Boolean, speedMs: Float, bearing: Float) {
        getPrefs(context).edit()
            .putBoolean(KEY_GEO_MOVE, enabled)
            .putFloat(KEY_GEO_SPEED, speedMs)
            .putFloat(KEY_GEO_BEARING, bearing)
            .apply()
    }

    fun resetSettings(context: Context) {
        val editor = getPrefs(context).edit()
        editor.clear()
        getPrefs(context).edit().putInt(KEY_THEME_INDEX, 0).apply() // Reset to CLASSIC_RED
        editor.apply()
    }

    // Firewall — apps fully blocked from internet (VPN + direct)
    private const val KEY_FIREWALL_BLOCKED = "firewall_blocked_apps"

    fun getFirewallBlockedApps(context: Context): Set<String> =
        getPrefs(context).getStringSet(KEY_FIREWALL_BLOCKED, emptySet()) ?: emptySet()

    fun setFirewallBlockedApps(context: Context, packages: Set<String>) =
        getPrefs(context).edit().putStringSet(KEY_FIREWALL_BLOCKED, packages).apply()

    fun addFirewallBlockedApp(context: Context, pkg: String) {
        val current = getFirewallBlockedApps(context).toMutableSet()
        current.add(pkg)
        setFirewallBlockedApps(context, current)
    }

    fun removeFirewallBlockedApp(context: Context, pkg: String) {
        val current = getFirewallBlockedApps(context).toMutableSet()
        current.remove(pkg)
        setFirewallBlockedApps(context, current)
    }

    // Blocked domains (xray routing rule — blackhole outbound)
    private const val KEY_BLOCKED_DOMAINS = "blocked_domains_list"

    fun getBlockedDomains(context: Context): Set<String> =
        getPrefs(context).getStringSet(KEY_BLOCKED_DOMAINS, emptySet()) ?: emptySet()

    fun setBlockedDomains(context: Context, domains: Set<String>) =
        getPrefs(context).edit().putStringSet(KEY_BLOCKED_DOMAINS, domains).apply()

    // ==================== v2.4.0 ====================

    // Biometric / PIN lock
    private const val KEY_BIOMETRIC_LOCK = "biometric_lock_enabled"
    fun isBiometricLockEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_BIOMETRIC_LOCK, false)
    fun setBiometricLockEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_BIOMETRIC_LOCK, enabled).apply()

    // ==================== v2.4.0: Network Boost ====================

    // Dual Network: keep cellular active while on WiFi
    private const val KEY_DUAL_NETWORK = "dual_network_enabled"
    fun isDualNetworkEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_DUAL_NETWORK, false)
    fun setDualNetworkEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_DUAL_NETWORK, enabled).apply()

    // Smart Port Selection: auto-probe ports to bypass restrictive networks
    private const val KEY_SMART_PORT = "smart_port_enabled"
    fun isSmartPortEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_SMART_PORT, false)
    fun setSmartPortEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_SMART_PORT, enabled).apply()

    // Port Hopping: switch to a new port every N minutes (for servers supporting port ranges)
    private const val KEY_PORT_HOPPING = "port_hopping_enabled"
    private const val KEY_PORT_HOPPING_RANGE = "port_hopping_range" // e.g. "10000-20000"
    private const val KEY_PORT_HOPPING_INTERVAL = "port_hopping_interval_min" // minutes
    fun isPortHoppingEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_PORT_HOPPING, false)
    fun setPortHoppingEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_PORT_HOPPING, enabled).apply()
    fun getPortHoppingRange(context: Context): String = getPrefs(context).getString(KEY_PORT_HOPPING_RANGE, "10000-20000") ?: "10000-20000"
    fun setPortHoppingRange(context: Context, range: String) = getPrefs(context).edit().putString(KEY_PORT_HOPPING_RANGE, range).apply()
    fun getPortHoppingInterval(context: Context): Int = getPrefs(context).getInt(KEY_PORT_HOPPING_INTERVAL, 5)
    fun setPortHoppingInterval(context: Context, minutes: Int) = getPrefs(context).edit().putInt(KEY_PORT_HOPPING_INTERVAL, minutes).apply()

    // HTTP Camouflage: disguise VPN traffic as normal HTTPS browsing
    // Uses xray httpupgrade / websocket transport with a fake Host header
    private const val KEY_HTTP_CAMOUFLAGE = "http_camouflage_enabled"
    private const val KEY_HTTP_CAMOUFLAGE_HOST = "http_camouflage_host"
    fun isHttpCamouflageEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_HTTP_CAMOUFLAGE, false)
    fun setHttpCamouflageEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_HTTP_CAMOUFLAGE, enabled).apply()
    fun getHttpCamouflageHost(context: Context): String = getPrefs(context).getString(KEY_HTTP_CAMOUFLAGE_HOST, "www.google.com") ?: "www.google.com"
    fun setHttpCamouflageHost(context: Context, host: String) = getPrefs(context).edit().putString(KEY_HTTP_CAMOUFLAGE_HOST, host).apply()

    // VPN Schedule
    private const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
    private const val KEY_SCHEDULE_CONNECT_H = "schedule_connect_h"
    private const val KEY_SCHEDULE_CONNECT_M = "schedule_connect_m"
    private const val KEY_SCHEDULE_DISCONNECT_H = "schedule_disconnect_h"
    private const val KEY_SCHEDULE_DISCONNECT_M = "schedule_disconnect_m"

    fun isScheduleEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_SCHEDULE_ENABLED, false)
    fun setScheduleEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_SCHEDULE_ENABLED, enabled).apply()
    fun getScheduleConnectHour(context: Context): Int = getPrefs(context).getInt(KEY_SCHEDULE_CONNECT_H, 9)
    fun setScheduleConnectHour(context: Context, h: Int) = getPrefs(context).edit().putInt(KEY_SCHEDULE_CONNECT_H, h).apply()
    fun getScheduleConnectMin(context: Context): Int = getPrefs(context).getInt(KEY_SCHEDULE_CONNECT_M, 0)
    fun setScheduleConnectMin(context: Context, m: Int) = getPrefs(context).edit().putInt(KEY_SCHEDULE_CONNECT_M, m).apply()
    fun getScheduleDisconnectHour(context: Context): Int = getPrefs(context).getInt(KEY_SCHEDULE_DISCONNECT_H, 18)
    fun setScheduleDisconnectHour(context: Context, h: Int) = getPrefs(context).edit().putInt(KEY_SCHEDULE_DISCONNECT_H, h).apply()
    fun getScheduleDisconnectMin(context: Context): Int = getPrefs(context).getInt(KEY_SCHEDULE_DISCONNECT_M, 0)
    fun setScheduleDisconnectMin(context: Context, m: Int) = getPrefs(context).edit().putInt(KEY_SCHEDULE_DISCONNECT_M, m).apply()
}
