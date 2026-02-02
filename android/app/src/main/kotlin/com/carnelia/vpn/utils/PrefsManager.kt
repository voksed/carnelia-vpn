package com.carnelia.vpn.utils

import android.content.Context
import android.content.SharedPreferences

object PrefsManager {
    private const val PREFS_NAME = "carnelia_prefs"
    private const val KEY_SPLIT_TUNNELING = "split_tunneling_enabled"
    private const val KEY_SPLIT_TUNNEL_MODE = "split_tunneling_mode" // "allow" or "disallow"
    private const val KEY_SELECTED_APPS = "selected_apps_packages"
    private const val KEY_BYPASS_RU = "bypass_ru_enabled"
    private const val KEY_AUTO_CONNECT = "auto_connect_enabled"
    private const val KEY_TOR_ENABLED = "tor_enabled"
    private const val KEY_FRAGMENTATION_ENABLED = "frag_enabled"
    private const val KEY_FRAGMENT_PACKETS = "frag_packets"
    private const val KEY_FRAGMENT_LENGTH = "frag_length"
    private const val KEY_FRAGMENT_INTERVAL = "frag_interval"
    private const val KEY_KILL_SWITCH = "kill_switch_enabled"
    private const val KEY_DNS_SERVER = "dns_server_ip"
    private const val KEY_THEME_COLOR = "theme_accent_color" // e.g. 0xFFE53935

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

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

    fun isTorEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_TOR_ENABLED, false)
    }

    fun setTorEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_TOR_ENABLED, enabled).apply()
    }

    // Fragmentation Settings
    fun isFragmentationEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_FRAGMENTATION_ENABLED, true) 
    }

    fun setFragmentationEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_FRAGMENTATION_ENABLED, enabled).apply()
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

    fun isAutoConnectEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_CONNECT, false)
    }

    fun setAutoConnectEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_CONNECT, enabled).apply()
    }

    fun getSelectedApps(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_SELECTED_APPS, emptySet()) ?: emptySet()
    }

    fun setSelectedApps(context: Context, packages: Set<String>) {
        getPrefs(context).edit().putStringSet(KEY_SELECTED_APPS, packages).apply()
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
        
        getPrefs(context).edit().putLong(KEY_THEME_COLOR, 0xFFE53935).apply() // Reset color
        
        editor.apply()
    }
}
