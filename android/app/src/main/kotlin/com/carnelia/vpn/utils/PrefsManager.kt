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

    fun isTorEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_TOR_ENABLED, false)
    fun setTorEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_TOR_ENABLED, enabled).apply()

    fun getTorSocksPort(context: Context): String = getPrefs(context).getString(KEY_TOR_SOCKS_PORT, "9050") ?: "9050"
    fun setTorSocksPort(context: Context, port: String) = getPrefs(context).edit().putString(KEY_TOR_SOCKS_PORT, port).apply()

    fun getTorHttpPort(context: Context): String = getPrefs(context).getString(KEY_TOR_HTTP_PORT, "8118") ?: "8118"
    fun setTorHttpPort(context: Context, port: String) = getPrefs(context).edit().putString(KEY_TOR_HTTP_PORT, port).apply()

    fun isTorUseBridges(context: Context): Boolean = getPrefs(context).getBoolean(KEY_TOR_USE_BRIDGES, false)
    fun setTorUseBridges(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_TOR_USE_BRIDGES, enabled).apply()

    fun getTorBridgeType(context: Context): String = getPrefs(context).getString(KEY_TOR_BRIDGE_TYPE, "obfs4") ?: "obfs4"
    fun setTorBridgeType(context: Context, type: String) = getPrefs(context).edit().putString(KEY_TOR_BRIDGE_TYPE, type).apply()

    fun getCustomTorBridges(context: Context): String = getPrefs(context).getString(KEY_TOR_BRIDGES, "") ?: ""
    fun setCustomTorBridges(context: Context, bridges: String) = getPrefs(context).edit().putString(KEY_TOR_BRIDGES, bridges).apply()
    
    fun isI2pEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_I2P_ENABLED, false)
    fun setI2pEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_I2P_ENABLED, enabled).apply()

    fun isUseInternalTor(context: Context): Boolean = getPrefs(context).getBoolean(KEY_USE_INTERNAL_TOR, true)
    fun setUseInternalTor(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_USE_INTERNAL_TOR, enabled).apply()

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
        return getPrefs(context).getString(KEY_FRAGMENT_LENGTH, "500-1000") ?: "500-1000"
    }

    fun setFragmentLength(context: Context, value: String) {
        getPrefs(context).edit().putString(KEY_FRAGMENT_LENGTH, value).apply()
    }
    
    fun getFragmentInterval(context: Context): String {
        return getPrefs(context).getString(KEY_FRAGMENT_INTERVAL, "1-3") ?: "1-3"
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

    fun resetSettings(context: Context) {
        val editor = getPrefs(context).edit()
        editor.clear()
        // We might want to keep some things like server list (managed by ServerRepository)
        // This clears PREFS_NAME which only contains the settings keys defined above.
        // Re-apply defaults explicitly if needed, but clear() removes them so getters will return defaults.
        // However, for safety/clarity, we can just clear.
        
        // Defaults check:
        // Split Tunneling: false
        // Bypass RU: false
        // Auto Connect: false
        // Frag Enabled: true (WAIT, getter default is true)
        // Frag params: 1-2, 500-1000, 1-3
        // DNS: 8.8.8.8
        
        getPrefs(context).edit().putInt(KEY_THEME_INDEX, 0).apply() // Reset to CLASSIC_RED
        
        editor.apply()
    }
}
