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
import java.util.Locale

/**
 * Universal VPN Config Parser
 * Supports: Outline (ss), Shadowsocks, VLESS, VMess, Trojan
 */
object ConfigParser {

    private val gson = Gson()

    fun parse(input: String): VpnServerConfig? {
        return try {
            parseOrThrow(input)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @Throws(IllegalArgumentException::class)
    fun parseOrThrow(input: String): VpnServerConfig {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) error("Configuration string is empty", "Строка конфигурации пуста")
        
        val lower = trimmed.lowercase() // Use lowercase for prefix check

        return when {
            lower.startsWith("ss://") -> parseShadowsocks(trimmed)
            lower.startsWith("vless://") -> parseVless(trimmed)
            lower.startsWith("vmess://") -> parseVmess(trimmed)
            lower.startsWith("trojan://") -> parseTrojan(trimmed)
            // Simple heuristic for OpenVPN text content
            lower.contains("client") && lower.contains("remote ") -> parseOpenVpnContent(trimmed)
            lower.contains("dev tun") -> parseOpenVpnContent(trimmed)
            lower.startsWith("client\r\n") || lower.startsWith("client\n") -> parseOpenVpnContent(trimmed)
            // Attempt generic OpenVPN fallback if looks like config
            lower.contains("remote ") && lower.contains("port ") -> parseOpenVpnContent(trimmed)
            // Attempt to decode base64 if no prefix
            isBase64(trimmed) -> parse(decodeBase64(trimmed)) ?: error("Failed to parse decoded config")
            else -> error(
                "Unknown protocol or invalid format. Supported: vless://, vmess://, ss://, trojan://, OpenVPN",
                "Неизвестный формат ключа. Поддерживается: vless, vmess, ss, trojan, openvpn"
            )
        }
    }

    private fun isBase64(str: String): Boolean {
        return try {
            if (str.length < 10) return false
            Base64.decode(str, Base64.DEFAULT)
            true
        } catch (e: Exception) { false }
    }

    private fun error(en: String, ru: String): Nothing {
        val isRu = Locale.getDefault().language == "ru"
        throw IllegalArgumentException(if (isRu) ru else en)
    }

    private fun decodeBase64(input: String): String {
        return try {
            String(
                Base64.decode(input, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP),
                StandardCharsets.UTF_8
            )
        } catch (e: Exception) {
            try {
                String(Base64.decode(input, Base64.DEFAULT), StandardCharsets.UTF_8)
            } catch (e2: Exception) {
                throw IllegalArgumentException("Base64 decode failed")
            }
        }
    }
    
    fun parseOpenVpnContent(content: String): VpnServerConfig {
        var name = "OpenVPN Server"
        try {
            val remoteLine = content.lines().find { it.trim().startsWith("remote ") }
            if (remoteLine != null) {
                val parts = remoteLine.trim().split("\\s+".toRegex())
                if (parts.size >= 2) {
                    name = parts[1]
                }
            }
        } catch (e: Exception) {}

        return VpnServerConfig(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = VpnProtocol.OPENVPN,
            host = name, 
            port = 1194, 
            config = mapOf("ovpn_data" to content)
        )
    }

    private fun parseShadowsocks(url: String): VpnServerConfig {
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

            val isOutline = url.contains("outline=1")
            
            val userPart: String
            val hostPart: String
            
            if (cleanUrl.contains("@")) {
                val parts = cleanUrl.split("@")
                userPart = parts[0]
                hostPart = parts[1]
            } else {
                val decoded = try {
                     decodeBase64(cleanUrl)
                } catch (e: Exception) {
                     error("SS: Invalid Base64 config", "SS: Ошибка декодирования Base64")
                }
                
                if (decoded.contains("@")) {
                     val parts = decoded.split("@")
                     userPart = parts[0]
                     hostPart = parts[1]
                } else {
                    error("SS: Malformed config (missing @)", "SS: Неверный формат ссылки (нет @)")
                }
            }

            val methodPass = if (userPart.contains(":")) {
                userPart.split(":", limit = 2)
            } else {
                 val decodedAuth = try {
                      decodeBase64(userPart)
                 } catch (e: Exception) {
                      error("SS: Invalid Auth Base64", "SS: Ошибка кодировки пароля")
                 }
                 decodedAuth.split(":", limit = 2)
            }

            if (methodPass.size != 2) error("SS: Method/Password missing", "SS: Не указан метод шифрования или пароль")
            val method = methodPass[0]
            val password = methodPass[1]

            val hostStr: String
            val portStr: String
            
            if (hostPart.startsWith("[")) {
                val close = hostPart.indexOf("]")
                if (close == -1) error("SS: Invalid IPv6", "SS: Некорректный IPv6")
                hostStr = hostPart.substring(1, close)
                if (hostPart.length > close + 1 && hostPart[close+1] == ':') {
                    portStr = hostPart.substring(close+2)
                } else {
                    error("SS: Port missing", "SS: Не указан порт")
                }
            } else {
                val hp = hostPart.split(":")
                if (hp.size != 2) error("SS: Invalid Host:Port", "SS: Неверный формат Хост:Порт")
                hostStr = hp[0]
                portStr = hp[1]
            }
            
            val port = portStr.toIntOrNull() ?: error("SS: Invalid Port", "SS: Некорректный порт")

            return VpnServerConfig(
                id = UUID.randomUUID().toString(),
                name = name,
                protocol = if (isOutline) VpnProtocol.OUTLINE else VpnProtocol.SHADOWSOCKS,
                host = hostStr,
                port = port,
                config = mapOf(
                    "method" to method,
                    "password" to password
                )
            )
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            error("Shadowsocks error: ${e.message}", "Ошибка Shadowsocks: ${e.message}")
        }
    }

    private fun parseVless(url: String): VpnServerConfig {
        try {
            val uri = Uri.parse(url)
            val userInfo = uri.userInfo ?: error("VLESS: User info (UUID) missing", "VLESS: Отсутствует UUID пользователя")
            val host = uri.host ?: error("VLESS: Host / IP missing", "VLESS: Отсутствует адрес сервера")
            val port = uri.port
            if (port == -1) error("VLESS: Port missing", "VLESS: Некорректный порт")

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
            
            if (config["security"] == "reality") {
                val pbk = config["pbk"] ?: ""
                config["publicKey"] = pbk
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
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            error("VLESS parse error: ${e.message}", "Ошибка разбора VLESS: ${e.message}")
        }
    }
    
    // VMess usually uses base64 encoded JSON
    private fun parseVmess(url: String): VpnServerConfig {
        try {
            val base64 = url.substring(8) // "vmess://" is 8 chars
            val jsonString = try {
                 decodeBase64(base64)
            } catch (e: Exception) {
                 error("VMess: Invalid Base64", "VMess: Некорректный Base64")
            }
            
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            val data: Map<String, Any> = try {
                 gson.fromJson(jsonString, mapType)
            } catch (e: Exception) {
                 error("VMess: Invalid JSON", "VMess: Некорректный JSON конфигурации")
            }

            val ps = (data["ps"] as? String) ?: "VMess Server"
            val add = (data["add"] as? String) ?: error("VMess: 'add' (address) missing", "VMess: Не указан адрес (add)")
            val portParam = data["port"] 
            val port = when(portParam) {
                is Number -> portParam.toInt()
                is String -> portParam.toIntOrNull() ?: 443
                else -> 443
            }

            val id = (data["id"] as? String) ?: error("VMess: 'id' (UUID) missing", "VMess: Не указан UUID (id)")
            val aid = (data["aid"] as? String) ?: "0"
            val net = (data["net"] as? String) ?: "tcp"
            val type = (data["type"] as? String) ?: "none"
            val host = (data["host"] as? String) ?: ""
            val path = (data["path"] as? String) ?: ""
            val tls = (data["tls"] as? String) ?: ""

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

        } catch (e: IllegalArgumentException) {
             throw e
        } catch (e: Exception) {
            e.printStackTrace()
             error("VMess parse error: ${e.message}", "Ошибка разбора VMess: ${e.message}")
        }
    }

    private fun parseTrojan(url: String): VpnServerConfig {
        try {
            val uri = Uri.parse(url)
            val password = uri.userInfo ?: error("Trojan: Password missing", "Trojan: Не указан пароль")
            val host = uri.host ?: error("Trojan: Host missing", "Trojan: Не указан хост")
            val port = uri.port
            if (port == -1) error("Trojan: Port missing", "Trojan: Не указан порт")
            
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

        } catch (e: IllegalArgumentException) {
             throw e
        } catch (e: Exception) {
             e.printStackTrace()
             error("Trojan error: ${e.message}", "Ошибка Trojan: ${e.message}")
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
