package com.carnelia.vpn.core

import android.content.Context
import com.carnelia.vpn.utils.AppLogger
import com.carnelia.vpn.utils.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object XrayCoreManager {

    private var xrayProcess: Process? = null
    private var streamJob: kotlinx.coroutines.Job? = null
    private val xrayScope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    const val LOCAL_PORT = 10808
    const val LOCAL_PASSWORD = "local-xray-bridge"
    const val LOCAL_METHOD = "chacha20-ietf-poly1305"
    
    // Constants for internal tags
    private const val TAG_PROXY = "proxy"
    private const val TAG_PROXY_OUT = "proxy_out"
    private const val TAG_DIRECT = "direct"
    private const val TAG_BLOCKED = "blocked"

    suspend fun startCore(context: Context, config: VpnServerConfig) = withContext(Dispatchers.IO) {
        stopCore() // Ensure clean state

        try {
            // 1. Prepare Executable
            val nativeLibraryDir = context.applicationInfo.nativeLibraryDir
            val executableFile = File(nativeLibraryDir, "libxray_core.so")
            
            if (!executableFile.exists()) {
                throw Exception("Xray Core binary not found at ${executableFile.absolutePath}")
            }
            
            val executablePath = executableFile.absolutePath

            // 2. Validate Config (Security Check)
            validateConfig(config)
            
            // 3. Generate Config
            val configJson = buildConfig(context, config)
            
            AppLogger.log("Xray Config Generated")
            // Not logging config details to avoid exposing UUIDs/keys in logs
            
            val configFile = File(context.filesDir, "xray_config.json")
            configFile.writeText(configJson.toString())

            // 4. Launch Process
            val command = listOf(executablePath, "-config", configFile.absolutePath)
            
            val processBuilder = ProcessBuilder(command)
            processBuilder.directory(context.filesDir)
            processBuilder.redirectErrorStream(true) // Merge stderr into stdout
            
            xrayProcess = processBuilder.start()
            
            // Consume output continuously (Stream Gobbler)
            val stream = xrayProcess!!.inputStream
            streamJob?.cancel()
            streamJob = xrayScope.launch {
                try {
                    stream.bufferedReader().use { reader ->
                        for (line in reader.lineSequence()) {
                            if (!isActive) break
                            AppLogger.log("Xray: $line")
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.log("Xray: Stream closed")
                }
            }

            // Monitor startup — give the process a short moment then check
            kotlinx.coroutines.delay(300)
            if (!xrayProcess!!.isAlive) {
                 val exitValue = xrayProcess?.exitValue()
                 throw Exception("Xray process died immediately (Exit Code: $exitValue)")
            }

            AppLogger.log("XrayCoreManager: Started successfully on PID ${getTag()}")

        } catch (e: Exception) {
            AppLogger.error("XrayCoreManager: Start Failed", e)
            stopCore()
            throw e
        }
    }

    fun stopCore() {
        if (xrayProcess != null) {
            AppLogger.log("XrayCoreManager: Stopping process...")
            streamJob?.cancel()
            streamJob = null
            xrayProcess?.destroy()
            xrayProcess = null
        }
    }

    private fun getTag(): String = "Active"

    /**
     * Main Config Builder
     */
    private fun buildConfig(context: Context, vpnConfig: VpnServerConfig): JSONObject {
        val root = JSONObject()
        
        // Log
        val accessLogPath = context.filesDir.absolutePath + "/xray_access.log"
        root.put("log", JSONObject()
            .put("loglevel", "warning")
            .put("access", accessLogPath)
        )

        // DNS
        root.put("dns", buildDns(context))

        // Inbounds
        root.put("inbounds", buildInbounds(context))

        // Outbounds
        root.put("outbounds", buildOutbounds(context, vpnConfig))
        
        // Routing
        root.put("routing", buildRouting(context))

        return root
    }
    
    private fun buildDns(context: Context): JSONObject {
        val dns = JSONObject()
        val servers = JSONArray()
        
        if (PrefsManager.isNetShieldEnabled(context)) {
             AppLogger.log("NetShield: Enabled. Using AdGuard DNS.")
             servers.put("94.140.14.14") // AdGuard Default
             servers.put("94.140.15.15")
        } else {
             // Use User Preferred DNS or Cloudflare/Google
             val userDns = PrefsManager.getDnsServer(context)
             if (userDns.isNotBlank()) {
                 servers.put(userDns)
             }
             servers.put("1.1.1.1")
             servers.put("8.8.8.8")
        }
        
        dns.put("servers", servers)
        dns.put("queryStrategy", "UseIP") // Avoid DNS poisoning affecting Xray resolution if possible
        return dns
    }
    
    private fun buildInbounds(context: Context): JSONArray {
        val inbounds = JSONArray()
        
        // 1. Local SOCKS/Shadowsocks Bridge for Tun2Socks
        val localInbound = JSONObject()
        localInbound.put("tag", TAG_PROXY)
        localInbound.put("port", LOCAL_PORT)
        localInbound.put("listen", "127.0.0.1")
        localInbound.put("protocol", "shadowsocks")
        
        val settings = JSONObject()
        settings.put("method", LOCAL_METHOD)
        settings.put("password", LOCAL_PASSWORD)
        settings.put("network", "tcp,udp")
        
        localInbound.put("settings", settings)
        
        // Sniffing
        val sniffing = JSONObject()
        val sniffs = JSONArray()
        sniffs.put("http")
        sniffs.put("tls")
        sniffs.put("quic")
        sniffing.put("enabled", true)
        sniffing.put("destOverride", sniffs)
        localInbound.put("sniffing", sniffing)

        inbounds.put(localInbound)

        // 2. LAN Http Proxy (Optional)
        if (PrefsManager.isAllowLanEnabled(context)) {
            val lanInbound = JSONObject()
            lanInbound.put("tag", "lan_proxy")
            lanInbound.put("port", 10809)
            lanInbound.put("listen", "0.0.0.0")
            lanInbound.put("protocol", "http")
             val lanSettings = JSONObject()
             lanSettings.put("auth", "noauth")
             lanSettings.put("udp", true)
             lanInbound.put("settings", lanSettings)
             
            inbounds.put(lanInbound)
            AppLogger.log("Allow LAN: Enabled on port 10809 (HTTP)")
        }
        
        return inbounds
    }

    private fun buildOutbounds(context: Context, vpnConfig: VpnServerConfig): JSONArray {
        val outbounds = JSONArray()
        val isDoubleTunnel = PrefsManager.isDoubleTunnelEnabled(context)
        val doubleServerId = PrefsManager.getDoubleTunnelServerId(context)

        // Determine double tunnel second server
        val secondConfig: VpnServerConfig? = if (isDoubleTunnel && doubleServerId.isNotBlank()) {
            try {
                com.carnelia.vpn.data.ServerRepository(context).getServers()
                    .firstOrNull { it.id == doubleServerId && it.id != vpnConfig.id }
            } catch (e: Exception) { null }
        } else null

        // 1. First hop (proxy_out — connects directly to server1)
        val realOutbound = JSONObject()
        realOutbound.put("tag", TAG_PROXY_OUT)
        if (PrefsManager.isMuxEnabled(context)) {
            val mux = JSONObject()
            mux.put("enabled", true)
            mux.put("concurrency", PrefsManager.getMuxTcpConcurrency(context))
            realOutbound.put("mux", mux)
        }
        configureProtocol(realOutbound, vpnConfig)
        applySockOpt(context, realOutbound)
        outbounds.put(realOutbound)

        // 2. Second hop (proxy_chain) — only if double tunnel configured
        if (secondConfig != null) {
            AppLogger.log("DoubleTunnel: ${vpnConfig.host} → ${secondConfig.host}")
            val chainOutbound = JSONObject()
            chainOutbound.put("tag", "proxy_chain")
            configureProtocol(chainOutbound, secondConfig)
            val chainStream = chainOutbound.optJSONObject("streamSettings") ?: JSONObject()
            val chainSockopt = chainStream.optJSONObject("sockopt") ?: JSONObject()
            chainSockopt.put("dialerProxy", TAG_PROXY_OUT)
            chainStream.put("sockopt", chainSockopt)
            chainOutbound.put("streamSettings", chainStream)
            outbounds.put(chainOutbound)
        }

        // 3. Direct
        val direct = JSONObject()
        direct.put("tag", TAG_DIRECT)
        direct.put("protocol", "freedom")
        direct.put("settings", JSONObject())
        outbounds.put(direct)

        // 4. Blocked
        val block = JSONObject()
        block.put("tag", TAG_BLOCKED)
        block.put("protocol", "blackhole")
        block.put("settings", JSONObject())
        outbounds.put(block)

        return outbounds
    }

    private fun buildRouting(context: Context): JSONObject {
        val routing = JSONObject()
        
        // Domain Strategy
        val ipType = PrefsManager.getPreferredIpType(context)
        when (ipType) {
            "ipv4" -> routing.put("domainStrategy", "UseIPv4") 
            "ipv6" -> routing.put("domainStrategy", "UseIPv6") 
            else -> routing.put("domainStrategy", "IPIfNonMatch")
        }

        // Determine effective outbound tag (proxy_chain for double tunnel)
        val isDoubleTunnel = PrefsManager.isDoubleTunnelEnabled(context)
        val doubleServerId = PrefsManager.getDoubleTunnelServerId(context)
        val effectiveProxy = if (isDoubleTunnel && doubleServerId.isNotBlank()) "proxy_chain" else TAG_PROXY_OUT

        val rules = JSONArray()

        // 1. Stealth Mode (Block UDP 443)
        if (PrefsManager.isStealthModeEnabled(context)) {
             val blockQuic = JSONObject()
             blockQuic.put("type", "field")
             blockQuic.put("port", "443")
             blockQuic.put("network", "udp")
             blockQuic.put("outboundTag", TAG_BLOCKED)
             rules.put(blockQuic)
        }

        // Private IP Bypass (hardcoded ranges, no geo files needed)
        val privateRule = JSONObject()
        privateRule.put("type", "field")
        privateRule.put("outboundTag", TAG_DIRECT)
        privateRule.put("ip", JSONArray().apply {
            put("10.0.0.0/8"); put("172.16.0.0/12"); put("192.168.0.0/16"); put("127.0.0.0/8")
        })
        rules.put(privateRule)

        // Blocked domains (user-defined via Traffic Map)
        val blockedDomains = PrefsManager.getBlockedDomains(context)
        if (blockedDomains.isNotEmpty()) {
            val domainBlockRule = JSONObject()
            domainBlockRule.put("type", "field")
            domainBlockRule.put("domain", JSONArray().apply { blockedDomains.forEach { put(it) } })
            domainBlockRule.put("outboundTag", TAG_BLOCKED)
            rules.put(domainBlockRule)
        }

        // Default rule — all traffic to effective proxy
        val defaultRule = JSONObject()
        defaultRule.put("type", "field")
        defaultRule.put("network", "tcp,udp")
        defaultRule.put("outboundTag", effectiveProxy)
        rules.put(defaultRule)
        
        routing.put("rules", rules)
        return routing
    }
    
    private fun applySockOpt(context: Context, outboundJson: JSONObject) {
         val streamSettings = outboundJson.optJSONObject("streamSettings") ?: JSONObject()
         val sockopt = JSONObject()
         
         if (PrefsManager.isFragmentationEnabled(context)) {
             val fragment = JSONObject()
             // No "enabled" field — xray enables fragment by presence of the object itself
             fragment.put("packets", PrefsManager.getFragmentPackets(context))
             fragment.put("length", PrefsManager.getFragmentLength(context))
             fragment.put("interval", PrefsManager.getFragmentInterval(context))
             
             sockopt.put("fragment", fragment)
             AppLogger.log("Fragmentation: Applied from prefs")
         }
         
         sockopt.put("tcpKeepAliveInterval", 300)
         
         streamSettings.put("sockopt", sockopt)
         outboundJson.put("streamSettings", streamSettings)
    }

    private fun configureProtocol(outbound: JSONObject, config: VpnServerConfig) {
        when (config.protocol) {
            VpnProtocol.VLESS -> configureVless(outbound, config)
            VpnProtocol.VMESS -> configureVmess(outbound, config)
            VpnProtocol.TROJAN -> configureTrojan(outbound, config)
            VpnProtocol.SHADOWSOCKS, VpnProtocol.OUTLINE -> configureShadowsocks(outbound, config)
            VpnProtocol.WIREGUARD -> configureWireguard(outbound, config)
            VpnProtocol.AMNEZIA_WG -> {
                AppLogger.log("AmneziaWG: Connecting via Xray WireGuard (Jc/Jmin obfuscation not applied — requires amneziawg-go)")
                configureWireguard(outbound, config)
            }
            VpnProtocol.SOCKS -> configureSocks(outbound, config)
            VpnProtocol.HTTP -> configureInternalHttp(outbound, config)
            else -> throw Exception("Unsupported protocol: ${config.protocol}")
        }
    }

    // Protocol Implementations (Keep existing logic minimal touch, just reformat if needed)
    
    private fun configureVless(outbound: JSONObject, config: VpnServerConfig) {
        outbound.put("protocol", "vless")
        val settings = JSONObject()
        val vnext = JSONArray()
        val server = JSONObject()
        server.put("address", config.host)
        server.put("port", config.port)
        
        val users = JSONArray()
        val user = JSONObject()
        
        val uuid = config.config["uuid"] ?: config.config["id"] ?: config.password
        if (uuid.isNullOrEmpty()) throw Exception("VLESS requires a valid UUID")
        user.put("id", uuid)
        user.put("encryption", "none")
        val flow = config.config["flow"] ?: ""
        if (flow.isNotBlank()) {
            user.put("flow", flow)
        }
        users.put(user)
        server.put("users", users)
        
        vnext.put(server)
        settings.put("vnext", vnext)
        outbound.put("settings", settings)
        
        val streamSettings = JSONObject()
        val transport = (config.config["type"] ?: "tcp").lowercase()
        val security = (config.config["security"] ?: "none").lowercase()
        streamSettings.put("network", transport)
        streamSettings.put("security", security)
        
        if (security == "reality") {
             // Support both new short keys and legacy full-name keys saved by older app versions
             val pbk = (config.config["pbk"] ?: config.config["publicKey"] ?: "").trim()
             if (pbk.isBlank() || !isValidRealityPublicKey(pbk)) throw Exception("VLESS REALITY: публичный ключ недействителен ($pbk). Удалите сервер и добавьте заново.")
             val sni = (config.config["sni"] ?: config.config["serverName"] ?: "").trim()
             val sid = (config.config["sid"] ?: config.config["shortId"] ?: "").trim()
             val fp  = (config.config["fp"]  ?: config.config["fingerprint"] ?: "").trim()
             val reality = JSONObject()
             reality.put("fingerprint", if (fp.isNotBlank()) fp else "chrome")
             reality.put("serverName", sni)
             reality.put("publicKey", pbk)
             reality.put("shortId", sid)
             reality.put("spiderX", if ((config.config["spx"] ?: "").isNotBlank()) config.config["spx"] else "/")
             reality.put("show", false)
             streamSettings.put("realitySettings", reality)
           } else if (security == "tls") {
             val tls = JSONObject()
             tls.put("serverName", config.config["sni"] ?: "")
             val fp = config.config["fp"] ?: ""
             if (fp.isNotBlank()) tls.put("fingerprint", fp)
             tls.put("allowInsecure", config.config["allowInsecure"] == "1")
             val alpn = config.config["alpn"] ?: ""
             if (alpn.isNotBlank()) {
                 val alpnArr = JSONArray()
                 alpn.split(",").forEach { alpnArr.put(it.trim()) }
                 tls.put("alpn", alpnArr)
             }
             streamSettings.put("tlsSettings", tls)
        }
        
        when (transport) {
            "ws" -> {
                val ws = JSONObject()
                ws.put("path", config.config["path"] ?: "/")
                val hostHeader = config.config["host_header"] ?: ""
                if (hostHeader.isNotBlank()) {
                    ws.put("headers", JSONObject().put("Host", hostHeader))
                }
                streamSettings.put("wsSettings", ws)
            }
            "grpc" -> {
                val grpc = JSONObject()
                grpc.put("serviceName", config.config["serviceName"] ?: "")
                val authority = config.config["authority"] ?: ""
                if (authority.isNotBlank()) {
                    grpc.put("authority", authority)
                }
                grpc.put("multiMode", false)
                streamSettings.put("grpcSettings", grpc)
            }
            "httpupgrade" -> {
                val httpUpgrade = JSONObject()
                httpUpgrade.put("path", config.config["path"] ?: "/")
                val hostHeader = config.config["host_header"] ?: ""
                if (hostHeader.isNotBlank()) {
                    httpUpgrade.put("host", hostHeader)
                    httpUpgrade.put("headers", JSONObject().put("Host", hostHeader))
                }
                streamSettings.put("httpupgradeSettings", httpUpgrade)
            }
            "http" -> {
                val http = JSONObject()
                http.put("path", config.config["path"] ?: "/")
                val hostHeader = config.config["host_header"] ?: ""
                if (hostHeader.isNotBlank()) {
                    val headers = JSONObject()
                    headers.put("Host", JSONArray().put(hostHeader))
                    http.put("headers", headers)
                }
                streamSettings.put("httpSettings", http)
            }
            "xhttp" -> {
                val xhttp = JSONObject()
                xhttp.put("path", config.config["path"] ?: "/")
                val hostHeader = config.config["host_header"] ?: ""
                if (hostHeader.isNotBlank()) {
                    xhttp.put("host", hostHeader)
                }
                val mode = (config.config["mode"] ?: "").lowercase()
                if (mode.isNotBlank()) {
                    xhttp.put("mode", mode)
                }
                streamSettings.put("xhttpSettings", xhttp)
            }
        }

        outbound.put("streamSettings", streamSettings)
    }

    private fun configureVmess(outbound: JSONObject, config: VpnServerConfig) {
        outbound.put("protocol", "vmess")
        val settings = JSONObject()
        val vnext = JSONArray()
        val server = JSONObject()
        server.put("address", config.host)
        server.put("port", config.port)
        
        val users = JSONArray()
        val user = JSONObject()
        // parseVmess stores uuid as "uuid"; support legacy "id" key as fallback
        user.put("id", config.config["uuid"] ?: config.config["id"] ?: config.password)
        // parseVmess stores alterId as "alterId"; support legacy "aid" key as fallback
        user.put("alterId", (config.config["alterId"] ?: config.config["aid"] ?: "0").toIntOrNull() ?: 0)
        user.put("security", "auto")
        users.put(user)
        server.put("users", users)
        
        vnext.put(server)
        settings.put("vnext", vnext)
        outbound.put("settings", settings)
        
        // parseVmess stores network as "network"; support legacy "net" key as fallback
        val vmessNetwork = config.config["network"] ?: config.config["net"] ?: "tcp"
        val streamSettings = JSONObject()
        streamSettings.put("network", vmessNetwork)
        streamSettings.put("security", config.config["tls"] ?: "none")
        
        if (config.config["tls"] == "tls") {
             val tls = JSONObject()
             tls.put("serverName", config.config["sni"] ?: config.config["host"] ?: "")
             streamSettings.put("tlsSettings", tls)
        }
        
        if (vmessNetwork == "ws") {
             val ws = JSONObject()
             ws.put("path", config.config["path"] ?: "/")
             if (config.config.containsKey("host") && !config.config["host"].isNullOrBlank()) {
                 ws.put("headers", JSONObject().put("Host", config.config["host"]))
             }
             streamSettings.put("wsSettings", ws)
        }
        outbound.put("streamSettings", streamSettings)
    }

    private fun configureTrojan(outbound: JSONObject, config: VpnServerConfig) {
        outbound.put("protocol", "trojan")
        val settings = JSONObject()
        val servers = JSONArray()
        val server = JSONObject()
        server.put("address", config.host)
        server.put("port", config.port)
        server.put("password", config.config["password"] ?: config.password ?: "")
        servers.put(server)
        settings.put("servers", servers)
        outbound.put("settings", settings)
        
        val streamSettings = JSONObject()
        // Use transport type and security from parsed config instead of hardcoding
        val trojanNetwork = config.config["type"] ?: "tcp"
        val trojanSecurity = config.config["security"] ?: "tls"
        streamSettings.put("network", trojanNetwork)
        streamSettings.put("security", trojanSecurity)
        val tls = JSONObject()
        tls.put("serverName", config.config["sni"] ?: "")
        val fp = config.config["fp"] ?: ""
        if (fp.isNotBlank()) tls.put("fingerprint", fp)
        streamSettings.put("tlsSettings", tls)
        
        if (trojanNetwork == "ws") {
            val ws = JSONObject()
            ws.put("path", config.config["path"] ?: "/")
            val hostHeader = config.config["host_header"] ?: config.config["sni"] ?: ""
            if (hostHeader.isNotBlank()) {
                ws.put("headers", JSONObject().put("Host", hostHeader))
            }
            streamSettings.put("wsSettings", ws)
        }
        outbound.put("streamSettings", streamSettings)
    }

    private fun configureShadowsocks(outbound: JSONObject, config: VpnServerConfig) {
         outbound.put("protocol", "shadowsocks")
         val settings = JSONObject()
         val servers = JSONArray()
         val server = JSONObject()
         server.put("address", config.host)
         server.put("port", config.port)
         server.put("method", config.config["method"] ?: "chacha20-ietf-poly1305")
         server.put("password", config.password ?: config.config["password"])
         servers.put(server)
         settings.put("servers", servers)
         outbound.put("settings", settings)
    }

    private fun configureWireguard(outbound: JSONObject, config: VpnServerConfig) {
        outbound.put("protocol", "wireguard")
        val settings = JSONObject()
        settings.put("secretKey", config.config["private_key"] ?: "")

        // Interface address (WireGuard assigns its own IP)
        val address = config.config["address"]
        if (!address.isNullOrBlank()) {
            val addrArray = JSONArray()
            address.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { addrArray.put(it) }
            settings.put("address", addrArray)
        }

        // DNS override from WG config
        val dns = config.config["dns"]
        if (!dns.isNullOrBlank()) {
            val dnsArray = JSONArray()
            dns.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { dnsArray.put(it) }
            settings.put("dns", dnsArray)
        }

        // MTU
        val mtu = config.config["mtu"]?.toIntOrNull()
        if (mtu != null) settings.put("mtu", mtu)

        val peers = JSONArray()
        val peer = JSONObject()
        peer.put("publicKey", config.config["public_key"] ?: "")
        if (!config.config["preshared_key"].isNullOrBlank()) {
            peer.put("preSharedKey", config.config["preshared_key"])
        }
        peer.put("endpoint", "${config.host}:${config.port}")
        val keepAlive = config.config["keepalive"]?.toIntOrNull()
        if (keepAlive != null) peer.put("keepAlive", keepAlive)
        // AllowedIPs (default to route all traffic)
        val allowedIpsStr = config.config["allowed_ips"] ?: "0.0.0.0/0"
        val allowedIps = JSONArray()
        allowedIpsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { allowedIps.put(it) }
        peer.put("allowedIPs", allowedIps)
        peers.put(peer)
        settings.put("peers", peers)
        outbound.put("settings", settings)
    }

    private fun configureSocks(outbound: JSONObject, config: VpnServerConfig) {
        outbound.put("protocol", "socks")
        val settings = JSONObject()
        val servers = JSONArray()
        val server = JSONObject()
        server.put("address", config.host)
        server.put("port", config.port)
        servers.put(server)
        settings.put("servers", servers)
        outbound.put("settings", settings)
    }
    
    private fun configureInternalHttp(outbound: JSONObject, config: VpnServerConfig) {
        outbound.put("protocol", "http")
        val settings = JSONObject()
        val servers = JSONArray()
        val server = JSONObject()
        server.put("address", config.host)
        server.put("port", config.port)
        servers.put(server)
        settings.put("servers", servers)
        outbound.put("settings", settings)
    }
    
    // Validates critical fields
    private fun validateConfig(config: VpnServerConfig) {
        if (config.host.isBlank()) throw Exception("Адрес сервера не указан")
        if (config.port <= 0 || config.port > 65535) throw Exception("Неверный порт: ${config.port}")

        // REALITY public key validation (check both new "pbk" and legacy "publicKey")
        if (config.protocol == VpnProtocol.VLESS && config.config["security"] == "reality") {
            val pbk = (config.config["pbk"] ?: config.config["publicKey"] ?: "").trim()
            if (pbk.isBlank() || !isValidRealityPublicKey(pbk)) {
                throw Exception(
                    "VLESS REALITY: публичный ключ недействителен ($pbk). " +
                    "Удалите сервер и добавьте его снова через кнопку \"Добавить сервер\""
                )
            }
        }
    }

    /** A valid REALITY public key is a base64url string (no ':' or spaces), at least 30 chars long. */
    private fun isValidRealityPublicKey(pbk: String): Boolean {
        if (pbk.length < 30) return false
        // Reject obvious placeholders like "Hash32:", "placeholder", "example"
        if (pbk.contains(':')) return false
        if (pbk.contains(' ')) return false
        val lower = pbk.lowercase()
        if (lower.startsWith("hash") || lower.startsWith("placeholder") || lower.startsWith("example")) return false
        return true
    }
}
