package com.carnelia.vpn.data

import android.content.Context
import android.content.SharedPreferences
import com.carnelia.vpn.core.VpnServerConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Repository for managing VPN servers
 * Persists data to SharedPreferences
 */
class ServerRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("vpn_servers", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val SERVERS_KEY = "saved_servers"
    private val LAST_USED_KEY = "last_used_server_id"

    fun getServers(): List<VpnServerConfig> {
        val json = prefs.getString(SERVERS_KEY, "[]")
        return try {
            val type = object : TypeToken<List<VpnServerConfig>>() {}.type
            val list: List<VpnServerConfig>? = gson.fromJson(json, type)
            // Filter out corrupted data (Gson can create objects with null fields even if Kotlin says non-null)
            list?.filter { 
                it != null && 
                it.id != null && 
                it.name != null && 
                it.host != null && 
                it.protocol != null 
            } ?: emptyList()
        } catch (e: Exception) {
            // If data is corrupted, clear it to prevent persistent crashes
            prefs.edit().remove(SERVERS_KEY).apply()
            emptyList()
        }
    }

    fun addServer(config: VpnServerConfig) {
        val current = getServers().toMutableList()
        // Avoid duplicates by ID or Host+Port combination
        val exists = current.any { 
            it.id == config.id || (it.host == config.host && it.port == config.port)
        }
        
        if (!exists) {
            current.add(0, config) // Add to top
            saveServers(current)
            com.carnelia.vpn.utils.AppLogger.log("Repository: Saved ${current.size} servers")
        } else {
            com.carnelia.vpn.utils.AppLogger.log("Repository: Server already exists or duplicate")
        }
    }

    fun removeServer(id: String) {
        val current = getServers().toMutableList()
        current.removeAll { it.id == id }
        saveServers(current)
    }

    fun saveServers(servers: List<VpnServerConfig>) {
        val json = gson.toJson(servers)
        prefs.edit().putString(SERVERS_KEY, json).commit() // Use commit for synchronous save
    }
    
    fun getLastUsedServer(): VpnServerConfig? {
        val id = prefs.getString(LAST_USED_KEY, null) ?: return null
        return getServers().find { it.id == id }
    }
    
    fun setLastUsedServer(server: VpnServerConfig) {
        prefs.edit().putString(LAST_USED_KEY, server.id).apply()
    }
}
