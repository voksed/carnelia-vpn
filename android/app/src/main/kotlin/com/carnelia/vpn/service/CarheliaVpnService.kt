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

import android.service.quicksettings.TileService

/**
 * Carnelia VPN Service
 * Background service for VPN connections
 */
class CarheliaVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.carnelia.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.carnelia.vpn.DISCONNECT"
        const val EXTRA_CONFIG = "vpn_config"
        
        var currentState: ConnectionState = ConnectionState.DISCONNECTED
            private set
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val binder = LocalBinder()
    
    private lateinit var vpnManager: VpnManager
    private var currentInterface: ParcelFileDescriptor? = null

    inner class LocalBinder : Binder() {
        fun getService(): CarheliaVpnService = this@CarheliaVpnService
    }

    override fun onCreate() {
        super.onCreate()
        AppLogger.log("Service: onCreate")
        vpnManager = VpnManager(this, scope)
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
                    vpnManager.disconnect()
                    stopSelf()
                }
                else -> {}
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        vpnManager.disconnect()
        vpnManager.destroy()
        scope.cancel()
        currentInterface?.close()
    }

    private fun setupVpnListeners() {
        vpnManager.onStateChanged { state ->
            currentState = state
            AppLogger.log("Service: State changed to $state")
            VpnGlobalState.updateState(state)
            
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                try {
                    TileService.requestListeningState(this, android.content.ComponentName(this, VpnTileService::class.java))
                } catch (e: Exception) {
                    // Ignore if tile not added
                }
            }

            when (state) {
                ConnectionState.CONNECTED -> {
                    establishVpnInterface()
                }
                ConnectionState.DISCONNECTED -> {
                    // Soft Kill Switch Logic:
                    // Only close VPN Interface if it was a manual disconnect or KS is disabled.
                    // However, we don't easily know if it's manual here unless we track intent.
                    // Simpler logic: If disconnected, we usually want to close to allow normal internet.
                    // But if KS is on, we want to BLOCK.
                    // The issue is: If we keep the interface open with no backend, packets die (KS works).
                    // But how does the user RECONNECT? They need to click Connect in app.
                    
                    // If we assume DISCONNECTED means "Stopped completely", we should close.
                    // If it was "Reconnecting" (ERROR -> RETRY), the state would be different?
                    // VpnManager handles retries. If it emits DISCONNECTED, it gave up.
                    
                    closeVpnInterface()
                }
                ConnectionState.ERROR -> {
                    // Error happened. 
                    if (PrefsManager.isKillSwitchEnabled(this)) {
                         AppLogger.log("Service: Soft Kill Switch Active - Keeping Interface Up to block traffic")
                         // Do NOT close interface. Traffic goes to blackhole.
                         updateNotification(VpnStats(0,0)) // Just update notification
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
            // Log error
            AppLogger.error("Service: VPN Error occurred: $error")
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
            builder.addDnsServer("1.1.1.1") // Cloudflare (Fastest global)
            builder.addDnsServer("1.0.0.1") // Cloudflare Backup
            builder.addDnsServer("9.9.9.9") // Quad9 (High performance fallback)
            
            builder.setSession("Carnelia VPN")
            
            // Kill Switch Implementation (Soft)
            // Setting metered can prevent some background syncs on expensive roaming, but for killswitch
            // we rely on the VPN interface remaining up or system settings.
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                builder.setMetered(false) 
            }
            
            // Split Tunneling Logic
            if (PrefsManager.isSplitTunnelingEnabled(this)) {
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
                
                // If mode is "disallow", we are effectively proxying "all except selected".
                // We should also exclude ourselves if we aren't in the list?
                // Actually, just standard practice to exclude self to avoid loop if possible.
                if (mode == "disallow" && !selectedApps.contains(packageName)) {
                     try {
                        builder.addDisallowedApplication(packageName)
                    } catch (e: Exception) { }
                }

            } else {
                AppLogger.log("Service: Split Tunneling disabled (Global Proxy)")
                try {
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
        try {
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                startForeground(1, createNotification("↓ ${stats.bytesReceived} B ↑ ${stats.bytesSent} B"), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1, createNotification("↓ ${stats.bytesReceived} B ↑ ${stats.bytesSent} B"))
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
