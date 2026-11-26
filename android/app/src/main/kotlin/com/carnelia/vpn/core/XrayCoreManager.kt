package com.carnelia.vpn.core

import android.content.Context
import com.carnelia.vpn.R
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
    const val LOCAL_HTTP_PORT = 10807  // localhost HTTP proxy for in-app diagnostics
    
    // Constants for internal tags
    private const val TAG_PROXY = "proxy"
    private const val TAG_PROXY_OUT = "proxy_out"
    private const val TAG_DIRECT = "direct"
    /** Freedom outbound that fragments the TLS hello; other outbounds dial through it via sockopt.dialerProxy. */
    const val TAG_FRAG_DIALER = "frag_dialer"
    private const val TAG_BLOCKED = "blocked"

    /** uTLS ClientHello-профиль по умолчанию (chrome/firefox/...), настраивается в Settings. */
    private fun defaultFingerprint(): String =
        PrefsManager.getTlsFingerprint(com.carnelia.vpn.ChimeraApplication.instance)

    /**
     * REALITY mandates TLS 1.3. Some uTLS fingerprints emit a TLS 1.2-only ClientHello —
     * notably "android" (Android11/OkHttp) — which makes the REALITY handshake impossible
     * ("Current fingerprint Android11 does not support TLS 1.3"). Such values, and a blank
     * one, are remapped to a modern TLS 1.3 browser fingerprint.
     */
    private fun realityFingerprint(fp: String): String {
        val tls13Capable = setOf(
            "chrome", "firefox", "edge", "ios", "safari", "360", "qq",
            "random", "randomized", "randomizedalpn", "randomizednoalpn"
        )
        return fp.trim().lowercase().takeIf { it in tls13Capable } ?: "chrome"
    }

    private var lastRealityWarnAt = 0L

    /**
     * Turns the core's silent REALITY-handshake failures into a visible reason. When the
     * outbound cannot authenticate, Xray retries in a tight loop while the UI still shows
     * "connected" with no traffic; this surfaces the cause (wrong key or fingerprint) as a
     * toast and a stored error. Purely informational — it does not touch tunnel lifecycle.
     * Debounced so the reconnect storm raises it at most once per window.
     */
    private fun reportRealityFailure(line: String) {
        val realCert = line.contains("received real certificate")
        val noTls13 = line.contains("does not support TLS 1.3")
        val invalid = line.contains("reality", ignoreCase = true) && line.contains("invalid", ignoreCase = true)
        if (!realCert && !noTls13 && !invalid) return

        val now = System.currentTimeMillis()
        if (now - lastRealityWarnAt < 15000) return
        lastRealityWarnAt = now

        val app = com.carnelia.vpn.ChimeraApplication.instance
        val message = when {
            noTls13 -> app.getString(R.string.xcore_reality_no_tls13)
            realCert -> app.getString(R.string.xcore_reality_real_cert)
            else -> app.getString(R.string.xcore_reality_rejected)
        }
        VpnGlobalState.setError(message)
        try {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(app, message, android.widget.Toast.LENGTH_LONG).show()
            }
        } catch (_: Exception) {}
    }

    suspend fun startCore(context: Context, config: VpnServerConfig) = withContext(Dispatchers.IO) {
        stopCore() // Ensure clean state
        lastRealityWarnAt = 0L // let a fresh connection surface its own failure promptly

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
                            reportRealityFailure(line)
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

    /** Starts Xray as a simple SS:10808 → SOCKS5:10810 bridge for Hysteria2 */
    suspend fun startCoreAsSocks5Bridge(context: Context) = withContext(Dispatchers.IO) {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val executableFile = File(nativeLibDir, "libxray_core.so")
        if (!executableFile.exists()) throw Exception("Xray binary not found")

        val config = JSONObject().apply {
            put("log", JSONObject().put("loglevel", "warning"))
            put("dns", buildDns(context))
            put("inbounds", JSONArray().apply {
                // SS inbound for tun2socks bridge
                put(JSONObject().apply {
                    put("tag", TAG_PROXY)
                    put("port", LOCAL_PORT)
                    put("listen", "127.0.0.1")
                    put("protocol", "shadowsocks")
                    put("settings", JSONObject().apply {
                        put("method", LOCAL_METHOD)
                        put("password", LOCAL_PASSWORD)
                        put("network", "tcp,udp")
                    })
                    put("sniffing", JSONObject().apply {
                        put("enabled", true)
                        put("destOverride", JSONArray().put("http").put("tls").put("quic"))
                    })
                })
                // Localhost HTTP proxy for in-app diagnostics
                put(JSONObject().apply {
                    put("tag", "local_http")
                    put("port", LOCAL_HTTP_PORT)
                    put("listen", "127.0.0.1")
                    put("protocol", "http")
                    put("settings", JSONObject().apply {
                        put("auth", "noauth")
                        put("udp", false)
                    })
                })
            })
            put("outbounds", JSONArray().put(JSONObject().apply {
                put("tag", TAG_PROXY_OUT)
                put("protocol", "socks")
                put("settings", JSONObject().apply {
                    put("servers", JSONArray().put(JSONObject().apply {
                        put("address", "127.0.0.1")
                        put("port", SingboxCoreManager.SOCKS5_PORT) // sing-box SOCKS5 port
                    }))
                })
            }).put(JSONObject().apply {
                put("tag", TAG_DIRECT)
                put("protocol", "freedom")
                put("settings", JSONObject())
            }).put(JSONObject().apply {
                put("tag", TAG_BLOCKED)
                put("protocol", "blackhole")
                put("settings", JSONObject())
            }))
            put("routing", buildRouting(context))
        }

        val configFile = File(context.filesDir, "xray_config.json")
        configFile.writeText(config.toString())

        val pb = ProcessBuilder(listOf(executableFile.absolutePath, "-config", configFile.absolutePath))
        pb.directory(context.filesDir)
        pb.redirectErrorStream(true)
        xrayProcess = pb.start()

        streamJob?.cancel()
        streamJob = xrayScope.launch {
            try {
                xrayProcess!!.inputStream.bufferedReader().use { r ->
                    for (line in r.lineSequence()) {
                        if (!isActive) break
                        AppLogger.log("Xray: $line")
                    }
                }
            } catch (_: Exception) {}
        }

        kotlinx.coroutines.delay(300)
        if (xrayProcess?.isAlive == false) {
            throw Exception("Xray bridge died immediately")
        }
        AppLogger.log("XrayCoreManager: SOCKS5 bridge started (SS:10808 → :10810)")
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
        root.put("routing", buildRouting(context, blockQuic = isFragmenting(context, vpnConfig)))

        return root
    }
    
    private fun buildDns(context: Context): JSONObject {
        val dns = JSONObject()
        val servers = JSONArray()
        
        if (PrefsManager.isProbeDohNeeded(context)) {
             // The probe saw this network's resolver return forged answers: ask over HTTPS instead.
             AppLogger.log("DNS: resolver tampering detected, using DoH")
             servers.put("https+local://1.1.1.1/dns-query")
             servers.put("https+local://8.8.8.8/dns-query")
        } else if (PrefsManager.isNetShieldEnabled(context)) {
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

        // 2. Localhost HTTP proxy for in-app diagnostics (fingerprint check, leak test)
        inbounds.put(JSONObject().apply {
            put("tag", "local_http")
            put("port", LOCAL_HTTP_PORT)
            put("listen", "127.0.0.1")
            put("protocol", "http")
            put("settings", JSONObject().apply {
                put("auth", "noauth")
                put("udp", false)
            })
        })

        // 3. LAN Http Proxy (Optional)
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

    private const val TAG_HOP_ENTRY = "hop_entry"

    /** Мульти-хоп: протоколы, которые Xray может тоннелировать через proxySettings.tag. */
    private fun isChainableProtocol(p: VpnProtocol): Boolean = p in setOf(
        VpnProtocol.VLESS, VpnProtocol.VMESS, VpnProtocol.TROJAN,
        VpnProtocol.SHADOWSOCKS, VpnProtocol.OUTLINE, VpnProtocol.SOCKS, VpnProtocol.HTTP
    )

    /**
     * Если включён мульти-хоп и выбран корректный "входной" сервер — добавляет для него
     * отдельный outbound и связывает основной outbound через proxySettings.tag, так что
     * реальный TCP-коннект идёт local -> entry -> exit -> интернет. Ни entry, ни exit сервер
     * по отдельности не видят одновременно настоящий IP клиента и конечный адрес назначения.
     */
    private fun buildMultiHopEntryOutbound(context: Context, exitConfig: VpnServerConfig): JSONObject? {
        if (exitConfig.protocol == VpnProtocol.FREEDOM) return null // no upstream to chain
        if (!PrefsManager.isMultiHopEnabled(context)) return null
        val entryId = PrefsManager.getMultiHopEntryServerId(context) ?: return null
        if (entryId == exitConfig.id) return null
        val entryConfig = try {
            com.carnelia.vpn.data.ServerRepository(context).getServers().find { it.id == entryId }
        } catch (_: Exception) { null } ?: return null
        if (!isChainableProtocol(entryConfig.protocol) || !isChainableProtocol(exitConfig.protocol)) {
            AppLogger.log("MultiHop: протокол entry/exit не поддерживает цепочку в Xray — пропускаю")
            return null
        }

        val entryOutbound = JSONObject()
        entryOutbound.put("tag", TAG_HOP_ENTRY)
        configureProtocol(entryOutbound, entryConfig)
        applySockOpt(context, entryOutbound)
        if (BlackWallEngine.isEnabled(context)) {
            BlackWallEngine.applyToXrayOutbound(context, entryOutbound, entryConfig)
        }
        AppLogger.log("MultiHop: entry=${entryConfig.name} (${entryConfig.host}) -> exit=${exitConfig.name} (${exitConfig.host})")
        return entryOutbound
    }

    /**
     * XTLS Vision (flow=xtls-rprx-vision) требует прямого владения TCP-соединением для своих
     * трюков с паддингом/сплайсингом — не переживает проксирование через другой outbound
     * (proxySettings.tag). При мульти-хопе снимаем flow с exit-outbound, иначе туннель
     * "поднимается", но реальные данные тихо не идут (не ошибка, а тихий зависон Vision).
     */
    private fun stripVisionFlowForChaining(outbound: JSONObject) {
        try {
            val users = outbound.optJSONObject("settings")?.optJSONArray("vnext")
                ?.optJSONObject(0)?.optJSONArray("users") ?: return
            for (i in 0 until users.length()) {
                val user = users.optJSONObject(i) ?: continue
                if (user.optString("flow").isNotBlank()) {
                    AppLogger.log("MultiHop: снимаю flow=${user.optString("flow")} с exit-outbound (несовместимо с цепочкой)")
                    user.remove("flow")
                }
            }
        } catch (e: Exception) {
            AppLogger.error("MultiHop: stripVisionFlowForChaining failed", e)
        }
    }

    private fun buildOutbounds(context: Context, vpnConfig: VpnServerConfig): JSONArray {
        val outbounds = JSONArray()

        // 1. Main proxy outbound
        val realOutbound = JSONObject()
        realOutbound.put("tag", TAG_PROXY_OUT)
        if (PrefsManager.isMuxEnabled(context)) {
            val mux = JSONObject()
            mux.put("enabled", true)
            mux.put("concurrency", PrefsManager.getMuxTcpConcurrency(context))
            realOutbound.put("mux", mux)
        }
        if (vpnConfig.protocol == VpnProtocol.FREEDOM) {
            // Serverless Black Wall: dial destinations directly and fragment the TLS
            // ClientHello, so DPI cannot read the SNI. No upstream server involved.
            configureFreedom(context, realOutbound)
        } else {
            configureProtocol(realOutbound, vpnConfig)
            applySockOpt(context, realOutbound)
            // Black Wall: apply after protocol config so streamSettings exists
            if (BlackWallEngine.isEnabled(context)) {
                BlackWallEngine.applyToXrayOutbound(context, realOutbound, vpnConfig)
            }
        }

        // Мульти-хоп: если настроен, exit-outbound дозванивается ЧЕРЕЗ entry-outbound
        val entryOutbound = buildMultiHopEntryOutbound(context, vpnConfig)
        if (entryOutbound != null) {
            stripVisionFlowForChaining(realOutbound)
            realOutbound.put("proxySettings", JSONObject().put("tag", TAG_HOP_ENTRY))
            outbounds.put(entryOutbound)
        }
        outbounds.put(realOutbound)

        addFragmentDialerIfUsed(context, outbounds)

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

    /** True when TLS fragmentation is in play for this connection (serverless, Black Wall or manual). */
    private fun isFragmenting(context: Context, vpnConfig: VpnServerConfig): Boolean =
        vpnConfig.protocol == VpnProtocol.FREEDOM || BlackWallEngine.isEnabled(context) ||
            PrefsManager.isFragmentationEnabled(context)

    private fun buildRouting(context: Context, blockQuic: Boolean = false): JSONObject {
        val routing = JSONObject()
        
        // Domain Strategy
        val ipType = PrefsManager.getPreferredIpType(context)
        when (ipType) {
            "ipv4" -> routing.put("domainStrategy", "UseIPv4") 
            "ipv6" -> routing.put("domainStrategy", "UseIPv6") 
            else -> routing.put("domainStrategy", "IPIfNonMatch")
        }

        // Determine effective outbound tag
        val effectiveProxy = TAG_PROXY_OUT

        val rules = JSONArray()

        // QUIC carries the SNI inside UDP where TCP fragmentation cannot reach it. Dropping UDP/443
        // makes browsers and apps fall back to TCP+TLS, which the fragmenter covers.
        if (blockQuic) {
            rules.put(JSONObject()
                .put("type", "field")
                .put("network", "udp")
                .put("port", "443")
                .put("outboundTag", TAG_BLOCKED))
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

         // REALITY crafts its own ClientHello for server-side authentication; sockopt.fragment
         // re-segments it and breaks the handshake. Fragmentation is only safe on plain TLS.
         val isReality = streamSettings.optString("security") == "reality" ||
             streamSettings.has("realitySettings")

         if (!isReality && PrefsManager.isFragmentationEnabled(context)) {
             // Xray ignores sockopt.fragment; fragmentation is done by a freedom outbound
             // that this outbound dials through (see addFragmentDialerIfUsed).
             sockopt.put("dialerProxy", TAG_FRAG_DIALER)
             AppLogger.log("Fragmentation: dialing through $TAG_FRAG_DIALER")
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
            VpnProtocol.HYSTERIA2 -> configureHysteria2(outbound, config)
            VpnProtocol.SOCKS -> configureSocks(outbound, config)
            VpnProtocol.HTTP -> configureInternalHttp(outbound, config)
            else -> throw Exception("Unsupported protocol: ${config.protocol}")
        }
    }

    /**
     * Fragment settings shared by every fragmenting path. Black Wall levels set the base
     * aggressiveness; a strategy measured by BypassProbe on this network beats the preset.
     * Without Black Wall the user's manual fragmentation preferences apply.
     */
    private fun fragmentSettings(context: Context): JSONObject {
        if (!BlackWallEngine.isEnabled(context) && PrefsManager.isFragmentationEnabled(context)) {
            return JSONObject()
                .put("packets", PrefsManager.getFragmentPackets(context))
                .put("length", PrefsManager.getFragmentLength(context))
                .put("interval", PrefsManager.getFragmentInterval(context))
        }
        val (presetLength, presetInterval) = BlackWallEngine.fragmentPreset(context)
        return JSONObject()
            .put("packets", "tlshello")
            .put("length", PrefsManager.getProbedFragmentLength(context).ifEmpty { presetLength })
            .put("interval", PrefsManager.getProbedFragmentInterval(context).ifEmpty { presetInterval })
    }

    /** Appends the fragmenting freedom outbound when any outbound dials through it. */
    private fun addFragmentDialerIfUsed(context: Context, outbounds: JSONArray) {
        val used = (0 until outbounds.length()).any {
            outbounds.getJSONObject(it).optJSONObject("streamSettings")
                ?.optJSONObject("sockopt")?.optString("dialerProxy") == TAG_FRAG_DIALER
        }
        if (!used) return
        outbounds.put(
            JSONObject()
                .put("tag", TAG_FRAG_DIALER)
                .put("protocol", "freedom")
                .put("settings", JSONObject().put("fragment", fragmentSettings(context)))
        )
    }

    /**
     * Serverless bypass outbound: a direct (freedom) connection whose TLS ClientHello is
     * fragmented so DPI cannot match on the SNI. Fragment aggressiveness follows the Black
     * Wall level and is always applied here, since fragmentation is the mode's only purpose.
     */
    private fun configureFreedom(context: Context, outbound: JSONObject) {
        outbound.put("protocol", "freedom")

        val fragment = fragmentSettings(context)
        // Freedom reads `fragment` from its own settings; under sockopt it is silently ignored.
        outbound.put("settings", JSONObject().put("domainStrategy", "UseIP").put("fragment", fragment))
        val sockopt = JSONObject().put("tcpKeepAliveInterval", 300)
        outbound.put("streamSettings", JSONObject().put("sockopt", sockopt))
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
             reality.put("fingerprint", realityFingerprint(fp.ifBlank { defaultFingerprint() }))
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
             tls.put("fingerprint", fp.ifBlank { defaultFingerprint() })
             // allowInsecure removed in Xray 25+; skip cert check by omitting pinnedPeerCertSha256
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
             tls.put("fingerprint", (config.config["fp"] ?: "").ifBlank { defaultFingerprint() })
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
        tls.put("fingerprint", fp.ifBlank { defaultFingerprint() })
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

    private fun configureHysteria2(outbound: JSONObject, config: VpnServerConfig) {
        outbound.put("protocol", "hysteria2")
        val settings = JSONObject()
        val servers = JSONArray()
        val server = JSONObject()
        server.put("address", config.host)
        server.put("port", config.port)
        server.put("password", config.config["password"] ?: "")

        // Obfs salamander (must be inside server object)
        val obfsType = config.config["obfs"] ?: ""
        val obfsPass = config.config["obfs_password"] ?: ""
        if (obfsType.isNotBlank() && obfsPass.isNotBlank()) {
            val obfsObj = JSONObject()
            obfsObj.put("type", obfsType)       // "salamander"
            obfsObj.put("password", obfsPass)
            server.put("obfs", obfsObj)
        }

        // Bandwidth hints (optional)
        val upMbps = config.config["up_mbps"]?.toIntOrNull() ?: 0
        val downMbps = config.config["down_mbps"]?.toIntOrNull() ?: 0
        if (upMbps > 0) server.put("up_mbps", upMbps)
        if (downMbps > 0) server.put("down_mbps", downMbps)

        servers.put(server)
        settings.put("servers", servers)
        outbound.put("settings", settings)

        // Hysteria2 in Xray uses TLS via streamSettings (no network field needed)
        val streamSettings = JSONObject()
        streamSettings.put("security", "tls")
        val tls = JSONObject()
        tls.put("serverName", config.config["sni"] ?: config.host)
        // allowInsecure removed in Xray 25+
        streamSettings.put("tlsSettings", tls)
        outbound.put("streamSettings", streamSettings)
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
        // Serverless bypass has no upstream host/port — it dials destinations directly.
        if (config.protocol == VpnProtocol.FREEDOM) return
        if (config.host.isBlank()) throw Exception("Адрес сервера не указан")
        if (config.port <= 0 || config.port > 65535) throw Exception("Неверный порт: ${config.port}")

        // REALITY public key validation (check both new "pbk" and legacy "publicKey")
        if (config.protocol == VpnProtocol.VLESS && config.config["security"] == "reality") {
            val pbk = (config.config["pbk"] ?: config.config["publicKey"] ?: "").trim()
            if (pbk.isBlank() || !isValidRealityPublicKey(pbk)) {
                throw Exception(
                    com.carnelia.vpn.ChimeraApplication.instance.getString(R.string.xcore_reality_invalid_pbk, pbk)
                )
            }
        }
    }

    /** A valid REALITY public key is a base64url string (no ':' or spaces), at least 30 chars long. */
    private fun isValidRealityPublicKey(pbk: String): Boolean {
        if (pbk.length < 30) return false
        if (pbk.contains(':')) return false
        if (pbk.contains(' ')) return false
        val lower = pbk.lowercase()
        if (lower.startsWith("hash") || lower.startsWith("placeholder") || lower.startsWith("example")) return false
        return true
    }

    // ── ConfigTuner test-core stubs ───────────────────────────────────────
    // Temporary isolated Xray instance used by ConfigTuner to measure latency.
    const val TEST_SOCKS_PORT = 10888
    private var testProcess: Process? = null
    private var testStreamJob: kotlinx.coroutines.Job? = null

    suspend fun startTestCore(
        context: Context,
        config: VpnServerConfig,
        muxEnabled: Boolean = false,
        muxConcurrency: Int = 4,
        fragEnabled: Boolean = false,
        fragMode: String = "balanced"
    ): Boolean = withContext(Dispatchers.IO) {
        stopTestCore()
        val binary = File(context.applicationInfo.nativeLibraryDir, "libxray.so")
        if (!binary.exists()) return@withContext false
        try {
            val cfg = buildTestConfig(context, config)
            val cfgFile = File(context.filesDir, "xray_test_config.json")
            cfgFile.writeText(cfg)
            val pb = ProcessBuilder(binary.absolutePath, "run", "-config", cfgFile.absolutePath)
            pb.redirectErrorStream(true)
            testProcess = pb.start()
            testStreamJob = xrayScope.launch {
                testProcess!!.inputStream.bufferedReader().use { r ->
                    for (line in r.lineSequence()) {
                        if (!isActive) break
                        AppLogger.log("XrayTest: $line")
                    }
                }
            }
            kotlinx.coroutines.delay(600)
            testProcess?.isAlive == true
        } catch (e: Exception) {
            AppLogger.error("XrayCoreManager.startTestCore", e)
            false
        }
    }

    fun stopTestCore() {
        testStreamJob?.cancel(); testStreamJob = null
        testProcess?.destroy(); testProcess = null
    }

    private fun buildTestConfig(context: Context, config: VpnServerConfig): String {
        // Reuse main buildConfig but swap inbound port to TEST_SOCKS_PORT
        val root = buildConfig(context, config)
        val inbounds = root.optJSONArray("inbounds") ?: org.json.JSONArray()
        for (i in 0 until inbounds.length()) {
            val inb = inbounds.getJSONObject(i)
            if (inb.optString("protocol") == "socks") {
                inb.put("port", TEST_SOCKS_PORT)
                break
            }
        }
        root.put("inbounds", inbounds)
        return root.toString(2)
    }
}
