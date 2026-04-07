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
    private val DEFAULT_ADDED_KEY = "default_server_added"
    private val DEFAULT_SERVER_VERSION_KEY = "default_server_version"
    private val CURRENT_DEFAULT_VERSION = 2

    init {
        cleanBrokenServers()
        checkAndAddDefaultServer()
    }

    /**
     * Auto-removes VLESS REALITY servers saved with invalid/placeholder public keys
     * (e.g. "Hash32:", "example", empty strings) that will always fail to connect.
     */
    private fun cleanBrokenServers() {
        val servers = getServers().toMutableList()
        val before = servers.size
        servers.removeAll { config ->
            val isBroken = config.protocol == com.carnelia.vpn.core.VpnProtocol.VLESS &&
                (config.config["security"] == "reality" || config.config["security"] == "reality") &&
                run {
                    val pbk = (config.config["pbk"] ?: config.config["publicKey"] ?: "").trim()
                    pbk.isBlank() || pbk.contains(':') || pbk.contains(' ') ||
                        pbk.length < 30 || pbk.lowercase().startsWith("hash") ||
                        pbk.lowercase().startsWith("placeholder") || pbk.lowercase().startsWith("example")
                }
            if (isBroken) {
                com.carnelia.vpn.utils.AppLogger.log("Repository: Removed broken VLESS REALITY server '${config.name}' (invalid publicKey).")
            }
            isBroken
        }
        if (servers.size != before) {
            saveServers(servers)
            // If last used server was removed, clear it
            val lastId = prefs.getString(LAST_USED_KEY, null)
            if (lastId != null && servers.none { it.id == lastId }) {
                prefs.edit().remove(LAST_USED_KEY).apply()
            }
        }
    }

    private fun checkAndAddDefaultServer() {
        val defaultKey = "ss://Y2hhY2hhMjAtaWV0Zi1wb2x5MTMwNTptbnhiQVJsSmYwcUp1eUlUc0JZa2lF@151.243.109.219:6932/?outline=1"
        val storedVersion = prefs.getInt(DEFAULT_SERVER_VERSION_KEY, 0)

        if (!prefs.getBoolean(DEFAULT_ADDED_KEY, false)) {
            // First launch — add default server
            val config = com.carnelia.vpn.utils.ConfigParser.parse(defaultKey)
            if (config != null) {
                val defaultServer = config.copy(
                    id = "default_server_id",
                    name = "Carnelia Free VPN",
                    country = "Default"
                )
                addServer(defaultServer)
                setLastUsedServer(defaultServer)
                com.carnelia.vpn.utils.AppLogger.log("Repository: Added default VPN server.")
            }
            prefs.edit()
                .putBoolean(DEFAULT_ADDED_KEY, true)
                .putInt(DEFAULT_SERVER_VERSION_KEY, CURRENT_DEFAULT_VERSION)
                .apply()
        } else if (storedVersion < CURRENT_DEFAULT_VERSION) {
            // Existing install — silently update the default server entry
            val config = com.carnelia.vpn.utils.ConfigParser.parse(defaultKey)
            if (config != null) {
                val defaultServer = config.copy(
                    id = "default_server_id",
                    name = "Carnelia Free VPN",
                    country = "Default"
                )
                addServer(defaultServer) // replaces by id
                com.carnelia.vpn.utils.AppLogger.log("Repository: Updated default VPN server to v$CURRENT_DEFAULT_VERSION.")
            }
            prefs.edit().putInt(DEFAULT_SERVER_VERSION_KEY, CURRENT_DEFAULT_VERSION).apply()
        }
    }

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
        val index = current.indexOfFirst { it.id == config.id }
        
        if (index == -1) {
            current.add(0, config) // Add to top
        } else {
             // Replace if exists
            current[index] = config
        }
        saveServers(current)
    }

    fun addOrUpdateServers(configs: List<VpnServerConfig>) {
        if (configs.isEmpty()) return
        val current = getServers().toMutableList()
        var changed = false
        
        configs.forEach { config ->
            val index = current.indexOfFirst { it.id == config.id }
            if (index == -1) {
                current.add(config)
                changed = true
            } else {
                if (current[index] != config) {
                    current[index] = config
                    changed = true
                }
            }
        }
        
        if (changed) {
            saveServers(current)
            com.carnelia.vpn.utils.AppLogger.log("Repository: Bulk updated ${configs.size} servers")
        }
    }

    fun removeSubscriptionServers(subId: String) {
        val current = getServers().toMutableList()
        val before = current.size
        current.removeAll { it.subscriptionId == subId }
        if (current.size != before) {
            saveServers(current)
            com.carnelia.vpn.utils.AppLogger.log("Repository: Removed ${before - current.size} subscription servers")
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
        val removed = current.removeAll { it.id == id }
        if (removed) {
            saveServers(current)
            // clear last used if it was the removed one
            val lastUsed = prefs.getString(LAST_USED_KEY, null)
            if (lastUsed == id) {
                prefs.edit().remove(LAST_USED_KEY).apply()
            }
        }
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
