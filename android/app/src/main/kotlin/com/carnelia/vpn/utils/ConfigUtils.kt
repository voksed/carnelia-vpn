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
        val trimmed = sanitizeInput(input)
        if (trimmed.isEmpty()) error("Configuration string is empty", "Строка конфигурации пуста")
        
        val lower = trimmed.lowercase() // Use lowercase for prefix check

        return when {
            lower.startsWith("ss://") -> parseShadowsocks(trimmed)
            lower.startsWith("vless://") -> parseVless(trimmed)
            lower.startsWith("vmess://") -> parseVmess(trimmed)
            lower.startsWith("trojan://") -> parseTrojan(trimmed)
            // WireGuard / AmneziaWG
            lower.startsWith("wireguard://") -> parseWireguardUri(trimmed)
            lower.startsWith("[interface]") ||
            lower.contains("\n[interface]") ||
            lower.contains("\r\n[interface]") -> parseWireguardIni(trimmed)
            // Simple heuristic for OpenVPN text content
            lower.contains("client") && lower.contains("remote ") -> parseOpenVpnContent(trimmed)
            lower.contains("dev tun") -> parseOpenVpnContent(trimmed)
            lower.startsWith("client\r\n") || lower.startsWith("client\n") -> parseOpenVpnContent(trimmed)
            // Attempt generic OpenVPN fallback if looks like config
            lower.contains("remote ") && lower.contains("port ") -> parseOpenVpnContent(trimmed)
            // Attempt to decode base64 if no prefix
            isBase64(trimmed) -> parse(decodeBase64(trimmed)) ?: error("Failed to parse decoded config")
            else -> error(
                "Unknown protocol or invalid format. Supported: vless://, vmess://, ss://, trojan://, wireguard://, OpenVPN",
                "Неизвестный формат ключа. Поддерживается: vless, vmess, ss, trojan, wireguard, openvpn"
            )
        }
    }

    private fun sanitizeInput(input: String): String {
        // Remove zero-width/BOM characters often introduced by messengers/clipboard.
        var normalized = input
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\u200D", "")
            .replace("\uFEFF", "")
            .replace("\u00A0", " ")
            .trim()

        // If user copied a message with additional text around a URL, extract the first supported URL.
        val extracted = extractFirstSupportedUrl(normalized)
        if (extracted != null) {
            normalized = extracted
        }

        val lower = normalized.lowercase()
        if (
            lower.startsWith("vless://") ||
            lower.startsWith("vmess://") ||
            lower.startsWith("ss://") ||
            lower.startsWith("trojan://") ||
            lower.startsWith("wireguard://")
        ) {
            // URL keys should not contain whitespaces; collapse accidental line breaks/spaces.
            normalized = normalized.replace(Regex("\\s+"), "")
        }
        return normalized
    }

    private fun extractFirstSupportedUrl(text: String): String? {
        val regex = Regex("(?i)(vless|vmess|ss|trojan|wireguard)://[^\\s\"'<>]+")
        val raw = regex.find(text)?.value ?: return null
        return raw.trimEnd('.', ',', ';', '!', '?', ')', ']', '}', '"', '\'', '»')
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
                // Remove parameters /?# if present
                val cleanHostPart = if (hostPart.contains("/")) hostPart.substringBefore("/") 
                                    else if (hostPart.contains("?")) hostPart.substringBefore("?")
                                    else if (hostPart.contains("#")) hostPart.substringBefore("#")
                                    else hostPart

                val hp = cleanHostPart.split(":")
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
            val schemeSep = url.indexOf("://")
            if (schemeSep <= 0) {
                error("VLESS: Invalid URL scheme", "VLESS: Некорректная схема URL")
            }
            val withoutScheme = url.substring(schemeSep + 3)

            val fragmentPart = withoutScheme.substringAfter('#', "")
            val beforeFragment = withoutScheme.substringBefore('#')
            val queryPart = beforeFragment.substringAfter('?', "")
            val authority = beforeFragment.substringBefore('?')

            val atIndex = authority.lastIndexOf('@')
            if (atIndex <= 0 || atIndex == authority.lastIndex) {
                error("VLESS: User info or host missing", "VLESS: Отсутствует UUID пользователя или хост")
            }

            val userInfo = authority.substring(0, atIndex)
            val hostPortPart = authority.substring(atIndex + 1)

            val host: String
            val port: Int

            if (hostPortPart.startsWith("[")) {
                val close = hostPortPart.indexOf(']')
                if (close == -1 || close == hostPortPart.lastIndex) {
                    error("VLESS: Invalid IPv6 host", "VLESS: Некорректный IPv6 хост")
                }
                host = hostPortPart.substring(1, close)
                val portPart = hostPortPart.substring(close + 1).removePrefix(":")
                port = portPart.toIntOrNull() ?: error("VLESS: Invalid Port", "VLESS: Некорректный порт")
            } else {
                val colonIdx = hostPortPart.lastIndexOf(':')
                if (colonIdx <= 0 || colonIdx == hostPortPart.lastIndex) {
                    error("VLESS: Host / Port missing", "VLESS: Отсутствует хост или порт")
                }
                host = hostPortPart.substring(0, colonIdx)
                val portPart = hostPortPart.substring(colonIdx + 1)
                port = portPart.toIntOrNull() ?: error("VLESS: Invalid Port", "VLESS: Некорректный порт")
            }

            val queryMap = mutableMapOf<String, String>()
            if (queryPart.isNotBlank()) {
                // Support both & and ; as delimiters
                queryPart.split(Regex("[&;]")).forEach { token ->
                    if (token.isBlank()) return@forEach
                    val rawKey = token.substringBefore("=", "").trim()
                    val key = rawKey.removePrefix("amp;").lowercase()
                    if (key.isBlank()) return@forEach
                    val rawValue = token.substringAfter("=", "")
                    val decodedValue = try {
                        URLDecoder.decode(rawValue, StandardCharsets.UTF_8.toString())
                    } catch (_: Exception) {
                        rawValue
                    }
                    // For pbk (REALITY public key): URLDecoder converts unencoded '+' to space,
                    // which corrupts standard-base64 encoded keys. Normalize to base64url:
                    // convert spaces back to '+', then '+' → '-', '/' → '_', strip padding.
                    val finalValue = if (key == "pbk" || key == "publickey" || key == "pk") {
                        decodedValue
                            .replace(' ', '+')   // restore any '+' that URLDecoder decoded as space
                            .replace('+', '-')   // standard base64 → base64url
                            .replace('/', '_')
                            .trimEnd('=')
                    } else {
                        decodedValue
                    }
                    queryMap[key] = finalValue
                }
            }

            fun queryValue(vararg keys: String): String? {
                for (key in keys) {
                    val value = queryMap[key.lowercase()]
                    if (!value.isNullOrBlank()) return value
                }
                return null
            }

            fun boolFlag(vararg keys: String): String {
                val raw = queryValue(*keys)?.trim()?.lowercase() ?: return "0"
                return if (raw == "1" || raw == "true" || raw == "yes" || raw == "on") "1" else "0"
            }

            val name = if (fragmentPart.isNotBlank()) {
                try {
                    URLDecoder.decode(fragmentPart, StandardCharsets.UTF_8.toString())
                } catch (_: Exception) {
                    fragmentPart
                }
            } else {
                "VLESS Server"
            }

            val config = mutableMapOf<String, String>()
            config["uuid"] = userInfo
            val transport = when ((queryValue("type", "transport", "net") ?: "tcp").trim().lowercase()) {
                "", "raw" -> "tcp"
                "h2" -> "http"
                "http-upgrade" -> "httpupgrade"
                "splithttp", "split-http" -> "xhttp"
                else -> (queryValue("type", "transport", "net") ?: "tcp").trim().lowercase()
            }
            val security = when ((queryValue("security", "tls") ?: "none").trim().lowercase()) {
                "", "none" -> "none"
                "tls", "reality" -> (queryValue("security", "tls") ?: "none").trim().lowercase()
                else -> (queryValue("security", "tls") ?: "none").trim().lowercase()
            }
            config["type"] = transport
            config["security"] = security
            config["fp"] = queryValue("fp", "fingerprint") ?: ""
            config["sni"] = queryValue("sni", "servername", "serverName", "host") ?: ""
            config["pbk"] = queryValue("pbk", "publickey", "publicKey", "pk") ?: ""
            config["sid"] = queryValue("sid", "shortid", "shortId") ?: ""
            config["flow"] = queryValue("flow") ?: ""
            config["path"] = queryValue("path") ?: "/"
            config["spx"] = queryValue("spx") ?: ""
            // Host header logic: prefer specific host param, fallback to sni
            config["host_header"] = queryValue("host", "authority") ?: config["sni"] ?: ""
            config["serviceName"] = queryValue("servicename", "serviceName") ?: ""
            config["authority"] = queryValue("authority") ?: ""
            config["mode"] = queryValue("mode") ?: ""
            config["alpn"] = queryValue("alpn") ?: ""
            config["allowInsecure"] = boolFlag("allowinsecure", "allowInsecure", "insecure")
            
            if (config["security"] == "reality") {
                val pbk = config["pbk"] ?: ""
                if (pbk.isBlank()) {
                    // Include found keys in error for debugging
                    val foundKeys = queryMap.keys.joinToString(", ")
                    error(
                        "VLESS REALITY: publicKey (pbk) is missing. Found params: $foundKeys", 
                        "VLESS REALITY: не найден ключ pbk. Найдены параметры: $foundKeys"
                    )
                }
                
                config["publicKey"] = pbk
                config["shortId"] = config["sid"] ?: ""
                config["serverName"] = config["sni"] ?: ""
                config["fingerprint"] = if (config["fp"]!!.isNotBlank()) config["fp"]!! else "chrome"
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

    private fun parseWireguardUri(url: String): VpnServerConfig {
        val body = url.removePrefix("wireguard://").removePrefix("wireguard://")
        val decoded = try { decodeBase64(body) } catch (e: Exception) { body }
        if (decoded.lowercase().contains("[interface]")) return parseWireguardIni(decoded)
        error("WireGuard: Cannot parse wireguard:// URI", "WireGuard: Неверный формат ссылки wireguard://")
    }

    /**
     * Parses a WireGuard or AmneziaWG INI config.
     * AmneziaWG is detected by presence of Jc/Jmin/Jmax/S1/S2/H1 obfuscation fields.
     * Note: Jc/Jmin/Jmax/S1/S2/H1-H4 obfuscation is stored but requires amneziawg-go
     * native library to actually work — connection falls back to plain WireGuard via Xray.
     */
    fun parseWireguardIni(content: String): VpnServerConfig {
        var section = ""
        val cfg = mutableMapOf<String, String>()
        var host = ""
        var port = 51820

        for (rawLine in content.lines()) {
            val line = rawLine.substringBefore("#").trim()
            if (line.isEmpty()) continue
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length - 1).lowercase()
                continue
            }
            val eqIdx = line.indexOf('=')
            if (eqIdx <= 0) continue
            val key = line.substring(0, eqIdx).trim().lowercase()
            val value = line.substring(eqIdx + 1).trim()
            when (section) {
                "interface" -> when (key) {
                    "privatekey"   -> cfg["private_key"] = value
                    "address"      -> cfg["address"] = value
                    "dns"          -> cfg["dns"] = value
                    "listenport"   -> cfg["listen_port"] = value
                    "mtu"          -> cfg["mtu"] = value
                    // AmneziaWG obfuscation fields
                    "jc"           -> cfg["Jc"] = value
                    "jmin"         -> cfg["Jmin"] = value
                    "jmax"         -> cfg["Jmax"] = value
                    "s1"           -> cfg["S1"] = value
                    "s2"           -> cfg["S2"] = value
                    "h1"           -> cfg["H1"] = value
                    "h2"           -> cfg["H2"] = value
                    "h3"           -> cfg["H3"] = value
                    "h4"           -> cfg["H4"] = value
                }
                "peer" -> when (key) {
                    "publickey"          -> cfg["public_key"] = value
                    "presharedkey"       -> cfg["preshared_key"] = value
                    "allowedips"         -> cfg["allowed_ips"] = value
                    "persistentkeepalive" -> cfg["keepalive"] = value
                    "endpoint" -> {
                        cfg["endpoint"] = value
                        if (value.startsWith("[")) {
                            val close = value.indexOf(']')
                            if (close > 0) {
                                host = value.substring(1, close)
                                if (value.length > close + 1 && value[close + 1] == ':') {
                                    port = value.substring(close + 2).toIntOrNull() ?: 51820
                                }
                            }
                        } else {
                            val parts = value.split(":")
                            host = parts[0]
                            port = parts.getOrNull(1)?.toIntOrNull() ?: 51820
                        }
                    }
                }
            }
        }

        if (host.isBlank()) error("WireGuard: endpoint missing", "WireGuard: Не указан адрес сервера (Endpoint)")
        if (cfg["private_key"].isNullOrBlank()) error("WireGuard: PrivateKey missing", "WireGuard: Не указан PrivateKey")
        if (cfg["public_key"].isNullOrBlank()) error("WireGuard: peer PublicKey missing", "WireGuard: Не указан PublicKey сервера")

        val isAmnezia = listOf("Jc", "Jmin", "Jmax", "S1", "S2", "H1").any { cfg.containsKey(it) }
        val protocol = if (isAmnezia) VpnProtocol.AMNEZIA_WG else VpnProtocol.WIREGUARD
        val name = if (isAmnezia) "AmneziaWG Server" else "WireGuard Server"

        return VpnServerConfig(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = protocol,
            host = host,
            port = port,
            config = cfg
        )
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
