package com.carnelia.vpn.core

import android.content.Context
import com.carnelia.vpn.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

            // 2. Generate Config
            val configJson = buildConfig(config)
            val configFile = File(context.filesDir, "xray_config.json")
            configFile.writeText(configJson.toString())

            // 3. Launch Process
            // Pass config file path as argument. Xray usually expects "-config config.json"
            val command = listOf(executablePath, "-config", configFile.absolutePath)
            
            val processBuilder = ProcessBuilder(command)
            processBuilder.directory(context.filesDir)
            
            // Redirect output to logcat or null to avoid buffer filling
            // processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT)
            // processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT)
            
            // Initialize config via env var if needed (XRAY_LOCATION_ASSET), 
            // but file parameter is safer.
            
            xrayProcess = processBuilder.start()
            
            // Monitor startup (simple check)
            if (!xrayProcess!!.isAlive) {
                 val error = xrayProcess!!.inputStream.bufferedReader().readText()
                 throw Exception("Xray exited immediately: $error")
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

    private fun buildConfig(vpnConfig: VpnServerConfig): JSONObject {
        val root = JSONObject()
        
        // Log
        val log = JSONObject()
        log.put("loglevel", "warning")
        root.put("log", log)

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
        inbounds.put(localInbound)
        root.put("inbounds", inbounds)

        // Outbound (Real Server)
        val outbounds = JSONArray()
        val realOutbound = JSONObject()
        realOutbound.put("tag", "proxy_out")
        
        when (vpnConfig.protocol) {
            VpnProtocol.VLESS -> configureVless(realOutbound, vpnConfig)
            VpnProtocol.VMESS -> configureVmess(realOutbound, vpnConfig)
            VpnProtocol.TROJAN -> configureTrojan(realOutbound, vpnConfig)
            VpnProtocol.SHADOWSOCKS, VpnProtocol.OUTLINE -> configureShadowsocks(realOutbound, vpnConfig)
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

        root.put("outbounds", outbounds)
        
        // Routing (Simple)
        val routing = JSONObject()
        routing.put("domainStrategy", "AsIs")
        val rules = JSONArray()
        // Here we could add split tunneling rules from 'traffic control' settings?
        // For now, route everything to proxy_out
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
        user.put("id", config.config["id"] ?: config.password) // UUID
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
}
