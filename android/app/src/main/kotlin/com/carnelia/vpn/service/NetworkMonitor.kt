package com.carnelia.vpn.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.content.Intent
import android.os.Build
import com.carnelia.vpn.utils.PrefsManager
import com.carnelia.vpn.utils.AppLogger
import com.carnelia.vpn.data.ServerRepository
import com.carnelia.vpn.core.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NetworkMonitor(private val context: Context) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = CoroutineScope(Dispatchers.IO)

    fun startMonitoring() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                AppLogger.log("NetworkMonitor: Network available")
                checkAndConnect(network)
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                AppLogger.log("NetworkMonitor: Network lost")
            }
        })
    }

    private fun checkAndConnect(network: Network) {
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return
        
        // Avoid reacting to the VPN network itself!
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            return
        }
        
        // If VPN is already connected or connecting, do nothing?
        // Wait, if we switch networks, we might be technically connected but using old network.
        // But CarheliaVpnService handles that? 
        // We only want to AUTO START if it's currently STOPPED.
        if (CarheliaVpnService.currentState == ConnectionState.CONNECTED || 
            CarheliaVpnService.currentState == ConnectionState.CONNECTING) {
             return
        }

        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

        if (isWifi && PrefsManager.isAutoConnectWifiEnabled(context)) {
            AppLogger.log("NetworkMonitor: Auto-connecting on Wi-Fi")
            triggerVpn()
        } else if (isCellular && PrefsManager.isAutoConnectMobileEnabled(context)) {
            AppLogger.log("NetworkMonitor: Auto-connecting on Mobile")
            triggerVpn()
        }
    }

    private fun triggerVpn() {
        scope.launch {
            val repository = ServerRepository(context)
            val lastServer = repository.getLastUsedServer()
            if (lastServer != null) {
                val intent = Intent(context, CarheliaVpnService::class.java)
                intent.action = CarheliaVpnService.ACTION_CONNECT
                intent.putExtra(CarheliaVpnService.EXTRA_CONFIG, lastServer)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                 AppLogger.log("NetworkMonitor: No last server to auto-connect")
            }
        }
    }
}
