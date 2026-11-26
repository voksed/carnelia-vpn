package com.carnelia.vpn.service

import com.carnelia.vpn.R

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.*
import com.carnelia.vpn.core.VpnManager
import com.carnelia.vpn.core.VpnServerConfig
import com.carnelia.vpn.core.ConnectionState
import com.carnelia.vpn.core.VpnStats
import com.carnelia.vpn.utils.AppLogger
import com.carnelia.vpn.utils.PrefsManager
import com.carnelia.vpn.core.VpnGlobalState
import java.util.concurrent.atomic.AtomicLong
import android.service.quicksettings.TileService
import com.carnelia.vpn.core.TrafficStatsManager
import com.carnelia.vpn.core.TrafficSession
import com.carnelia.vpn.core.DualNetworkManager

/**
 * ChimeraWire Service
 * Background service for VPN connections
 */
class ChimeraVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.carnelia.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.carnelia.vpn.DISCONNECT"
        const val ACTION_RECONNECT = "com.carnelia.vpn.RECONNECT"
        const val ACTION_NETWORK_RECONNECT = "com.carnelia.vpn.NETWORK_RECONNECT"
        const val ACTION_REBUILD_INTERFACE = "com.carnelia.vpn.REBUILD_INTERFACE"
        const val ACTION_LOCKDOWN = "com.carnelia.vpn.LOCKDOWN"
        const val EXTRA_CONFIG = "vpn_config"
        const val EXTRA_LOCKDOWN_REASON = "lockdown_reason"
        
        var currentState: ConnectionState = ConnectionState.DISCONNECTED
            private set

        /** Returns true if pbk looks like a valid VLESS REALITY public key */
        fun isValidRealityPbk(pbk: String): Boolean {
            if (pbk.isBlank() || pbk.length < 20) return false
            if (pbk.contains(':') || pbk.contains(' ')) return false
            val lower = pbk.lowercase()
            if (lower.startsWith("hash") || lower.startsWith("placeholder") || lower.startsWith("example")) return false
            return true
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val binder = LocalBinder()
    
    private lateinit var vpnManager: VpnManager
    private var currentInterface: ParcelFileDescriptor? = null
    private val connectionStartTime = AtomicLong(0L)
    private var lastNotificationUpdate = 0L
    private var currentConfig: VpnServerConfig? = null
    private var fallbackAttempts: Int = 0
    private val maxFallbackAttempts = 3
    private lateinit var serverRepository: com.carnelia.vpn.data.ServerRepository
    private lateinit var dualNetworkManager: DualNetworkManager
    private var isLockdown = false

    inner class LocalBinder : Binder() {
        fun getService(): ChimeraVpnService = this@ChimeraVpnService
    }

    override fun onCreate() {
        super.onCreate()
        AppLogger.log("Service: onCreate")
        vpnManager = VpnManager(this, scope)
        serverRepository = com.carnelia.vpn.data.ServerRepository(this)
        dualNetworkManager = DualNetworkManager(this)
        dualNetworkManager.onNetworksChanged = { networks ->
            AppLogger.log("Service: DualNetwork changed — ${networks.size} underlying networks")
            if (currentState == ConnectionState.CONNECTED) {
                setUnderlyingNetworks(networks.toTypedArray())
            }
        }
        setupVpnListeners()

        // Start foreground immediately to prevent crash on Android 8+
        if (android.os.Build.VERSION.SDK_INT >= 34) {
             try {
                startForeground(1, createNotification("Initialized"), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
             } catch (e: Exception) {
                AppLogger.error("Service: Failed to start foreground (Android 14)", e)
             }
        } else {
             startForeground(1, createNotification("Initialized"))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            AppLogger.log("Service: onStartCommand action=${it.action}")
            when (it.action) {
                ACTION_CONNECT -> {
                    // Extract config from intent
                    val config = it.getSerializableExtra(EXTRA_CONFIG) as? VpnServerConfig
                    if (config != null) {
                        currentConfig = config
                        fallbackAttempts = 0
                        // Detailed logging to trace config source
                        AppLogger.log("Service: ACTION_CONNECT proto=${config.protocol.name} name='${config.name}' host=${config.host}:${config.port}")
                        if (config.protocol.name == "VLESS") {
                            val pbk = (config.config["pbk"] ?: config.config["publicKey"] ?: "(null)").trim()
                            val sni = config.config["sni"] ?: "(null)"
                            val sid = config.config["sid"] ?: "(null)"
                            val fp  = config.config["fp"]  ?: "(null)"
                            AppLogger.log("Service: VLESS pbk='$pbk' security=${config.config["security"]} sni='$sni' sid='$sid' fp='$fp'")
                            // Hard guard: reject invalid VLESS REALITY public keys
                            if (config.config["security"] == "reality" && !isValidRealityPbk(pbk)) {
                                AppLogger.error("Service: REJECTED VLESS REALITY config — invalid pbk='$pbk'. Ignoring connect request.")
                                VpnGlobalState.setError(getString(R.string.xsvc_reality_invalid, pbk))
                                return@let
                            }
                        }
                        try {
                            startForeground(1, createNotification("Connecting to ${config.name}..."))
                            vpnManager.connect(config)
                        } catch (e: Exception) {
                            AppLogger.error("Service: Error starting foreground or connecting", e)
                        }
                    } else {
                        AppLogger.error("Service: Config is null in onStartCommand")
                    }
                }
                ACTION_DISCONNECT -> {
                    scope.launch {
                        try {
                            if (isLockdown) {
                                isLockdown = false
                                AppLogger.log("Service: LOCKDOWN снят пользователем")
                            } else {
                                vpnManager.disconnect()
                            }
                        } catch (e: Exception) {
                            AppLogger.error("Service: disconnect error", e)
                        }
                        // Teardown must complete even if the disconnect above cancelled this
                        // scope: without destroying the service the system keeps the VPN
                        // session alive (tun0 + status-bar key stay up, next connect misfires).
                        withContext(NonCancellable) {
                            closeVpnInterface()
                            currentState = ConnectionState.DISCONNECTED
                            VpnGlobalState.updateState(ConnectionState.DISCONNECTED)
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    }
                }
                ACTION_LOCKDOWN -> {
                    val reason = it.getStringExtra(EXTRA_LOCKDOWN_REASON) ?: getString(R.string.xsvc_threat_detected)
                    scope.launch {
                        vpnManager.disconnect() // на случай если был активен обычный туннель
                        isLockdown = true
                        currentState = ConnectionState.LOCKDOWN
                        VpnGlobalState.updateState(ConnectionState.LOCKDOWN)
                        startForeground(1, createNotification(getString(R.string.xsvc_lockdown, reason)))
                        establishLockdownInterface()
                        AppLogger.log("Service: LOCKDOWN активирован — $reason")
                    }
                }
                ACTION_RECONNECT -> {
                    val config = it.getSerializableExtra(EXTRA_CONFIG) as? VpnServerConfig
                    if (config != null) {
                        AppLogger.log("Service: ACTION_RECONNECT to ${config.host}:${config.port}")
                        val pbkInvalid = config.protocol.name == "VLESS" &&
                            config.config["security"] == "reality" &&
                            !isValidRealityPbk((config.config["pbk"] ?: config.config["publicKey"] ?: "").trim())
                        if (pbkInvalid) {
                            val pbk = (config.config["pbk"] ?: config.config["publicKey"] ?: "").trim()
                            AppLogger.error("Service: REJECTED RECONNECT — invalid pbk='$pbk'")
                            VpnGlobalState.setError(getString(R.string.xsvc_reality_invalid_short, pbk))
                        } else {
                            vpnManager.switchServer(config)
                            startForeground(1, createNotification("Switching to ${config.name}..."))
                        }
                    }
                }
                ACTION_NETWORK_RECONNECT -> {
                    // Сменилась дефолтная сеть (Wi-Fi <-> моб). Не рвём VPN насовсем, а
                    // ПЕРЕподключаемся на новой сети, НЕ опуская TUN — то есть без окна утечки:
                    // пока ядро пере-дозванивается, весь трафик заперт в поднятом туннеле.
                    val cfg = currentConfig
                    if (currentState == ConnectionState.CONNECTED && cfg != null) {
                        AppLogger.log("Service: ACTION_NETWORK_RECONNECT — смена сети, бесшовный reconnect")
                        VpnGlobalState.updateState(ConnectionState.RECONNECTING)
                        startForeground(1, createNotification(getString(R.string.xsvc_net_switch)))
                        scope.launch {
                            try {
                                // перепривязка underlying-сетей к новой дефолтной
                                dualNetworkManager.stop(); dualNetworkManager.start()
                                if (dualNetworkManager.isDualActive()) {
                                    setUnderlyingNetworks(dualNetworkManager.getUnderlyingNetworks())
                                }
                                // TUN остаётся поднятым — switchServer пере-дозванивается ядром
                                vpnManager.switchServer(cfg)
                            } catch (e: Exception) {
                                AppLogger.error("Service: network reconnect failed", e)
                            }
                        }
                    }
                }
                ACTION_REBUILD_INTERFACE -> {
                    // Hot-reload TUN interface (re-apply split tunnel / firewall rules).
                    // Xray process keeps running — only TUN fd is rebuilt.
                    if (currentState == ConnectionState.CONNECTED) {
                        AppLogger.log("Service: ACTION_REBUILD_INTERFACE — rebuilding TUN")
                        scope.launch {
                            closeVpnInterface()
                            establishVpnInterface()
                        }
                    }
                }
                else -> {}
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        try {
            runBlocking {
                withContext(NonCancellable) {
                    vpnManager.disconnect()
                }
            }
        } catch (e: Exception) {
            AppLogger.error("Service: onDestroy disconnect failed", e)
        }
        try { vpnManager.destroy() } catch (e: Exception) { AppLogger.error("Service: destroy failed", e) }
        try { scope.cancel() } catch (e: Exception) {}
        try { currentInterface?.close() } catch (e: Exception) {}
        currentInterface = null
    }

    private fun setupVpnListeners() {
        vpnManager.onStateChanged { state ->
            currentState = state
            AppLogger.log("Service: State changed to $state")
            VpnGlobalState.updateState(state)
            
            if (state == ConnectionState.CONNECTED) {
                connectionStartTime.set(System.currentTimeMillis())
            } else if (state == ConnectionState.DISCONNECTED) {
                // Save Statistics
                val endTime = System.currentTimeMillis()
                val startTime = connectionStartTime.get()
                val duration = (endTime - startTime) / 1000
                if (startTime > 0 && duration > 5) { // Only save sessions > 5 seconds
                    val finalStats = VpnGlobalState.stats.value
                    if (finalStats.bytesReceived > 0 || finalStats.bytesSent > 0) {
                        AppLogger.log("Service: Saving session. Duration: ${duration}s, Rx: ${finalStats.bytesReceived}, Tx: ${finalStats.bytesSent}")
                        TrafficStatsManager.saveSession(
                            this@ChimeraVpnService,
                            TrafficSession(startTime, duration, finalStats.bytesReceived, finalStats.bytesSent)
                        )
                    }
                }
                connectionStartTime.set(0L)
            }

            if (android.os.Build.VERSION.SDK_INT >= 24) {
                try {
                    TileService.requestListeningState(this, android.content.ComponentName(this, VpnTileService::class.java))
                } catch (e: Exception) {
                    // Ignore if tile not added
                }
            }

            when (state) {
                ConnectionState.CONNECTED -> {
                    // Start measuring session duration
                    connectionStartTime.set(System.currentTimeMillis())
                    establishVpnInterface()
                    // Dual Network (WiFi + Mobile boost)
                    dualNetworkManager.start()
                    if (dualNetworkManager.isDualActive()) {
                        setUnderlyingNetworks(dualNetworkManager.getUnderlyingNetworks())
                    }
                    // Noise Mode
                    if (PrefsManager.isNoiseModeEnabled(this)) {
                        com.carnelia.vpn.core.NoiseModeManager.start(PrefsManager.getNoiseModeIntensity(this))
                    }
                }
                ConnectionState.CONNECTING -> {
                     // Reset global stats to avoid phantom usage from previous sessions
                     VpnGlobalState.updateStats(VpnStats(0, 0))
                }
                ConnectionState.DISCONNECTED -> {
                    com.carnelia.vpn.core.NoiseModeManager.stop()
                    dualNetworkManager.stop()
                    closeVpnInterface()
                }
                ConnectionState.ERROR -> {
                    com.carnelia.vpn.core.NoiseModeManager.stop()
                    if (PrefsManager.isFallbackEnabled(this)) {
                        tryFallback()
                    }
                    if (PrefsManager.isKillSwitchEnabled(this)) {
                         AppLogger.log("Service: Soft Kill Switch Active - Keeping Interface Up to block traffic")
                         updateNotification(VpnStats(0,0))
                    } else {
                         closeVpnInterface()
                    }
                }
                else -> {}
            }
        }
        
        vpnManager.onStatsChanged { stats ->
            // Update notification with stats
            updateNotification(stats)
            VpnGlobalState.updateStats(stats)
        }
        
        vpnManager.onError { error ->
            // Log error and surface to UI
            AppLogger.error("Service: VPN Error occurred: $error")
            VpnGlobalState.setError(error)
        }
    }

    private fun tryFallback() {
        if (fallbackAttempts >= maxFallbackAttempts) {
            AppLogger.log("Service: Fallback exhausted after $maxFallbackAttempts attempts")
            return
        }
        val servers = serverRepository.getServers()
        if (servers.size < 2) return
        fallbackAttempts++
        AppLogger.log("Service: Fallback attempt $fallbackAttempts — selecting best server by ping")
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Pick fastest reachable server (TCP ping, skip current and UDP-only)
            val udpOnly = setOf(
                com.carnelia.vpn.core.VpnProtocol.HYSTERIA2,
                com.carnelia.vpn.core.VpnProtocol.TUIC,
                com.carnelia.vpn.core.VpnProtocol.WARP,
                com.carnelia.vpn.core.VpnProtocol.WIREGUARD,
                com.carnelia.vpn.core.VpnProtocol.AMNEZIA_WG
            )
            val candidates = servers.filter { it.id != currentConfig?.id }
            val ranked = candidates.map { server ->
                val ping = if (server.protocol in udpOnly) {
                    try {
                        val proc = Runtime.getRuntime().exec(arrayOf("ping", "-c", "1", "-W", "2", server.host))
                        val exit = proc.waitFor()
                        if (exit == 0) {
                            val out = proc.inputStream.bufferedReader().readText()
                            Regex("time[=<]([0-9.]+)").find(out)?.groupValues?.get(1)?.toFloatOrNull()?.toInt() ?: 999
                        } else 9999
                    } catch (_: Exception) { 9999 }
                } else {
                    try {
                        val start = System.currentTimeMillis()
                        java.net.Socket().use { it.connect(java.net.InetSocketAddress(server.host, server.port), 3000) }
                        (System.currentTimeMillis() - start).toInt()
                    } catch (_: Exception) { 9999 }
                }
                server to ping
            }.sortedBy { it.second }

            val nextServer = ranked.firstOrNull { it.second < 9000 }?.first
                ?: candidates.firstOrNull()
                ?: return@launch

            AppLogger.log("Service: Fallback → ${nextServer.name} (ping=${ranked.find { it.first.id == nextServer.id }?.second}ms)")
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                delay(1500)
                currentConfig = nextServer
                serverRepository.setLastUsedServer(nextServer)
                VpnGlobalState.updateState(ConnectionState.RECONNECTING)
                vpnManager.switchServer(nextServer)
                startForeground(1, createNotification("Failover: ${nextServer.name}"))
            }
        }
    }

    private fun establishVpnInterface() {
        try {
            AppLogger.log("Service: Establishing VPN interface...")
            val builder = Builder()

            val isAwg = currentConfig?.protocol == com.carnelia.vpn.core.VpnProtocol.AMNEZIA_WG
            if (isAwg) {
                // AWG: use peer address from WireGuard config so server-side AllowedIPs check passes.
                // Config field "address" is like "10.66.66.13/32" or "10.66.66.13/24".
                val rawAddr = currentConfig?.config?.get("address") ?: "10.111.222.1/32"
                val parts = rawAddr.trim().split("/")
                val ip = parts[0].trim()
                val prefix = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 32
                val mtu = currentConfig?.config?.get("mtu")?.toIntOrNull() ?: 1280
                builder.setMtu(mtu)
                builder.addAddress(ip, prefix)
            } else {
                builder.setMtu(1280)
                builder.addAddress("10.111.222.1", 32)
            }

            builder.addRoute("0.0.0.0", 0)

            // IPv4-only routing: the tun2socks bridge forwards IPv4 exclusively.
            // Adding a ::/0 route would pull app IPv6 traffic into a tunnel that cannot
            // proxy it, producing high TX / near-zero RX. IPv6 is intentionally left
            // to the underlying network here.

            // IPv4-only DNS — IPv6 DNS causes requests to IPv6 destinations that IPv4-only
            // VLESS servers can't proxy, leading to high TX / near-zero RX (requests sent but
            // no responses). Force IPv4 DNS so all resolution stays in IPv4 space.
            val currentDns = if (isAwg) {
                currentConfig?.config?.get("dns")?.split(",")?.firstOrNull()?.trim() ?: "1.1.1.1"
            } else {
                PrefsManager.getDnsServer(this)
            }
            if (currentDns.isNotEmpty()) {
                try { builder.addDnsServer(currentDns) } catch (_: Exception) {}
            }
            if (currentDns != "1.1.1.1") {
                try { builder.addDnsServer("1.1.1.1") } catch (_: Exception) {}
            }
            try { builder.addDnsServer("8.8.8.8") } catch (_: Exception) {}
            
            builder.setSession("ChimeraWire")
            
            // Kill Switch Implementation (Soft)
            // Setting metered can prevent some background syncs on expensive roaming, but for killswitch
            // we rely on the VPN interface remaining up or system settings.
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                builder.setMetered(false) 
            }
            if (android.os.Build.VERSION.SDK_INT >= 21 && PrefsManager.isKillSwitchEnabled(this)) {
                 builder.setBlocking(true)
            }
            
            // Split tunneling. Android allows either an allow-list (only these apps use the VPN,
            // everything else keeps the normal connection) or a disallow-list (everything uses
            // the VPN except these apps), never both, so the two are resolved up front.
            val firewallBlocked = PrefsManager.getFirewallBlockedApps(this)
            val splitOn = PrefsManager.isSplitTunnelingEnabled(this)
            val mode = PrefsManager.getSplitTunnelMode(this) // "allow" or "disallow"
            // The app itself never goes into the tunnel: Xray shares its UID and would loop.
            val selected = if (splitOn) PrefsManager.getSelectedApps(this).filter { it != packageName } else emptyList()
            val allowList = selected.filter { it !in firewallBlocked }

            if (splitOn && mode == "allow" && allowList.isNotEmpty()) {
                AppLogger.log("Service: Split Tunneling (allow) — VPN only for ${allowList.size} apps, rest direct")
                for (pkg in allowList) {
                    try { builder.addAllowedApplication(pkg) } catch (e: Exception) {
                        AppLogger.error("Service: Failed to allow app $pkg", e)
                    }
                }
            } else {
                val excluded = linkedSetOf<String>()
                excluded += firewallBlocked
                if (splitOn && mode == "disallow") excluded += selected
                excluded += packageName
                if (splitOn && selected.isEmpty()) {
                    AppLogger.log("Service: Split Tunneling enabled but list empty — Global Proxy")
                } else if (!splitOn) {
                    AppLogger.log("Service: Split Tunneling disabled (Global Proxy)")
                } else {
                    AppLogger.log("Service: Split Tunneling ($mode) — ${excluded.size - 1} apps bypass the VPN")
                }
                for (pkg in excluded) {
                    try { builder.addDisallowedApplication(pkg) } catch (e: Exception) {
                        AppLogger.error("Service: Failed to exclude app $pkg", e)
                    }
                }
            }

            // HTTP Proxy for basic browsing (Fallback if Tun2Socks is missing)
            /*
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                 try {
                     builder.setHttpProxy(android.net.ProxyInfo.buildDirectProxy("127.0.0.1", 10809))
                     AppLogger.log("Service: HTTP Proxy set to 127.0.0.1:10809")
                 } catch (e: Exception) {
                     AppLogger.error("Service: Failed to set HTTP Proxy", e)
                 }
            }
            */
            
            currentInterface = builder.establish()
            AppLogger.log("Service: Interface established: $currentInterface")
            
            // Notify Manager/Protocol
            currentInterface?.let {
                vpnManager.onInterfaceEstablished(it)
            }
        } catch (e: Exception) {
            AppLogger.error("Service: Failed to establish interface", e)
        }
    }

    /**
     * Полная блокировка сети устройства: TUN-интерфейс забирает себе весь трафик
     * (0.0.0.0/0 + ::/0), но ничего из него не читает — пакеты уходят в никуда.
     * Это останавливает ЛЮБОЕ приложение от выхода в сеть, включая потенциальный
     * RAT/шпион, до того как пользователь разберётся и снимет блокировку вручную.
     */
    private fun establishLockdownInterface() {
        try {
            AppLogger.log("Service: Устанавливаю lockdown-интерфейс")
            val builder = Builder()
            builder.setMtu(1280)
            builder.addAddress("10.255.255.1", 32)
            try { builder.addRoute("0.0.0.0", 0) } catch (e: Exception) { AppLogger.error("Service: lockdown addRoute v4 failed", e) }
            try { builder.addRoute("::", 0) } catch (e: Exception) { AppLogger.error("Service: lockdown addRoute v6 failed", e) }
            builder.setSession("ChimeraWire Protection Mode")
            builder.setBlocking(true)
            currentInterface = builder.establish()
            AppLogger.log("Service: Lockdown-интерфейс поднят — трафик устройства заблокирован")
        } catch (e: Exception) {
            AppLogger.error("Service: Не удалось поднять lockdown-интерфейс", e)
        }
    }

    private fun closeVpnInterface() {
        try {
            AppLogger.log("Service: Closing interface")
            currentInterface?.close()
            currentInterface = null
        } catch (e: Exception) {
            AppLogger.error("Service: Error closing interface", e)
        }
    }

    private fun updateNotification(stats: com.carnelia.vpn.core.VpnStats) {
        val now = System.currentTimeMillis()
        if (now - lastNotificationUpdate < 500) return // debounce 500ms
        lastNotificationUpdate = now
        try {
            val rx = android.text.format.Formatter.formatFileSize(this, stats.bytesReceived)
            val tx = android.text.format.Formatter.formatFileSize(this, stats.bytesSent)
            
            // Calculate Duration
            var durationText = ""
            val startTime = connectionStartTime.get()
            if (startTime > 0) {
                val diff = (now - startTime) / 1000
                val h = diff / 3600
                val m = (diff % 3600) / 60
                val s = diff % 60
                durationText = if (h > 0) String.format("%02d:%02d:%02d • ", h, m, s) else String.format("%02d:%02d • ", m, s)
            }
            
            val text = "$durationText↓ $rx ↑ $tx"
            
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                startForeground(1, createNotification(text), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1, createNotification(text))
            }
        } catch (e: Exception) {
            // Logs might be too frequent here, limiting?
            // For now, catch to prevent crash, maybe log only once or ignore visual update failure
            // AppLogger.error("Service: Notification update failed", e) 
        }
    }

    private fun createNotification(text: String): android.app.Notification {
        val channelId = "vpn_channel"
        val channelName = "VPN Connection"
        val notificationManager = getSystemService(android.app.NotificationManager::class.java)
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(channelId, channelName, android.app.NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        // Action: Disconnect
        val disconnectIntent = Intent(this, ChimeraVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = android.app.PendingIntent.getService(
            this, 0, disconnectIntent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val disconnectAction = android.app.Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            getString(R.string.xsvc_disconnect),
            disconnectPendingIntent
        ).build()

        // Content: Open App
        val contentIntent = Intent(this, com.carnelia.vpn.MainActivity::class.java).apply {
             flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = android.app.PendingIntent.getActivity(
             this, 0, contentIntent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        return android.app.Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("ChimeraWire")
            .setContentText(text)
            .setContentIntent(contentPendingIntent)
            .addAction(disconnectAction)
            .setOngoing(true)
            .build()
    }
}
