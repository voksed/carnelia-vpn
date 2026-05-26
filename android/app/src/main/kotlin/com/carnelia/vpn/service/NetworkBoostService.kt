package com.carnelia.vpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.IBinder
import com.carnelia.vpn.R
import com.carnelia.vpn.StandaloneToolsActivity
import com.carnelia.vpn.core.DualNetworkManager
import com.carnelia.vpn.utils.AppLogger
import com.carnelia.vpn.utils.PrefsManager

/**
 * NetworkBoostService — v2.4.0
 *
 * Standalone Foreground Service that keeps Wi-Fi and Mobile Data active at the
 * same time WITHOUT requiring a VPN connection.
 *
 * Useful when:
 *  - VPN is OFF but the user wants lower latency / backup path
 *  - Mobile internet is used as an accelerator alongside Wi-Fi
 *
 * Lifecycle controlled from StandaloneToolsActivity or it can be started
 * directly from a Quick Settings tile in the future.
 */
class NetworkBoostService : Service() {

    companion object {
        const val ACTION_START = "com.carnelia.vpn.NETBOOST_START"
        const val ACTION_STOP  = "com.carnelia.vpn.NETBOOST_STOP"
        private const val NOTIFICATION_ID = 42
        private const val CHANNEL_ID = "netboost_channel"

        var isRunning: Boolean = false
            private set
    }

    private lateinit var dualNetworkManager: DualNetworkManager

    override fun onCreate() {
        super.onCreate()
        dualNetworkManager = DualNetworkManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                isRunning = true
                startForeground(NOTIFICATION_ID, buildNotification())
                AppLogger.log("NetworkBoostService: Starting dual-network boost")
                // Override the pref check inside DualNetworkManager by temporarily enabling it
                PrefsManager.setDualNetworkEnabled(this, true)
                dualNetworkManager.start()
                AppLogger.log("NetworkBoostService: Running — faster=${dualNetworkManager.getFasterNetworkLabel()}")
            }
            ACTION_STOP -> stop()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------------------------------------------------------------
    private fun stop() {
        isRunning = false
        dualNetworkManager.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        AppLogger.log("NetworkBoostService: Stopped")
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Network Boost", NotificationManager.IMPORTANCE_LOW)
            )
        }

        val stopIntent = Intent(this, NetworkBoostService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val openIntent = Intent(this, StandaloneToolsActivity::class.java)
        val openPi = PendingIntent.getActivity(this, 1, openIntent, PendingIntent.FLAG_IMMUTABLE)

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle("Network Boost Active")
            .setContentText("WiFi + Mobile running simultaneously")
            .setContentIntent(openPi)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    "Stop",
                    stopPi
                ).build()
            )
            .build()
    }
}
