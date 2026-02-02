package com.carnelia.vpn.utils

import android.net.Uri
import android.util.Base64
import com.carnelia.vpn.core.VpnServerConfig
import com.carnelia.vpn.core.VpnProtocol
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Universal VPN Config Parser
 * Supports: Outline (ss), Shadowsocks, VLESS, VMess, Trojan
 */
object ConfigParser {

    private val gson = Gson()

    fun parse(input: String): VpnServerConfig? {
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("ss://") -> parseShadowsocks(trimmed)
            trimmed.startsWith("vless://") -> parseVless(trimmed)
            trimmed.startsWith("vmess://") -> parseVmess(trimmed)
            trimmed.startsWith("trojan://") -> parseTrojan(trimmed)
            else -> null
        }
    }

    private fun parseShadowsocks(url: String): VpnServerConfig? {
        try {
            var cleanUrl = url.substring(5)
            val tagIndex = cleanUrl.indexOf("#")
            var name = "Shadowsocks Server"
            if (tagIndex != -1) {
                try {
                    name = URLDecoder.decode(cleanUrl.substring(tagIndex + 1), StandardCharsets.UTF_8.toString())
                } catch (e: Exception) {
                    name = cleanUrl.substring(tagIndex + 1)
                }
                cleanUrl = cleanUrl.substring(0, tagIndex)
            }

            // Outline usually has /?outline=1
            val isOutline = url.contains("outline=1")
            
            val userPart: String
            val hostPart: String
            
            if (cleanUrl.contains("@")) {
                val parts = cleanUrl.split("@")
                userPart = parts[0]
                hostPart = parts[1]
            } else {
                // Try base64 decoding the whole thing (SIP002)
                val decoded = String(Base64.decode(cleanUrl, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
                if (decoded.contains("@")) {
                     val parts = decoded.split("@")
                     userPart = parts[0]
                     hostPart = parts[1]
                } else {
                    return null
                }
            }

            val methodPass = if (userPart.contains(":")) {
                userPart.split(":", limit = 2)
            } else {
                 val decodedAuth = String(Base64.decode(userPart, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
                 decodedAuth.split(":", limit = 2)
            }

            if (methodPass.size != 2) return null
            val method = methodPass[0]
            val password = methodPass[1]

            val hostPort = hostPart.split("/")[0].split(":")
            if (hostPort.size != 2) return null
            val host = hostPort[0]
            val port = hostPort[1].toIntOrNull() ?: return null

            return VpnServerConfig(
                id = UUID.randomUUID().toString(),
                name = name,
                protocol = if (isOutline) VpnProtocol.OUTLINE else VpnProtocol.SHADOWSOCKS,
                host = host,
                port = port,
                config = mapOf(
                    "method" to method,
                    "password" to password
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun parseVless(url: String): VpnServerConfig? {
        try {
            val uri = Uri.parse(url)
            val userInfo = uri.userInfo ?: return null // UUID
            val host = uri.host ?: return null
            val port = uri.port
            if (port == -1) return null

            val queryMap = mutableMapOf<String, String>()
            uri.queryParameterNames.forEach { key ->
                uri.getQueryParameter(key)?.let { queryMap[key] = it }
            }

            val name = uri.fragment?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.toString()) } ?: "VLESS Server"

            val config = mutableMapOf<String, String>()
            config["uuid"] = userInfo
            config["type"] = queryMap["type"] ?: "tcp"
            config["security"] = queryMap["security"] ?: "none"
            config["fp"] = queryMap["fp"] ?: ""
            config["sni"] = queryMap["sni"] ?: ""
            config["pbk"] = queryMap["pbk"] ?: ""
            config["sid"] = queryMap["sid"] ?: ""
            config["flow"] = queryMap["flow"] ?: ""
            
            // XTLS-Reality checks
            if (config["security"] == "reality") {
                config["publicKey"] = config["pbk"] ?: ""
                config["shortId"] = config["sid"] ?: ""
                config["serverName"] = config["sni"] ?: ""
                config["fingerprint"] = config["fp"] ?: "chrome"
            }

            return VpnServerConfig(
                id = UUID.randomUUID().toString(),
                name = name,
                protocol = VpnProtocol.VLESS,
                host = host,
                port = port,
                config = config
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    // VMess usually uses base64 encoded JSON
    private fun parseVmess(url: String): VpnServerConfig? {
        try {
            val base64 = url.removePrefix("vmess://")
            val jsonString = String(Base64.decode(base64, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
            val mapType = object : TypeToken<Map<String, String>>() {}.type
            val data: Map<String, String> = gson.fromJson(jsonString, mapType)

            val ps = data["ps"] ?: "VMess Server"
            val add = data["add"] ?: return null
            val port = data["port"]?.toIntOrNull() ?: 443
            val id = data["id"] ?: return null
            val aid = data["aid"] ?: "0"
            val net = data["net"] ?: "tcp"
            val type = data["type"] ?: "none"
            val host = data["host"] ?: ""
            val path = data["path"] ?: ""
            val tls = data["tls"] ?: ""

            return VpnServerConfig(
                id = UUID.randomUUID().toString(),
                name = ps,
                protocol = VpnProtocol.VMESS,
                host = add,
                port = port,
                config = mapOf(
                    "uuid" to id,
                    "alterId" to aid,
                    "network" to net,
                    "type" to type,
                    "host" to host,
                    "path" to path,
                    "tls" to tls
                )
            )

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun parseTrojan(url: String): VpnServerConfig? {
        try {
            val uri = Uri.parse(url)
            val password = uri.userInfo ?: return null
            val host = uri.host ?: return null
            val port = uri.port
            if (port == -1) return null
            
            val name = uri.fragment?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.toString()) } ?: "Trojan Server"

            val queryMap = mutableMapOf<String, String>()
            uri.queryParameterNames.forEach { key ->
                uri.getQueryParameter(key)?.let { queryMap[key] = it }
            }

            return VpnServerConfig(
                id = "trojan-${System.currentTimeMillis()}",
                name = name,
                protocol = VpnProtocol.TROJAN,
                host = host,
                port = port,
                config = mapOf(
                    "password" to password,
                    "sni" to (queryMap["sni"] ?: ""),
                    "type" to (queryMap["type"] ?: "tcp"),
                    "security" to (queryMap["security"] ?: "tls")
                )
            )

        } catch (e: Exception) {
             e.printStackTrace()
             return null
        }
    }
}

/**
 * VPN Configuration Import/Export
 */
object ConfigImportExport {
    
    private val gson = Gson()

    fun importFromJson(json: String): List<VpnServerConfig> {
        return try {
             val listType = object : TypeToken<List<VpnServerConfig>>() {}.type
             gson.fromJson(json, listType)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    fun exportToJson(configs: List<VpnServerConfig>): String {
        return try {
            gson.toJson(configs)
        } catch (e: Exception) {
            "[]"
        }
    }
}
