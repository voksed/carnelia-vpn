package com.carnelia.vpn.core

import com.carnelia.vpn.utils.AppLogger
import kotlinx.coroutines.*
import kotlin.random.Random

/**
 * Noise Mode — generates fake background traffic to make VPN usage
 * indistinguishable from normal browsing.
 * No extra dependencies: uses java.net.URL (already available).
 */
object NoiseModeManager {

    private var noiseJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val noiseHosts = listOf(
        "https://www.example.com",
        "https://www.cloudflare.com/robots.txt",
        "https://github.com/robots.txt",
        "https://www.wikipedia.org/robots.txt",
        "https://www.reddit.com/robots.txt",
        "https://www.amazon.com/robots.txt",
        "https://www.microsoft.com/robots.txt",
        "https://httpbin.org/get",
        "https://www.apple.com/robots.txt",
        "https://www.netflix.com/robots.txt"
    )

    fun start(intensity: String) {
        if (noiseJob?.isActive == true) return
        val baseDelay = when (intensity) {
            "medium" -> 10_000L
            "high"   -> 3_000L
            else     -> 30_000L // "low" default
        }
        AppLogger.log("NoiseMode: Starting with intensity=$intensity (delay=${baseDelay}ms)")
        noiseJob = scope.launch {
            while (isActive) {
                val url = noiseHosts.random()
                try {
                    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                    conn.connect()
                    conn.inputStream?.use { it.read() } // read 1 byte to complete handshake
                    conn.disconnect()
                } catch (e: Exception) {
                    // Expected — server may refuse, timeout etc.
                }
                val jitter = Random.nextLong(-baseDelay / 4, baseDelay / 4)
                delay((baseDelay + jitter).coerceAtLeast(1000L))
            }
        }
    }

    fun stop() {
        if (noiseJob?.isActive == true) {
            AppLogger.log("NoiseMode: Stopping")
            noiseJob?.cancel()
            noiseJob = null
        }
    }

    val isRunning: Boolean get() = noiseJob?.isActive == true
}
