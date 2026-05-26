package com.carnelia.vpn.core

import android.content.Context
import com.carnelia.vpn.utils.AppLogger
import com.carnelia.vpn.utils.PrefsManager
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.Socket

/**
 * SmartPortSelector — v2.4.0
 *
 * Solves the "private / corporate / hotel network" problem where non-standard
 * ports are firewalled and the VPN cannot connect.
 *
 * How it works:
 *  1. Takes the user-configured port and a list of fallback ports.
 *  2. Probes each port by attempting a raw TCP handshake to the server host.
 *  3. Returns the first port that succeeds (fast parallel probe).
 *  4. Caches the working port so subsequent connects are instant.
 *  5. Captive portal detection: checks if HTTP redirects happen on port 80;
 *     if yes, sets the captive-portal flag for the UI to show a warning.
 *
 * Common fallback ports in order of priority:
 *   443  – HTTPS, almost never blocked
 *   80   – HTTP, blocked nowhere
 *   8443 – HTTPS alternative
 *   8080 – HTTP proxy, widely open
 *   2053 – Cloudflare DoH, usually open
 *   2087, 2096 – Cloudflare panel ports
 *   587  – SMTP submission, often open as "trusted"
 *   8388 – Common Shadowsocks port
 */
class SmartPortSelector(private val context: Context) {

    companion object {
        // Ports to try in addition to the user-configured one.
        // The user port is always tried first (see selectBestPort).
        val FALLBACK_PORTS = listOf(443, 80, 8443, 8080, 2053, 2087, 2096, 587)
        const val PROBE_TIMEOUT_MS = 2000
    }

    // Cached host → working port from the last successful probe.
    private val cache = mutableMapOf<String, Int>()

    // Set to true when a captive portal is detected.
    var captivePortalDetected: Boolean = false
        private set

    /**
     * Selects the best reachable port for [host] starting with [preferredPort].
     *
     * Returns the first reachable port, or [preferredPort] if nothing is reachable
     * (let xray fail on its own in that case).
     *
     * Must be called from a coroutine / background thread.
     */
    suspend fun selectBestPort(host: String, preferredPort: Int): Int {
        if (!PrefsManager.isSmartPortEnabled(context)) return preferredPort

        // Return cached result immediately
        cache[host]?.let { cached ->
            AppLogger.log("SmartPort: cache hit $host → $cached")
            return cached
        }

        // Build probe list: preferred first, then fallbacks excluding preferred
        val ports = mutableListOf(preferredPort)
        FALLBACK_PORTS.filter { it != preferredPort }.forEach { ports.add(it) }

        AppLogger.log("SmartPort: probing $host with ports $ports")

        // Detect captive portal in background (non-blocking for main flow)
        val captiveJob = CoroutineScope(Dispatchers.IO).launch { detectCaptivePortal(host) }

        // Parallel probe — return the highest priority port that answers
        val result = withTimeoutOrNull(8000L) {
            coroutineScope {
                val deferreds = ports.map { port ->
                    async(Dispatchers.IO) {
                        if (probePort(host, port)) port else null
                    }
                }
                
                var winner: Int? = null
                for (deferred in deferreds) {
                    val r = try { 
                        deferred.await() 
                    } catch (e: kotlinx.coroutines.CancellationException) { 
                        throw e 
                    } catch (_: Exception) { 
                        null 
                    }
                    if (r != null) {
                        winner = r
                        deferreds.forEach { it.cancel() }
                        break
                    }
                }
                winner
            }
        }

        captiveJob.cancel()

        val chosen = result ?: preferredPort
        if (result != null) {
            cache[host] = chosen
            AppLogger.log("SmartPort: selected port $chosen for $host")
        } else {
            AppLogger.log("SmartPort: no reachable port found for $host, using $preferredPort")
        }
        return chosen
    }

    /**
     * Clears the port cache. Call when the user manually changes the server or port.
     */
    fun clearCache() {
        cache.clear()
        captivePortalDetected = false
    }

    // ---------------------------------------------------------------
    private fun probePort(host: String, port: Int): Boolean {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), PROBE_TIMEOUT_MS)
            socket.close()
            AppLogger.log("SmartPort: $host:$port reachable")
            true
        } catch (e: Exception) {
            AppLogger.log("SmartPort: $host:$port unreachable (${e.javaClass.simpleName})")
            false
        }
    }

    /**
     * Detects a captive portal by making a plain-HTTP HEAD to a known URL.
     * If the response is NOT 204/200, a captive portal is probably intercepting.
     */
    private fun detectCaptivePortal(hint: String) {
        try {
            val url = java.net.URL("http://connectivitycheck.gstatic.com/generate_204")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.instanceFollowRedirects = false
            val code = conn.responseCode
            conn.disconnect()
            captivePortalDetected = (code != 204 && code != 200)
            if (captivePortalDetected) {
                AppLogger.log("SmartPort: Captive portal detected (HTTP $code from $hint)")
            }
        } catch (e: Exception) {
            AppLogger.log("SmartPort: Captive portal check failed: ${e.message}")
        }
    }
}
