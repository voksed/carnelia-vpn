package com.carnelia.vpn.core.xray

import com.carnelia.vpn.core.VpnServerConfig
import com.carnelia.vpn.core.VpnProtocol
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject

object XrayConfigBuilder {

    private val gson = GsonBuilder().setPrettyPrinting().create()


    fun build(config: VpnServerConfig): String {
        val root = JsonObject()
        
        // Log
        val log = JsonObject()
        log.addProperty("loglevel", "debug") // Increased for debugging
        root.add("log", log)

        // Stats
        root.add("stats", JsonObject())
        
        // Policy
        val policy = JsonObject()
        
        // System Policy
        val systemPolicy = JsonObject()
        systemPolicy.addProperty("statsInboundUplink", true)
        systemPolicy.addProperty("statsInboundDownlink", true)
        systemPolicy.addProperty("statsOutboundUplink", true)
        systemPolicy.addProperty("statsOutboundDownlink", true)
        policy.add("system", systemPolicy)
        
        // Level 0 Policy (Default)
        val level0 = JsonObject()
        level0.addProperty("statsUserUplink", true)
        level0.addProperty("statsUserDownlink", true)
        level0.addProperty("uplinkOnly", 0)
        level0.addProperty("downlinkOnly", 0)
        
        val levels = JsonObject()
        levels.add("0", level0)
        policy.add("levels", levels)

        root.add("policy", policy)

        // DNS
        val dns = JsonObject()
        val servers = JsonArray()
        
        // Custom DNS injection
        if (config.config.containsKey("dns_server")) {
            servers.add(config.config["dns_server"])
        } else {
            servers.add("8.8.8.8")
        }
        
        servers.add("1.1.1.1")
        servers.add("2001:4860:4860::8888") // IPv6 Google
        dns.add("servers", servers)
        root.add("dns", dns)

        // Inbounds
        val inbounds = JsonArray()
        
        // Local SOCKS inbound
        val socksInbound = JsonObject()
        socksInbound.addProperty("tag", "socks")
        socksInbound.addProperty("port", 10808)
        socksInbound.addProperty("protocol", "socks")
        socksInbound.add("settings", JsonObject().apply {
            addProperty("auth", "noauth") // Changed to noauth for system stats
            addProperty("udp", true)
            addProperty("userLevel", 0) // Bind to Level 0 policy
            // No accounts for system stats
        })
        
        // Local HTTP inbound (for ProxyInfo)
        val httpInbound = JsonObject()
        httpInbound.addProperty("tag", "http")
        httpInbound.addProperty("port", 10809)
        httpInbound.addProperty("protocol", "http")
        httpInbound.add("settings", JsonObject().apply {
            addProperty("allowTransparent", false)
            addProperty("userLevel", 0) // Bind to Level 0 policy
            // No auth, no accounts for system stats
        })
        
        // Sniffing
        val sniffing = JsonObject()
        sniffing.addProperty("enabled", true)
        val destOverride = JsonArray()
        destOverride.add("http")
        destOverride.add("tls")
        sniffing.add("destOverride", destOverride)
        socksInbound.add("sniffing", sniffing)
        // httpInbound.add("sniffing", sniffing) // Optional for http
        
        inbounds.add(socksInbound)
        inbounds.add(httpInbound) // Add HTTP inbound
        root.add("inbounds", inbounds)

        // Outbounds
        val outbounds = JsonArray()
        
        // Proxy Outbound
        val proxyOutbound = JsonObject()
        proxyOutbound.addProperty("tag", "proxy")
        val xrayProtocol = getXrayProtocolName(config.protocol)
        proxyOutbound.addProperty("protocol", xrayProtocol)
        
        if (config.protocol == VpnProtocol.WIREGUARD) {
             val settings = JsonObject()
             settings.addProperty("secretKey", config.config["private_key"])
             
             val addressArr = JsonArray()
             val addr = config.config["address"] ?: "10.0.0.2/32"
             addressArr.add(addr)
             settings.add("address", addressArr)
             
             val peers = JsonArray()
             val peer = JsonObject()
             peer.addProperty("publicKey", config.config["public_key"])
             peer.addProperty("endpoint", "${config.host}:${config.port}")
             if (config.config.containsKey("keep_alive")) {
                  peer.addProperty("keepAlive", config.config["keep_alive"]?.toIntOrNull() ?: 25)
             }
             peers.add(peer)
             settings.add("peers", peers)
             settings.addProperty("mtu", config.config["mtu"]?.toIntOrNull() ?: 1280)

             proxyOutbound.add("settings", settings)
             // Wireguard usually doesn't need streamSettings, it's a transport itself
        } else {
            // Standard VMESS/VLESS/TROJAN/SHADOWSOCKS
            val settings = JsonObject()
            val vnext = JsonArray()
            val serverNode = JsonObject()
            serverNode.addProperty("address", config.host)
            serverNode.addProperty("port", config.port)
            
            val users = JsonArray()
            val user = JsonObject()
            
            when (config.protocol) {
                VpnProtocol.VLESS -> {
                    user.addProperty("id", config.config["uuid"])
                    user.addProperty("encryption", "none")
                    user.addProperty("flow", config.config["flow"] ?: "")
                }
                VpnProtocol.VMESS -> {
                    user.addProperty("id", config.config["uuid"])
                    user.addProperty("alterId", config.config["alterId"]?.toIntOrNull() ?: 0)
                    user.addProperty("security", "auto")
                }
                VpnProtocol.TROJAN -> {
                    user.addProperty("password", config.config["password"])
                }
                VpnProtocol.SHADOWSOCKS, VpnProtocol.OUTLINE -> {
                     user.addProperty("password", config.config["password"])
                     user.addProperty("method", config.config["method"])
                     user.addProperty("level", 0)
                }
                else -> {}
            }
            users.add(user)
            serverNode.add("users", users)
            vnext.add(serverNode)
    
            if (config.protocol == VpnProtocol.SHADOWSOCKS || config.protocol == VpnProtocol.OUTLINE) {
                 val serversArr = JsonArray()
                 val ssServer = JsonObject()
                 ssServer.addProperty("address", config.host)
                 ssServer.addProperty("port", config.port)
                 ssServer.addProperty("method", config.config["method"])
                 ssServer.addProperty("password", config.config["password"])
                 ssServer.addProperty("level", 0)
                 serversArr.add(ssServer)
                 settings.add("servers", serversArr)
            } else {
                 settings.add("vnext", vnext)
            }
            
            proxyOutbound.add("settings", settings)
            
            // Stream Settings (Not for WireGuard)
            val streamSettings = JsonObject()
            val network = config.config["type"] ?: "tcp"
            streamSettings.addProperty("network", network)
            
            val security = config.config["security"] ?: "none"
            streamSettings.addProperty("security", security)

            // Anti-censorship: Socket Options
            val sockopt = JsonObject()
            sockopt.addProperty("tcpFastOpen", true) // Bypass handshake blocking
            sockopt.addProperty("tcpNoDelay", true) // Low latency
            sockopt.addProperty("tcpKeepAliveInterval", 15) // NAT keep-alive
            sockopt.addProperty("mark", 255) // Explicit VPN marker
            
            // Fragmentation Switch
            if (config.config["frag_enabled"] == "true") {
                sockopt.addProperty("dialerProxy", "fragment") 
            }
            
            streamSettings.add("sockopt", sockopt)
            
            if (security == "tls") {
                val tlsSettings = JsonObject()
                tlsSettings.addProperty("serverName", config.config["sni"])
                tlsSettings.addProperty("allowInsecure", false)
                tlsSettings.addProperty("fingerprint", config.config["fingerprint"] ?: "chrome")
                // Add ALPN primarily for TLS traffic
                val alpn = JsonArray()
                alpn.add("h2")
                alpn.add("http/1.1")
                tlsSettings.add("alpn", alpn)
                streamSettings.add("tlsSettings", tlsSettings)
            } else if (security == "reality") {
                val realitySettings = JsonObject()
                realitySettings.addProperty("serverName", config.config["serverName"])
                realitySettings.addProperty("publicKey", config.config["publicKey"])
                realitySettings.addProperty("shortId", config.config["shortId"])
                realitySettings.addProperty("fingerprint", config.config["fingerprint"] ?: "chrome")
                realitySettings.addProperty("show", false)
                realitySettings.addProperty("spiderX", "/")
                streamSettings.add("realitySettings", realitySettings)
            }
            
            if (network == "ws") {
                 val wsSettings = JsonObject()
                 wsSettings.addProperty("path", config.config["path"] ?: "/")
                 val headers = JsonObject()
                 headers.addProperty("Host", config.config["host"] ?: config.config["sni"] ?: "")
                 headers.addProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                 wsSettings.add("headers", headers)
                 streamSettings.add("wsSettings", wsSettings)
            } else if (network == "grpc") {
                 val grpcSettings = JsonObject()
                 grpcSettings.addProperty("serviceName", config.config["serviceName"] ?: "gun")
                 streamSettings.add("grpcSettings", grpcSettings)
            }
            
            proxyOutbound.add("streamSettings", streamSettings)
        } // End else WireGuard

        outbounds.add(proxyOutbound)
        
        // Direct Outbound
        val directOutbound = JsonObject()
        directOutbound.addProperty("tag", "direct")
        directOutbound.addProperty("protocol", "freedom")
        directOutbound.add("settings", JsonObject())
        outbounds.add(directOutbound)
        
        // Block Outbound
        val blockOutbound = JsonObject()
        blockOutbound.addProperty("tag", "block")
        blockOutbound.addProperty("protocol", "blackhole")
        blockOutbound.add("settings", JsonObject())
        outbounds.add(blockOutbound)
        
        // Fragment Outbound (Customizable)
        val fragmentOutbound = JsonObject()
        fragmentOutbound.addProperty("tag", "fragment")
        fragmentOutbound.addProperty("protocol", "freedom")
        val fragSettings = JsonObject()
        val fragment = JsonObject()
        
        fragment.addProperty("packets", config.config["frag_packets"] ?: "1-2") 
        fragment.addProperty("length", config.config["frag_length"] ?: "500-1000") 
        fragment.addProperty("interval", config.config["frag_interval"] ?: "1-3")    
        
        fragSettings.add("fragment", fragment)
        fragSettings.addProperty("domainStrategy", "UseIP")
        fragmentOutbound.add("settings", fragSettings)
        
        // Ensure fragment outbound uses the right socket options too
        val fragStream = JsonObject()
        val fragSockopt = JsonObject()
        fragSockopt.addProperty("tcpFastOpen", true)
        fragSockopt.addProperty("mark", 255)
        fragStream.add("sockopt", fragSockopt)
        fragmentOutbound.add("streamSettings", fragStream)
        
        outbounds.add(fragmentOutbound)
        
        root.add("outbounds", outbounds)
        
        // Routing
        val routing = JsonObject()
        routing.addProperty("domainStrategy", "IPIfNonMatch")
        val rules = JsonArray()
        
        // Private IP rule -> Direct
        val privateRule = JsonObject()
        privateRule.addProperty("type", "field")
        privateRule.addProperty("outboundTag", "direct")
        val ipCidr = JsonArray()
        ipCidr.add("10.0.0.0/8")
        ipCidr.add("172.16.0.0/12")
        ipCidr.add("192.168.0.0/16")
        ipCidr.add("127.0.0.0/8")
        privateRule.add("ip", ipCidr)
        rules.add(privateRule)
        
        // RU Bypass (Smart Routing)
        // Hardcoded domains to avoid geoip asset dependency
        if (config.config["bypass_ru"] == "true") {
             val ruRule = JsonObject()
             ruRule.addProperty("type", "field")
             ruRule.addProperty("outboundTag", "direct") // go direct
             val domains = JsonArray()
             domains.add("domain:ru")
             domains.add("domain:su")
             domains.add("domain:xn--p1ai") // .рф punycode
             domains.add("domain:yandex.ru")
             domains.add("domain:vk.com")
             domains.add("domain:mail.ru")
             domains.add("domain:gosuslugi.ru")
             domains.add("domain:sberbank.ru")
             ruRule.add("domain", domains)
             rules.add(ruRule)
        }
        
        routing.add("rules", rules)
        root.add("routing", routing)
        
        return gson.toJson(root)
    }

    private fun getXrayProtocolName(protocol: VpnProtocol): String {
        return when (protocol) {
             VpnProtocol.VLESS -> "vless"
             VpnProtocol.VMESS -> "vmess"
             VpnProtocol.TROJAN -> "trojan"
             VpnProtocol.SHADOWSOCKS, VpnProtocol.OUTLINE -> "shadowsocks"
             VpnProtocol.WIREGUARD -> "wireguard"
             else -> "vless"
        }
    }
}
