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
        com.carnelia.vpn.utils.AppLogger.log("Repository: Loaded raw JSON: $json")
        return try {
            // Use Array to avoid R8/ProGuard TypeToken issues with generics
            val array = gson.fromJson(json, Array<VpnServerConfig>::class.java)
            val list = array?.toList() ?: emptyList()
            
            if (array == null) {
                com.carnelia.vpn.utils.AppLogger.log("Repository: Deserialized list is NULL")
            } else {
                com.carnelia.vpn.utils.AppLogger.log("Repository: Deserialized list size: ${list.size}")
                list.forEachIndexed { index, config ->
                    com.carnelia.vpn.utils.AppLogger.log("Repository: Item $index: id=${config.id}, protocol=${config.protocol}, host=${config.host}")
                }
            }

            // Filter is relaxed to check for essential connection data only.
            // If Protocol is null, we might default it or skip.
            // But we must assume if Gson fails to load protocol, it is broken data.
            // However, debugging shows R8 sometimes causes issues here.
            val result = list?.filter { 
               it != null && !it.id.isNullOrBlank()
            } ?: emptyList()
            if (result.isEmpty() && list != null && list.isNotEmpty()) {
                 com.carnelia.vpn.utils.AppLogger.log("Repository: WARNING - ALL items were filtered out! Check R8 obfuscation or data integrity.")
            }
            result
        } catch (e: Exception) {
            // Log error but don't clear data immediately to allow recovery if it's just a read error
             com.carnelia.vpn.utils.AppLogger.error("Repository: Error loading servers", e)
            emptyList()
        }
    }

    fun addServer(config: VpnServerConfig) {
        val current = getServers().toMutableList()
        // Allow multiple configs for same host (e.g. different keys/users)
        // Only check for exact ID duplication (which implies same object instance or explicit update)
        val exists = current.any { it.id == config.id }
        
        if (!exists) {
            current.add(0, config) // Add to top
            saveServers(current)
            com.carnelia.vpn.utils.AppLogger.log("Repository: Saved ${current.size} servers")
        } else {
            // If ID exists, maybe update it? For now, just log.
            com.carnelia.vpn.utils.AppLogger.log("Repository: Server with ID ${config.id} already exists")
        }
    }

    fun updateServer(config: VpnServerConfig) {
        val current = getServers().toMutableList()
        val index = current.indexOfFirst { it.id == config.id }
        if (index != -1) {
            current[index] = config
            saveServers(current)
            com.carnelia.vpn.utils.AppLogger.log("Repository: Updated server ${config.id}")
        } else {
            com.carnelia.vpn.utils.AppLogger.log("Repository: Failed to update, server ${config.id} not found")
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
