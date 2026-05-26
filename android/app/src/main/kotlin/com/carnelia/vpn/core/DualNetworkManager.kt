package com.carnelia.vpn.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import com.carnelia.vpn.utils.AppLogger
import com.carnelia.vpn.utils.PrefsManager
import kotlinx.coroutines.*

/**
 * DualNetworkManager — v2.4.0
 *
 * Keeps the cellular data network active alongside Wi-Fi to:
 * 1. Boost VPN throughput by routing outbound xray sockets through the faster network.
 * 2. Provide instant fallback if one network becomes unavailable.
 * 3. Report the combined downstream speed from both interfaces.
 *
 * Usage:
 *   manager.start(vpnService)   // called inside VpnService, after TUN is up
 *   manager.stop()              // called on disconnect
 *   manager.getUnderlyingNetworks() // pass result to setUnderlyingNetworks()
 */
class DualNetworkManager(private val context: Context) {

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Currently held networks (WiFi + cellular)
    private val activeNetworks = mutableListOf<Network>()

    // Registered callbacks (held to unregister later)
    private var wifiCallback: ConnectivityManager.NetworkCallback? = null
    private var cellCallback: ConnectivityManager.NetworkCallback? = null

    // Caller-supplied lambda: invoked whenever the network set changes
    var onNetworksChanged: ((List<Network>) -> Unit)? = null

    // ---------------------------------------------------------------
    fun start() {
        if (!PrefsManager.isDualNetworkEnabled(context)) return
        AppLogger.log("DualNetwork: Starting")
        requestWifi()
        requestCellular()
    }

    fun stop() {
        scope.cancel()
        unregisterAll()
        activeNetworks.clear()
        AppLogger.log("DualNetwork: Stopped")
    }

    /** Returns the list of underlying networks to pass to VpnService.setUnderlyingNetworks(). */
    fun getUnderlyingNetworks(): Array<Network> = activeNetworks.toTypedArray()

    /** True if both Wi-Fi and Cellular are currently active. */
    fun isDualActive(): Boolean {
        var hasWifi = false
        var hasCell = false
        for (net in activeNetworks) {
            val caps = cm.getNetworkCapabilities(net) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) hasWifi = true
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) hasCell = true
        }
        return hasWifi && hasCell
    }

    /**
     * Returns the name of the "faster" underlying network based on LinkDownstreamBandwidthKbps.
     * Used in the UI status chip.
     */
    fun getFasterNetworkLabel(): String {
        var bestLabel = "WiFi"
        var bestBw = 0
        for (net in activeNetworks) {
            val caps = cm.getNetworkCapabilities(net) ?: continue
            val bw = caps.linkDownstreamBandwidthKbps
            if (bw > bestBw) {
                bestBw = bw
                bestLabel = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                    else -> "Net"
                }
            }
        }
        return bestLabel
    }

    // ---------------------------------------------------------------
    private fun requestWifi() {
        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (!activeNetworks.contains(network)) {
                    activeNetworks.add(network)
                    AppLogger.log("DualNetwork: Wi-Fi available $network (total=${activeNetworks.size})")
                    notifyChanged()
                }
            }
            override fun onLost(network: Network) {
                activeNetworks.remove(network)
                AppLogger.log("DualNetwork: Wi-Fi lost $network (total=${activeNetworks.size})")
                notifyChanged()
            }
        }
        wifiCallback = cb
        try { cm.requestNetwork(req, cb) } catch (e: Exception) {
            AppLogger.error("DualNetwork: requestWifi failed", e)
        }
    }

    private fun requestCellular() {
        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (!activeNetworks.contains(network)) {
                    activeNetworks.add(network)
                    AppLogger.log("DualNetwork: Cellular available $network (total=${activeNetworks.size})")
                    notifyChanged()
                }
            }
            override fun onLost(network: Network) {
                activeNetworks.remove(network)
                AppLogger.log("DualNetwork: Cellular lost $network (total=${activeNetworks.size})")
                notifyChanged()
            }
        }
        cellCallback = cb
        try { cm.requestNetwork(req, cb) } catch (e: Exception) {
            AppLogger.error("DualNetwork: requestCellular failed", e)
        }
    }

    private fun unregisterAll() {
        listOfNotNull(wifiCallback, cellCallback).forEach { cb ->
            try { cm.unregisterNetworkCallback(cb) } catch (_: Exception) {}
        }
        wifiCallback = null
        cellCallback = null
    }

    private fun notifyChanged() {
        onNetworksChanged?.invoke(activeNetworks.toList())
    }
}
