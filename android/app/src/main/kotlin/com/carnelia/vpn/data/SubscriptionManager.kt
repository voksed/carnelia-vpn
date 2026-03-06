package com.carnelia.vpn.data

import android.content.Context
import android.util.Base64
import com.carnelia.vpn.core.VpnServerConfig
import com.carnelia.vpn.utils.AppLogger
import com.carnelia.vpn.utils.ConfigParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.UUID

class SubscriptionManager(private val context: Context) {

    private val repository = ServerRepository(context)
    private val client = OkHttpClient()
    
    private val PREFS = "vpn_subs"
    private val KEY_SUBS = "saved_subscriptions"
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val gson = com.google.gson.Gson()

    fun getSubscriptions(): List<Subscription> {
        val json = prefs.getString(KEY_SUBS, "[]")
        return try {
            val list = gson.fromJson(json, Array<Subscription>::class.java)
            list?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addSubscription(name: String, url: String) {
        val subs = getSubscriptions().toMutableList()
        if (subs.any { it.url == url }) return
        
        val newSub = Subscription(
            id = UUID.randomUUID().toString(),
            name = name,
            url = url,
            lastUpdated = 0,
            serverCount = 0
        )
        subs.add(newSub)
        saveSubscriptions(subs)
    }
    
    fun removeSubscription(id: String) {
        val subs = getSubscriptions().toMutableList()
        subs.removeAll { it.id == id }
        saveSubscriptions(subs)
        repository.removeSubscriptionServers(id)
    }

    private fun saveSubscriptions(list: List<Subscription>) {
        prefs.edit().putString(KEY_SUBS, gson.toJson(list)).apply()
    }

    suspend fun updateSubscription(id: String): Boolean {
        val sub = getSubscriptions().find { it.id == id } ?: return false
        
        return try {
            val content = withContext(Dispatchers.IO) {
                fetchUrl(sub.url)
            }
            if (content.isBlank()) return false
            
            val decoded = tryDecode(content)
            
            val configs = parseConfigs(decoded, sub.id)
            if (configs.isNotEmpty()) {
                // We use remove+add because we want to sync the state exactly with the remote list
                repository.removeSubscriptionServers(sub.id)
                repository.addOrUpdateServers(configs)
                
                val updatedSub = sub.copy(lastUpdated = System.currentTimeMillis(), serverCount = configs.size)
                val all = getSubscriptions().toMutableList()
                val idx = all.indexOfFirst { it.id == id }
                if (idx != -1) {
                    all[idx] = updatedSub
                    saveSubscriptions(all)
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            AppLogger.error("SubManager: Failed to update ${sub.name}", e)
            false
        }
    }
    
    private fun fetchUrl(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            return response.body?.string() ?: ""
        }
    }
    
    private fun tryDecode(content: String): String {
        return try {
            val trimmed = content.trim()
            // Heuristic: if no spaces and no :// and length > 24, might be base64
            if (!trimmed.contains(" ") && !trimmed.contains("\n") && !trimmed.contains("://") && trimmed.length > 20) {
                 String(Base64.decode(trimmed, Base64.DEFAULT), StandardCharsets.UTF_8)
            } else {
                content
            }
        } catch (e: Exception) {
            content
        }
    }

    private fun parseConfigs(content: String, subId: String): List<VpnServerConfig> {
        val list = mutableListOf<VpnServerConfig>()
        content.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("//")) {
                val config = ConfigParser.parse(trimmed)
                if (config != null) {
                    list.add(config.copy(subscriptionId = subId))
                }
            }
        }
        return list
    }
}