package com.carnelia.vpn.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.carnelia.vpn.core.VpnServerConfig
import com.carnelia.vpn.core.VpnProtocol

/**
 * VPN Configuration Repository
 * Stores and manages saved VPN servers
 */
class VpnConfigRepository(private val dataStore: DataStore<Preferences>) {
    
    companion object {
        private val SERVERS_KEY = stringPreferencesKey("vpn_servers")
        private val LAST_SERVER_KEY = stringPreferencesKey("last_server_id")
        private val AUTO_CONNECT_KEY = booleanPreferencesKey("auto_connect")
    }
    
    /**
     * Get all saved servers
     */
    val allServers: Flow<List<VpnServerConfig>> = dataStore.data.map { preferences ->
        val serversJson = preferences[SERVERS_KEY] ?: return@map emptyList()
        parseServersJson(serversJson)
    }
    
    /**
     * Save server configuration
     */
    suspend fun saveServer(server: VpnServerConfig) {
        dataStore.edit { preferences ->
            val currentServers = parseServersJson(preferences[SERVERS_KEY] ?: "[]").toMutableList()
            currentServers.removeIf { it.id == server.id }
            currentServers.add(server)
            preferences[SERVERS_KEY] = serversToJson(currentServers)
        }
    }
    
    /**
     * Get server by ID
     */
    suspend fun getServer(serverId: String): VpnServerConfig? {
        return try {
            val servers = parseServersJson(dataStore.data.map { it[SERVERS_KEY] ?: "[]" }.toString())
            servers.find { it.id == serverId }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Delete server
     */
    suspend fun deleteServer(serverId: String) {
        dataStore.edit { preferences ->
            val currentServers = parseServersJson(preferences[SERVERS_KEY] ?: "[]").toMutableList()
            currentServers.removeIf { it.id == serverId }
            preferences[SERVERS_KEY] = serversToJson(currentServers)
        }
    }
    
    /**
     * Set last used server
     */
    suspend fun setLastServer(serverId: String) {
        dataStore.edit { preferences ->
            preferences[LAST_SERVER_KEY] = serverId
        }
    }
    
    /**
     * Get last used server ID
     */
    val lastServerId: Flow<String?> = dataStore.data.map { preferences ->
        preferences[LAST_SERVER_KEY]
    }
    
    /**
     * Set auto-connect preference
     */
    suspend fun setAutoConnect(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AUTO_CONNECT_KEY] = enabled
        }
    }
    
    /**
     * Get auto-connect preference
     */
    val autoConnect: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[AUTO_CONNECT_KEY] ?: false
    }
    
    // Private helpers
    
    private fun parseServersJson(json: String): List<VpnServerConfig> {
        // In production: use proper JSON library (Gson, kotlinx.serialization)
        return emptyList() // Placeholder
    }
    
    private fun serversToJson(servers: List<VpnServerConfig>): String {
        // In production: use proper JSON library
        return "[]" // Placeholder
    }
}
