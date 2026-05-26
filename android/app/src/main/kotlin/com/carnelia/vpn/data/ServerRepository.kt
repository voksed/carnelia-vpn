package com.carnelia.vpn.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.carnelia.vpn.core.VpnServerConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Base64

/**
 * Repository for managing VPN servers
 * Persists data to EncryptedSharedPreferences (AES-256-GCM)
 */
class ServerRepository(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "vpn_servers_enc",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        com.carnelia.vpn.utils.AppLogger.error("ServerRepository: EncryptedSharedPreferences failed, falling back to plain", e)
        context.getSharedPreferences("vpn_servers", Context.MODE_PRIVATE)
    }
    private val gson = Gson()
    private val SERVERS_KEY = "saved_servers"
    private val LAST_USED_KEY = "last_used_server_id"
    private var hasLoggedInitialSnapshot = false

    init {
        migrateLegacyPrefs(context)
        cleanBrokenServers()
    }

    /**
     * One-time migration: copy servers from old plain "vpn_servers" SharedPreferences
     * (used by app versions before encrypted storage was added) into the current store.
     * Runs only when the current store is empty AND legacy data exists.
     */
    private fun migrateLegacyPrefs(context: Context) {
        // Skip if current store already has data
        if (!prefs.getString(SERVERS_KEY, "[]").isNullOrEmpty()
            && prefs.getString(SERVERS_KEY, "[]") != "[]") return

        val legacy = context.getSharedPreferences("vpn_servers", Context.MODE_PRIVATE)
        val legacyJson = legacy.getString(SERVERS_KEY, null) ?: return
        if (legacyJson == "[]" || legacyJson.isBlank()) return

        com.carnelia.vpn.utils.AppLogger.log("ServerRepository: Migrating legacy servers from 'vpn_servers' → 'vpn_servers_enc'")
        try {
            // Validate that it's parseable JSON before migrating
            val array = gson.fromJson(legacyJson, Array<VpnServerConfig>::class.java)
            if (!array.isNullOrEmpty()) {
                prefs.edit().putString(SERVERS_KEY, legacyJson).apply()
                val lastUsed = legacy.getString(LAST_USED_KEY, null)
                if (!lastUsed.isNullOrBlank()) {
                    prefs.edit().putString(LAST_USED_KEY, lastUsed).apply()
                }
                com.carnelia.vpn.utils.AppLogger.log("ServerRepository: Migrated ${array.size} servers successfully")
            }
        } catch (e: Exception) {
            com.carnelia.vpn.utils.AppLogger.error("ServerRepository: Legacy migration failed", e)
        }
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
                    !isValidRealityPublicKey(pbk)
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

    fun getServers(): List<VpnServerConfig> {
        val json = prefs.getString(SERVERS_KEY, "[]")
        return try {
            // Use Array to avoid R8/ProGuard TypeToken issues with generics
            val array = gson.fromJson(json, Array<VpnServerConfig>::class.java)
            val list = array?.toList() ?: emptyList()
            
            if (array == null) {
                com.carnelia.vpn.utils.AppLogger.log("Repository: Deserialized list is NULL")
            } else if (!hasLoggedInitialSnapshot) {
                hasLoggedInitialSnapshot = true
                com.carnelia.vpn.utils.AppLogger.log("Repository: Loaded ${list.size} servers")
                list.take(3).forEachIndexed { index, config ->
                    com.carnelia.vpn.utils.AppLogger.log("Repository: Item $index: id=${config.id}, protocol=${config.protocol}, host=${config.host}")
                }
            }

            // Filter is relaxed to check for essential connection data only.
            // If Protocol is null, we might default it or skip.
            // But we must assume if Gson fails to load protocol, it is broken data.
            // However, debugging shows R8 sometimes causes issues here.
            val result = list.filter { it.id.isNotBlank() }
            if (result.isEmpty() && list.isNotEmpty()) {
                 com.carnelia.vpn.utils.AppLogger.log("Repository: WARNING - ALL items were filtered out! Check R8 obfuscation or data integrity.")
            }
            val deduped = deduplicateServers(result)
            if (deduped.size != result.size) {
                com.carnelia.vpn.utils.AppLogger.log("Repository: Removed ${result.size - deduped.size} duplicate server entries")
                saveServers(deduped)
            }
            deduped
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
        val semanticIndex = current.indexOfFirst { isSemanticallySameServer(it, config) }
        
        if (index == -1) {
            if (semanticIndex != -1) {
                current[semanticIndex] = config.copy(id = current[semanticIndex].id)
            } else {
                current.add(0, config) // Add to top
            }
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
            val semanticIndex = current.indexOfFirst { isSemanticallySameServer(it, config) }
            if (index == -1) {
                if (semanticIndex == -1) {
                    current.add(config)
                    changed = true
                } else if (current[semanticIndex] != config) {
                    current[semanticIndex] = config.copy(id = current[semanticIndex].id)
                    changed = true
                }
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

    private fun deduplicateServers(servers: List<VpnServerConfig>): List<VpnServerConfig> {
        val byId = LinkedHashSet<String>()
        val bySemantic = LinkedHashSet<String>()
        val deduped = mutableListOf<VpnServerConfig>()

        for (server in servers) {
            if (!byId.add(server.id)) continue
            val semanticKey = semanticServerKey(server)
            if (semanticKey != null && !bySemantic.add(semanticKey)) continue
            deduped.add(server)
        }

        return deduped
    }

    private fun isSemanticallySameServer(a: VpnServerConfig, b: VpnServerConfig): Boolean {
        val aKey = semanticServerKey(a) ?: return false
        val bKey = semanticServerKey(b) ?: return false
        return aKey == bKey
    }

    private fun semanticServerKey(config: VpnServerConfig): String? {
        if (config.protocol != com.carnelia.vpn.core.VpnProtocol.VLESS) return null

        val security = config.config["security"]?.trim()?.lowercase() ?: ""
        if (security != "reality") return null

        fun pick(vararg keys: String): String {
            for (key in keys) {
                val value = config.config[key]?.trim()
                if (!value.isNullOrEmpty()) return value.lowercase()
            }
            return ""
        }

        val host = config.host.trim().lowercase()
        val uuid = pick("uuid", "id")
        val pbk = pick("pbk", "publicKey")
        val sid = pick("sid", "shortId")
        val sni = pick("sni", "serverName")

        return listOf(
            config.protocol.name,
            host,
            config.port.toString(),
            security,
            uuid,
            pbk,
            sid,
            sni
        ).joinToString("|")
    }

    private fun isValidRealityPublicKey(raw: String): Boolean {
        val pbk = raw.trim()
        if (pbk.isBlank()) return false
        if (pbk.contains(':') || pbk.contains(' ')) return false
        val lower = pbk.lowercase()
        if (lower.startsWith("hash") || lower.startsWith("placeholder") || lower.startsWith("example")) return false
        if (!pbk.matches(Regex("^[A-Za-z0-9_-]{30,120}$"))) return false

        // REALITY key is base64url for 32 bytes (commonly 43 chars without '=' padding).
        return try {
            var normalized = pbk.replace('-', '+').replace('_', '/')
            val padding = normalized.length % 4
            if (padding != 0) {
                normalized += "=".repeat(4 - padding)
            }
            Base64.getDecoder().decode(normalized).size == 32
        } catch (_: Exception) {
            false
        }
    }
}
