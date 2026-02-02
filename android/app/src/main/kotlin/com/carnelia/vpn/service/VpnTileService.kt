package com.carnelia.vpn.service

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.carnelia.vpn.core.ConnectionState
import com.carnelia.vpn.data.ServerRepository
import com.carnelia.vpn.MainActivity
import com.carnelia.vpn.utils.AppLogger

@RequiresApi(Build.VERSION_CODES.N)
class VpnTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        
        // Check actual service state (requires CarheliaVpnService.currentState to be accessible)
        val state = CarheliaVpnService.currentState
        
        when (state) {
            ConnectionState.CONNECTED -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "Connected" // Could be server name but simple is better for QuickSettings
                tile.icon = android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_lock_lock) // Or custom icon
            }
            ConnectionState.CONNECTING, ConnectionState.DISCONNECTING -> {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.label = "Loading..."
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "Carnelia VPN"
                tile.icon = android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_lock_idle_lock) // Or custom icon
            }
        }
        
        try {
            tile.updateTile()
        } catch (e: Exception) {
            // Ignore if tile update fails (e.g. not listening yet fully)
        }
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return
        
        if (tile.state == Tile.STATE_UNAVAILABLE) return

        // Check for VPN permissions first
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            // Permission missing, must open Activity
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivityAndCollapse(intent)
            return
        }

        if (tile.state == Tile.STATE_INACTIVE) {
            // Start VPN
            // We need a config. Get last used.
            val repository = ServerRepository(this)
            val lastServer = repository.getLastUsedServer()
            
            if (lastServer != null) {
                // Connect
                val intent = Intent(this, CarheliaVpnService::class.java)
                intent.action = CarheliaVpnService.ACTION_CONNECT
                intent.putExtra(CarheliaVpnService.EXTRA_CONFIG, lastServer)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                
                // Optimistic UI update
                tile.state = Tile.STATE_UNAVAILABLE
                tile.label = "Connecting..."
                tile.updateTile()
            } else {
                // No config, open app
                val intent = Intent(this, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivityAndCollapse(intent)
            }
        } else {
            // Stop VPN
            val intent = Intent(this, CarheliaVpnService::class.java)
            intent.action = CarheliaVpnService.ACTION_DISCONNECT
            startService(intent)
            
             // Optimistic UI update
            tile.state = Tile.STATE_UNAVAILABLE
            tile.label = "Disconnecting..."
            tile.updateTile()
        }
    }
}
