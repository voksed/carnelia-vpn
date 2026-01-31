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

/**
 * Carnelia VPN Service
 * Background service for VPN connections
 */
class CarheliaVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.carnelia.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.carnelia.vpn.DISCONNECT"
        const val EXTRA_CONFIG = "vpn_config"
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
        vpnManager = VpnManager(scope)
        setupVpnListeners()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_CONNECT -> {
                    // Extract config from intent
                    val config = it.getSerializableExtra(EXTRA_CONFIG) as? VpnServerConfig
                    if (config != null) {
                        vpnManager.connect(config)
                    }
                }
                ACTION_DISCONNECT -> {
                    vpnManager.disconnect()
                    stopSelf()
                }
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
            when (state) {
                ConnectionState.CONNECTED -> {
                    establishVpnInterface()
                }
                ConnectionState.DISCONNECTED -> {
                    closeVpnInterface()
                }
                ConnectionState.ERROR -> {
                    closeVpnInterface()
                }
                else -> {}
            }
        }
        
        vpnManager.onStatsChanged { stats ->
            // Update notification with stats
            updateNotification(stats)
        }
        
        vpnManager.onError { error ->
            // Log error
            android.util.Log.e("CarheliaVPN", "VPN Error: $error")
        }
    }

    private fun establishVpnInterface() {
        try {
            val builder = Builder()
            builder.addAddress("10.8.0.6", 24)
            builder.addRoute("0.0.0.0", 0)
            builder.addDnsServer("8.8.8.8")
            builder.addDnsServer("8.8.4.4")
            builder.setSession("Carnelia VPN")
            
            currentInterface = builder.establish()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun closeVpnInterface() {
        try {
            currentInterface?.close()
            currentInterface = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateNotification(stats: com.carnelia.vpn.core.VpnStats) {
        // Update foreground notification with byte counters
        val notification = android.app.Notification.Builder(this, "vpn_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Carnelia VPN")
            .setContentText("↓ ${stats.bytesReceived} B ↑ ${stats.bytesSent} B")
            .build()
        
        startForeground(1, notification)
    }
}
