package com.carnelia.vpn.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.carnelia.vpn.data.ServerRepository
import com.carnelia.vpn.utils.AppLogger
import com.carnelia.vpn.utils.PrefsManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            AppLogger.log("BootReceiver: Boot completed received")
            
            if (PrefsManager.isAutoConnectEnabled(context)) {
                AppLogger.log("BootReceiver: Auto-connect enabled, attempting to start VPN")
                
                try {
                    // Check if VPN permission is needed (returns null if ALREADY granted)
                    val vpnIntent = android.net.VpnService.prepare(context)
                    if (vpnIntent != null) {
                        AppLogger.error("BootReceiver: VPN permission missing, cannot auto-connect.")
                        return
                    }

                    val repository = ServerRepository(context)
                    val lastServer = repository.getLastUsedServer()
                    if (lastServer != null) {
                        AppLogger.log("BootReceiver: Connecting to ${lastServer.name}")
                        val serviceIntent = Intent(context, CarheliaVpnService::class.java).apply {
                            action = CarheliaVpnService.ACTION_CONNECT
                            putExtra(CarheliaVpnService.EXTRA_CONFIG, lastServer)
                        }
                        
                        // Starting service from background on Android 8+ requires startForegroundService
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    } else {
                        AppLogger.error("BootReceiver: No last server configured")
                    }
                } catch (e: Exception) {
                    AppLogger.error("BootReceiver: Failed to start VPN", e)
                }
            }
        }
    }
}