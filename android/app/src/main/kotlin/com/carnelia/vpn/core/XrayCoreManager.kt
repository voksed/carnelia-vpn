package com.carnelia.vpn.core

import android.content.Context
import com.carnelia.vpn.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object XrayCoreManager {

    private var xrayProcess: Process? = null
    const val LOCAL_PORT = 10808
    const val LOCAL_PASSWORD = "local-xray-bridge"
    const val LOCAL_METHOD = "chacha20-ietf-poly1305"

    suspend fun startCore(context: Context, config: VpnServerConfig) = withContext(Dispatchers.IO) {
        stopCore() // Ensure clean state

        try {
            // 1. Prepare Executable
            val nativeLibraryDir = context.applicationInfo.nativeLibraryDir
            val executableFile = File(nativeLibraryDir, "libxray_core.so")
            
            if (!executableFile.exists()) {
                throw Exception("Xray Core binary not found at ${executableFile.absolutePath}")
            }
            
            // Critical fix for Android 10+: Execute directly from nativeLibraryDir.
            // Do NOT copy to filesDir (W^X violation leads to Permission Denied).
            val executablePath = executableFile.absolutePath

            // 2. Validate Config (Security Check)
            validateConfig(config)

            // Check for assets
            val geositeFile = File(context.filesDir, "geosite.dat")
            val hasGeosite = geositeFile.exists()
            if (!hasGeosite) {
                AppLogger.log("WARNING: geosite.dat not found in ${context.filesDir}. Advanced domain blocking will be limited.")
            }

            // Check for geoip (for bypass)
            val geoipFile = File(context.filesDir, "geoip.dat")
            val hasGeoip = geoipFile.exists()

            // Read Settings
            val bypassRu = com.carnelia.vpn.utils.PrefsManager.isBypassRuEnabled(context)
            val useMux = com.carnelia.vpn.utils.PrefsManager.isMuxEnabled(context)
            val ipType = com.carnelia.vpn.utils.PrefsManager.getPreferredIpType(context)
            val allowLan = com.carnelia.vpn.utils.PrefsManager.isAllowLanEnabled(context)
            
            // Fragmentation Settings
            val fragEnabled = com.carnelia.vpn.utils.PrefsManager.isFragmentationEnabled(context)
            val fragMode = com.carnelia.vpn.utils.PrefsManager.getFragmentationMode(context)

            val muxTcp = com.carnelia.vpn.utils.PrefsManager.getMuxTcpConcurrency(context)
            val muxUdp = com.carnelia.vpn.utils.PrefsManager.getMuxUdpConcurrency(context)
            val muxQuic = com.carnelia.vpn.utils.PrefsManager.getMuxQuicMode(context)

            // 3. Generate Config
            val configJson = buildConfig(config, hasGeosite, hasGeoip, bypassRu, useMux, ipType, allowLan, fragEnabled, fragMode, muxTcp, muxUdp, muxQuic)
            
            AppLogger.log("Xray Config: $configJson")
            
            val configFile = File(context.filesDir, "xray_config.json")
            configFile.writeText(configJson.toString())

            // 3. Launch Process
            // Pass config file path as argument. Xray usually expects "-config config.json"
            val command = listOf(executablePath, "-config", configFile.absolutePath)
            
            val processBuilder = ProcessBuilder(command)
            processBuilder.directory(context.filesDir)
            processBuilder.redirectErrorStream(true) // Merge stderr into stdout
            
            xrayProcess = processBuilder.start()
            
            // Consume output continuously (Stream Gobbler)
            val stream = xrayProcess!!.inputStream
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    stream.bufferedReader().useLines { lines ->
                        lines.forEach { AppLogger.log("Xray: $it") }
                    }
                } catch (e: Exception) { 
                    AppLogger.log("Xray: Stream closed")
                }
            }

            // Monitor startup
            // Wait a moment for immediate crash (config error)
             Thread.sleep(500)
            if (!xrayProcess!!.isAlive) {
                 throw Exception("Xray process died during startup. See logs for details.")
            }

            AppLogger.log("XrayCoreManager: Started successfully on PID ${getTag()}")

        } catch (e: Exception) {
            AppLogger.error("XrayCoreManager: Start Failed", e)
            stopCore()
            throw e
        }
    }

    fun stopCore() {
        xrayProcess?.destroy()
        xrayProcess = null
    }

    private fun getTag(): String {
         // Requires Java 9+ for pid(), or reflection on older Android
         return "Active"
    }

    private fun buildConfig(
        vpnConfig: VpnServerConfig, 
        hasGeosite: Boolean, 
        hasGeoip: Boolean, 
        bypassRu: Boolean, 
        useMux: Boolean, 
        ipType: String, 
        allowLan: Boolean, 
        fragEnabled: Boolean, 
        fragMode: String,
        muxTcp: Int,
        muxUdp: Int,
        muxQuic: String
    ): JSONObject {
        val root = JSONObject()
        
        // Log
        val log = JSONObject()
        log.put("loglevel", "warning")
        root.put("log", log)

        // DNS
        val dns = JSONObject()
        val servers = JSONArray()
        
        if (VpnGlobalState.isNetShieldEnabled) {
             AppLogger.log("NetShield: Enabled. Using AdGuard DNS.")
             servers.put("94.140.14.14") // AdGuard Default
             servers.put("94.140.15.15")
        } else {
             servers.put("8.8.8.8")
             servers.put("1.1.1.1")
        }
        
        dns.put("servers", servers)
        root.put("dns", dns)

        // Inbound (Local ShadowSocks listener for Tun2Socks)
        val inbounds = JSONArray()
        val localInbound = JSONObject()
        localInbound.put("tag", "proxy")
        localInbound.put("port", LOCAL_PORT)
        localInbound.put("listen", "127.0.0.1")
        localInbound.put("protocol", "shadowsocks")
        
        val settings = JSONObject()
        settings.put("method", LOCAL_METHOD)
        settings.put("password", LOCAL_PASSWORD)
        settings.put("network", "tcp,udp")
        
        localInbound.put("settings", settings)

        // Sniffing (Helps with routing and logging)
        val sniffing = JSONObject()
        val sniffs = JSONArray()
        sniffs.put("http")
        sniffs.put("tls")
        sniffs.put("quic") // V2.0 Stealth: Sniff QUIC to handle it (or block it)
        sniffing.put("enabled", true)
        sniffing.put("destOverride", sniffs)
        localInbound.put("sniffing", sniffing)

        inbounds.put(localInbound)

        // Allow LAN Connections (Additional Inbound)
        if (allowLan) {
            val lanInbound = JSONObject()
            lanInbound.put("tag", "lan_proxy")
            lanInbound.put("port", 10809) // Different port for LAN
            lanInbound.put("listen", "0.0.0.0") // Listen on all interfaces
            lanInbound.put("protocol", "http") // Http proxy is easier for LAN sharing usually
            // Or SOCKS
             val lanSettings = JSONObject()
             lanSettings.put("auth", "noauth")
             lanSettings.put("udp", true)
             lanInbound.put("settings", lanSettings)
             
            inbounds.put(lanInbound)
            AppLogger.log("Allow LAN: Enabled on port 10809 (HTTP)")
        }

        root.put("inbounds", inbounds)

        // Outbound (Real Server)
        val outbounds = JSONArray()
        val realOutbound = JSONObject()
        realOutbound.put("tag", "proxy_out")
        
        // Mux Settings
        if (useMux) {
            val mux = JSONObject()
            mux.put("enabled", true)
            mux.put("concurrency", muxTcp) // Standard Mux concurrency
            
            // These fields require newer Xray core (1.8.0+). 
            // Commenting out temporarily to prevent crashes on older cores.
            // mux.put("xudpConcurrency", muxUdp)
            // mux.put("xudpProxyUDP443", muxQuic)
            
            realOutbound.put("mux", mux)
            AppLogger.log("Mux: Enabled (TCP: $muxTcp)") 
            // AppLogger.log("Mux: Enabled (TCP: $muxTcp, UDP: $muxUdp, QUIC: $muxQuic)")
        }
        
        // Preferred IP Strategy logic moved to routing section below
        
        // Helper to apply fragmentation to outbound
        fun applySockOpt(outboundJson: JSONObject) {
             val streamSettings = outboundJson.optJSONObject("streamSettings") ?: JSONObject()
             val sockopt = JSONObject()
             
             if (fragEnabled) {
                 val fragment = JSONObject()
                 fragment.put("enabled", true)
                 
                 // Zapret-like presets
                 when (fragMode) {
                     "light" -> {
                         // Minimal split, just ClientHello. 
                         fragment.put("packets", "1-1") 
                         fragment.put("length", "100-200") 
                         fragment.put("interval", "10-20") 
                     }
                     "balanced" -> {
                         // Standard bypass (like zapret --split-tls default)
                         fragment.put("packets", "tlshello") // or "1-2"
                         fragment.put("length", "100-200")
                         fragment.put("interval", "10-30")
                     }
                     "aggressive" -> {
                         // Heavy fragmentation for severe blocking
                         fragment.put("packets", "1-5")
                         fragment.put("length", "40-100") 
                         fragment.put("interval", "50-100") 
                     }
                     else -> {
                         fragment.put("packets", "tlshello")
                         fragment.put("length", "100-200")
                         fragment.put("interval", "10-20")
                     }
                 }
                 sockopt.put("fragment", fragment)
                 AppLogger.log("Fragmentation: Applied mode '$fragMode'")
             }
             
             // TFO (TCP Fast Open) - always good to have if supported
             sockopt.put("tcpKeepAliveInterval", 100)
             
             streamSettings.put("sockopt", sockopt)
             outboundJson.put("streamSettings", streamSettings)
        }

        when (vpnConfig.protocol) {
            VpnProtocol.VLESS -> {
                configureVless(realOutbound, vpnConfig)
                applySockOpt(realOutbound)
            }
            VpnProtocol.VMESS -> {
                configureVmess(realOutbound, vpnConfig)
                applySockOpt(realOutbound)
            }
            VpnProtocol.TROJAN -> {
                configureTrojan(realOutbound, vpnConfig)
                applySockOpt(realOutbound)
            }
            VpnProtocol.SHADOWSOCKS, VpnProtocol.OUTLINE -> configureShadowsocks(realOutbound, vpnConfig) // SS doesn't support fragment well usually
            VpnProtocol.WIREGUARD -> configureWireguard(realOutbound, vpnConfig)
            VpnProtocol.SOCKS -> configureSocks(realOutbound, vpnConfig)
            VpnProtocol.HTTP -> configureHttp(realOutbound, vpnConfig)
            else -> throw Exception("Unsupported protocol: ${vpnConfig.protocol}")
        }
        
        outbounds.put(realOutbound)
        
        // Direct Outbound (for safety/failover)
        val direct = JSONObject()
        direct.put("tag", "direct")
        direct.put("protocol", "freedom")
        direct.put("settings", JSONObject())
        outbounds.put(direct)

        // Block Outbound (For NetShield/Stealth)
        val block = JSONObject()
        block.put("tag", "blocked")
        block.put("protocol", "blackhole")
        block.put("settings", JSONObject())
        outbounds.put(block)

        root.put("outbounds", outbounds)
        
        // Routing
        val routing = JSONObject()
        
        // Apply Preferred IP Strategy
        when (ipType) {
            "ipv4" -> routing.put("domainStrategy", "UseIPv4") 
            "ipv6" -> routing.put("domainStrategy", "UseIPv6") 
            else -> routing.put("domainStrategy", "IPIfNonMatch")
        }

        val rules = JSONArray()
        
        // 1. NetShield Blocking Rules (DNS + Routing)
        if (VpnGlobalState.isNetShieldEnabled) {
             AppLogger.log("NetShield: Active. Applying block rules.")
             
             // Block specific ad/tracker domains explicitly
             val item = JSONObject()
             item.put("type", "field")
             item.put("outboundTag", "blocked")
             val domains = JSONArray()
             
             if (hasGeosite) {
                 domains.put("geosite:category-ads-all") // Standard Xray Geosite
             } else {
                 AppLogger.log("NetShield: Scaling back rules. geosite.dat missing.")
             }

             domains.put("domain:googleadservices.com")
             domains.put("domain:doubleclick.net")
             domains.put("domain:facebook.net")
             domains.put("domain:analytics.google.com")
             domains.put("domain:appsflyer.com")
             domains.put("domain:adjust.com")
             domains.put("domain:crashlytics.com")
             domains.put("domain:adcolony.com")
             domains.put("domain:unityads.unity3d.com")
             item.put("domain", domains)
             rules.put(item)
        }
        
        if (VpnGlobalState.isStealthModeEnabled) {
             AppLogger.log("StealthMode: Enabled. Blocking UDP/443 (QUIC).")
             
             // Rule: Block UDP port 443 (QUIC)
             val blockQuic = JSONObject()
             blockQuic.put("type", "field")
             blockQuic.put("port", "443")
             blockQuic.put("network", "udp")
             blockQuic.put("outboundTag", "blocked")
             rules.put(blockQuic)
        }

        // 2. Bypass LAN (Always Private IPs direct)
        // Only if geoip.dat exists, otherwise Xray crashes trying to find "private"
        if (hasGeoip) {
            val privateRule = JSONObject()
            privateRule.put("type", "field")
            privateRule.put("outboundTag", "direct")
            val privateIps = JSONArray()
            privateIps.put("geoip:private")
            privateRule.put("ip", privateIps)
            rules.put(privateRule)
        } else {
             AppLogger.log("XrayConfig: geoip.dat missing, skipping LAN bypass rule.")
        }


        // 3. Smart Routing (Bypass RU)
        if (bypassRu) {
            AppLogger.log("SmartRouting: Bypass RU enabled.")
            val bypassRule = JSONObject()
            bypassRule.put("type", "field")
            bypassRule.put("outboundTag", "direct")
            
            if (hasGeosite) {
                 val domains = JSONArray()
                 domains.put("geosite:ru")
                 domains.put("geosite:yandex")
                 domains.put("geosite:mailru")
                 domains.put("geosite:vk") 
                 domains.put("geosite:cn") 
                 bypassRule.put("domain", domains)
            } else {
                 AppLogger.log("SmartRouting: Geosite missing, domain bypass skipped.")
            }
            
            if (hasGeoip) {
                 val ips = JSONArray()
                 ips.put("geoip:ru")
                 ips.put("geoip:cn")
                 bypassRule.put("ip", ips)
            } else {
                 AppLogger.log("SmartRouting: Geoip missing, IP bypass skipped.")
            }
            
            rules.put(bypassRule)
        }

        
        routing.put("rules", rules)
        root.put("routing", routing)

        return root
    }

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
        AppLogger.log("XrayConfig: Configuring VLESS with UUID: '$uuid'")
        if (uuid.isNullOrEmpty()) {
             throw Exception("VLESS requires a valid UUID, but got empty/null")
        }
        user.put("id", uuid) // UUID
        user.put("encryption", "none")
        user.put("flow", config.config["flow"] ?: "")
        users.put(user)
        server.put("users", users)
        
        vnext.put(server)
        settings.put("vnext", vnext)
        outbound.put("settings", settings)
        
        val streamSettings = JSONObject()
        streamSettings.put("network", config.config["type"] ?: "tcp") // ws, tcp, etc
        streamSettings.put("security", config.config["security"] ?: "none") // tls, reality...
        
        if (config.config["security"] == "reality") {
             val reality = JSONObject()
             reality.put("fingerprint", config.config["fp"] ?: "chrome")
             reality.put("serverName", config.config["sni"] ?: "")
             reality.put("publicKey", config.config["pbk"] ?: "")
             reality.put("shortId", config.config["sid"] ?: "")
             reality.put("show", false)
             streamSettings.put("realitySettings", reality)
        } else if (config.config["security"] == "tls") {
             val tls = JSONObject()
             tls.put("serverName", config.config["sni"] ?: "")
             streamSettings.put("tlsSettings", tls)
        }
        
        // WS Settings
         if (config.config["type"] == "ws") {
             val ws = JSONObject()
             ws.put("path", config.config["path"] ?: "/")
             ws.put("headers", JSONObject().put("Host", config.config["host"] ?: ""))
             streamSettings.put("wsSettings", ws)
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
        user.put("id", config.config["id"] ?: config.password)
        user.put("alterId", (config.config["aid"] ?: "0").toInt())
        user.put("security", "auto")
        users.put(user)
        server.put("users", users)
        
        vnext.put(server)
        settings.put("vnext", vnext)
        outbound.put("settings", settings)
        
        val streamSettings = JSONObject()
        streamSettings.put("network", config.config["net"] ?: "tcp")
        streamSettings.put("security", config.config["tls"] ?: "none")
        
         if (config.config["tls"] == "tls") {
             val tls = JSONObject()
             tls.put("serverName", config.config["sni"] ?: config.config["host"] ?: "")
             streamSettings.put("tlsSettings", tls)
        }
        
        if (config.config["net"] == "ws") {
             val ws = JSONObject()
             ws.put("path", config.config["path"] ?: "/")
             ws.put("headers", JSONObject().put("Host", config.config["host"] ?: ""))
             streamSettings.put("wsSettings", ws)
        }

        outbound.put("streamSettings", streamSettings)
    }

    private fun configureTrojan(outbound: JSONObject, config: VpnServerConfig) {
        outbound.put("protocol", "trojan")
        // ... implementation similar to VLESS
        val settings = JSONObject()
        val servers = JSONArray()
        val server = JSONObject()
        server.put("address", config.host)
        server.put("port", config.port)
        server.put("password", config.password)
        servers.put(server)
        settings.put("servers", servers)
        outbound.put("settings", settings)
        
        val streamSettings = JSONObject()
        streamSettings.put("network", "tcp")
        streamSettings.put("security", "tls")
        val tls = JSONObject()
        tls.put("serverName", config.config["sni"] ?: "")
        streamSettings.put("tlsSettings", tls)
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
        // Xray supports wireguard!
        // We need fields like secretKey, peers, etc.
        // Assuming config map has them
        val settings = JSONObject()
        settings.put("secretKey", config.config["private_key"] ?: "")
        
        val peers = JSONArray()
        val peer = JSONObject()
        peer.put("publicKey", config.config["public_key"] ?: "")
        peer.put("endpoint", "${config.host}:${config.port}")
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
        
        val users = JSONArray()
        if (config.username?.isNotEmpty() == true && config.password?.isNotEmpty() == true) {
             val user = JSONObject()
             user.put("user", config.username)
             user.put("pass", config.password)
             users.put(user)
             server.put("users", users)
        }
        
        servers.put(server)
        settings.put("servers", servers)
        outbound.put("settings", settings)
    }

    private fun configureHttp(outbound: JSONObject, config: VpnServerConfig) {
        // Xray outbound for HTTP ("http" protocol)
        outbound.put("protocol", "http")
        val settings = JSONObject()
        val servers = JSONArray()
        val server = JSONObject()
        server.put("address", config.host)
        server.put("port", config.port)
        
        if (config.username?.isNotEmpty() == true && config.password?.isNotEmpty() == true) {
            val user = JSONObject()
            user.put("user", config.username)
            user.put("pass", config.password)
            val users = JSONArray()
            users.put(user)
            server.put("users", users)
        }
        
        servers.put(server)
        settings.put("servers", servers)
        outbound.put("settings", settings)
    }

    private fun validateConfig(config: VpnServerConfig) {
        // Basic Sanity Check (Always Run)
        if (config.config.values.any { it.contains("your-public-key-placeholder") }) {
            throw Exception("Config Error: 'your-public-key-placeholder' detected. Please replace it with your actual key.")
        }

        if (!VpnGlobalState.isSecureKeyCheckEnabled) return

        AppLogger.log("SecurityCheck: Validating key integrity...")
        
        // Allow anomalous ports if security check is disabled or if user insists (Warn only)
        if (config.port !in 1..65535) {
             AppLogger.log("Security Check: Anomalous Port ${config.port} detected. Proceeding with caution.")
             // throw Exception("Security Check: Invalid Port ${config.port}") // Disabled to allow anomalous ports
        }
        if (config.host.isEmpty()) throw Exception("Security Check: Host is empty")
        
        // Protocol specific validation
        if (config.protocol == VpnProtocol.VLESS) {
            val uuid = config.config["uuid"] ?: config.config["id"]
            if (uuid == null || !uuid.matches(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"))) {
                 throw Exception("Security Check: Invalid VLESS UUID format. Potential malicious config.")
            }
            if (config.config["security"] == "reality") {
                 val pbk = config.config["pbk"] ?: config.config["publicKey"]
                 if (pbk.isNullOrEmpty()) throw Exception("Security Check: Reality requires Public Key")
            }
        }
    }
}
