package com.carnelia.vpn.service

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

/**
 * Carnelia VPN Service
 * Background service for VPN connections
 */
class CarheliaVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.carnelia.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.carnelia.vpn.DISCONNECT"
        const val ACTION_RECONNECT = "com.carnelia.vpn.RECONNECT"
        const val ACTION_REBUILD_INTERFACE = "com.carnelia.vpn.REBUILD_INTERFACE"
        const val EXTRA_CONFIG = "vpn_config"
        
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

    inner class LocalBinder : Binder() {
        fun getService(): CarheliaVpnService = this@CarheliaVpnService
    }

    override fun onCreate() {
        super.onCreate()
        AppLogger.log("Service: onCreate")
        vpnManager = VpnManager(this, scope)
        serverRepository = com.carnelia.vpn.data.ServerRepository(this)
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
                            AppLogger.log("Service: VLESS pbk='$pbk' security=${config.config["security"]}")
                            // Hard guard: reject invalid VLESS REALITY public keys
                            if (config.config["security"] == "reality" && !isValidRealityPbk(pbk)) {
                                AppLogger.error("Service: REJECTED VLESS REALITY config — invalid pbk='$pbk'. Ignoring connect request.")
                                VpnGlobalState.setError("Сервер VLESS REALITY содержит недействительный ключ ($pbk). Удалите сервер и добавьте заново.")
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
                        vpnManager.disconnect()
                        stopSelf()
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
                            VpnGlobalState.setError("Сервер VLESS REALITY содержит недействительный ключ ($pbk).")
                        } else {
                            vpnManager.switchServer(config)
                            startForeground(1, createNotification("Switching to ${config.name}..."))
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
        runBlocking {
            withContext(NonCancellable) {
                vpnManager.disconnect()
            }
        }
        vpnManager.destroy()
        scope.cancel()
        currentInterface?.close()
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
                            this@CarheliaVpnService,
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
        val nextServer = servers.firstOrNull { it.id != currentConfig?.id } ?: return
        fallbackAttempts++
        AppLogger.log("Service: Fallback attempt $fallbackAttempts → ${nextServer.name}")
        scope.launch {
            delay(2000)
            currentConfig = nextServer
            serverRepository.setLastUsedServer(nextServer)
            VpnGlobalState.updateState(ConnectionState.RECONNECTING)
            vpnManager.switchServer(nextServer)
            startForeground(1, createNotification("Fallback: ${nextServer.name}"))
        }
    }

    private fun establishVpnInterface() {
        try {
            AppLogger.log("Service: Establishing VPN interface...")
            val builder = Builder()
            // Optimize MTU for performance/latency (1280 is safe, 1400 might be faster but riskier)
            builder.setMtu(1280)
            
            builder.addAddress("10.111.222.1", 32)
            builder.addRoute("0.0.0.0", 0)
            
            // Ultra-Low Latency DNS configuration (Cloudflare + Quad9)
            // Using closest geo-distributed servers
            val currentDns = PrefsManager.getDnsServer(this)
            if (currentDns.isNotEmpty()) {
                builder.addDnsServer(currentDns) // User selected
            }
            // Fallbacks just in case user DNS fails or is empty/invalid
            if (currentDns != "1.1.1.1") builder.addDnsServer("1.1.1.1")
            
            builder.setSession("Carnelia VPN")
            
            // Kill Switch Implementation (Soft)
            // Setting metered can prevent some background syncs on expensive roaming, but for killswitch
            // we rely on the VPN interface remaining up or system settings.
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                builder.setMetered(false) 
            }
            if (android.os.Build.VERSION.SDK_INT >= 21 && PrefsManager.isKillSwitchEnabled(this)) {
                 builder.setBlocking(true)
            }
            
            // Split Tunneling Logic
            val firewallBlocked = PrefsManager.getFirewallBlockedApps(this)
            val hasFirewall = firewallBlocked.isNotEmpty()

            if (hasFirewall) {
                // Firewall mode: add blocked apps to disallowedApplication.
                // They won't route through VPN tunnel.
                // NOTE: setUnderlyingNetworks(emptyArray()) is intentionally NOT called —
                // it causes Android to mark the VPN as "no connectivity" and ALL apps lose internet.
                AppLogger.log("Service: Firewall mode — disallowing ${firewallBlocked.size} apps")
                for (pkg in firewallBlocked) {
                    try { builder.addDisallowedApplication(pkg) } catch (e: Exception) {}
                }
                // Exclude self to prevent Xray loop
                try { builder.addDisallowedApplication(packageName) } catch (e: Exception) {}
            } else if (PrefsManager.isSplitTunnelingEnabled(this)) {
                val selectedApps = PrefsManager.getSelectedApps(this)
                val mode = PrefsManager.getSplitTunnelMode(this) // "allow" or "disallow"

                if (selectedApps.isNotEmpty()) {
                    AppLogger.log("Service: Split Tunneling ($mode) for ${selectedApps.size} apps")
                    for (pkg in selectedApps) {
                        try {
                            if (mode == "disallow") {
                                builder.addDisallowedApplication(pkg)
                            } else {
                                builder.addAllowedApplication(pkg)
                            }
                        } catch (e: Exception) {
                            AppLogger.error("Service: Failed to $mode app $pkg", e)
                        }
                    }
                } else {
                    AppLogger.log("Service: Split Tunneling active but list empty ($mode) (Proxying all).")
                }

                // Exclude self to avoid Xray Loop (since Xray runs under app's UID)
                if (mode == "disallow" && !selectedApps.contains(packageName)) {
                     try {
                        builder.addDisallowedApplication(packageName)
                    } catch (e: Exception) { }
                }

            } else {
                AppLogger.log("Service: Split Tunneling disabled (Global Proxy)")
                try {
                    // Critical: Exclude self to allow Xray process to reach internet directly
                    builder.addDisallowedApplication(packageName)
                } catch (e: Exception) {
                    AppLogger.error("Service: Failed to exclude self from VPN", e)
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
        val disconnectIntent = Intent(this, CarheliaVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = android.app.PendingIntent.getService(
            this, 0, disconnectIntent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val disconnectAction = android.app.Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            "Отключить",
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
            .setContentTitle("Carnelia VPN")
            .setContentText(text)
            .setContentIntent(contentPendingIntent)
            .addAction(disconnectAction)
            .setOngoing(true)
            .build()
    }
}
