package com.carnelia.vpn.core

import android.content.Context
import android.content.Intent
import com.carnelia.vpn.data.ServerRepository
import com.carnelia.vpn.service.CarheliaVpnService
import com.carnelia.vpn.utils.AppLogger
import com.carnelia.vpn.utils.PrefsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import kotlin.math.sqrt

data class TuneCandidate(
    val label: String,
    val shortLabel: String,
    val muxEnabled: Boolean,
    val muxConcurrency: Int,
    val fragEnabled: Boolean,
    val fragMode: String
)

data class TuneTestResult(
    val candidate: TuneCandidate,
    val avgLatencyMs: Long,
    val jitterMs: Long,
    val score: Float,
    val isBest: Boolean = false
)

sealed class TunerState {
    object Idle : TunerState()
    data class PreparingVpn(val message: String) : TunerState()
    data class Running(
        val currentIndex: Int,
        val total: Int,
        val currentLabel: String,
        val results: List<TuneTestResult>
    ) : TunerState()
    data class Done(
        val bestResult: TuneTestResult,
        val allResults: List<TuneTestResult>,
        val appliedAutomatically: Boolean,
        val vpnReconnected: Boolean = false
    ) : TunerState()
    object NoServer : TunerState()
}

object ConfigTuner {

    private val _state = MutableStateFlow<TunerState>(TunerState.Idle)
    val state: StateFlow<TunerState> = _state.asStateFlow()

    private var tuneJob: Job? = null

    val candidates = listOf(
        TuneCandidate("Базовый (без надстроек)", "Baseline", false, 0, false, "none"),
        TuneCandidate("Mux × 2", "Mux ×2", true, 2, false, "none"),
        TuneCandidate("Mux × 4", "Mux ×4", true, 4, false, "none"),
        TuneCandidate("Mux × 8", "Mux ×8", true, 8, false, "none"),
        TuneCandidate("Mux × 16", "Mux ×16", true, 16, false, "none"),
        TuneCandidate("Фрагментация: лёгкая", "Frag Light", false, 0, true, "light"),
        TuneCandidate("Фрагментация: сбалансированная", "Frag Balanced", false, 0, true, "balanced"),
        TuneCandidate("Фрагментация: агрессивная", "Frag Aggr.", false, 0, true, "aggressive"),
        TuneCandidate("Mux ×4 + Фрагментация", "Mux+Frag", true, 4, true, "balanced")
    )

    fun startTune(context: Context, scope: CoroutineScope) {
        tuneJob?.cancel()
        _state.value = TunerState.Idle

        tuneJob = scope.launch(Dispatchers.IO) {
            val server = ServerRepository(context).getLastUsedServer()
            if (server == null) {
                _state.value = TunerState.NoServer
                return@launch
            }

            // Auto-disconnect VPN if active
            val wasVpnActive = VpnGlobalState.connectionState.value.let {
                it == ConnectionState.CONNECTED ||
                it == ConnectionState.CONNECTING ||
                it == ConnectionState.RECONNECTING
            }
            if (wasVpnActive) {
                _state.value = TunerState.PreparingVpn("Отключаем VPN для калибровки...")
                AppLogger.log("ConfigTuner: auto-disconnecting VPN before calibration")
                val disconnectIntent = Intent(context, CarheliaVpnService::class.java).apply {
                    action = CarheliaVpnService.ACTION_DISCONNECT
                }
                context.startService(disconnectIntent)
                // Wait up to 10 seconds for disconnect
                withTimeoutOrNull(10_000L) {
                    VpnGlobalState.connectionState.first { it == ConnectionState.DISCONNECTED }
                }
                delay(1200) // Extra wait for xray process cleanup
            }

            if (!isActive) return@launch

            AppLogger.log("ConfigTuner: starting calibration for ${server.host}:${server.port}")
            val results = mutableListOf<TuneTestResult>()

            candidates.forEachIndexed { i, candidate ->
                if (!isActive) return@launch

                _state.value = TunerState.Running(
                    currentIndex = i,
                    total = candidates.size,
                    currentLabel = candidate.label,
                    results = results.toList()
                )

                // Real probe: start a temporary Xray instance with this candidate's settings,
                // then make 2 HTTP requests through its SOCKS5 port to measure actual latency.
                // This correctly reflects fragmentation and mux overhead (unlike plain TCP pings).
                val probes = mutableListOf<Long>()
                val started = XrayCoreManager.startTestCore(
                    context, server,
                    candidate.muxEnabled, candidate.muxConcurrency,
                    candidate.fragEnabled, candidate.fragMode
                )
                if (started) {
                    repeat(2) {
                        if (!isActive) return@repeat
                        val lat = probeViaSocks5()
                        if (lat > 0) probes.add(lat)
                        delay(300)
                    }
                    XrayCoreManager.stopTestCore()
                } else {
                    // Xray binary unavailable — fall back to direct TCP timing
                    AppLogger.log("ConfigTuner: TestCore unavailable, using direct TCP for ${candidate.shortLabel}")
                    repeat(2) {
                        if (!isActive) return@repeat
                        val lat = directTcpProbe(server.host, server.port)
                        if (lat > 0) probes.add(lat)
                        delay(300)
                    }
                }

                val avgLat = if (probes.isEmpty()) -1L else probes.average().toLong()
                val jitter = if (probes.size < 2) 0L else {
                    val mean = probes.average()
                    sqrt(probes.map { d -> (d - mean) * (d - mean) }.average()).toLong()
                }

                val score = computeScore(candidate, avgLat, jitter)
                results.add(TuneTestResult(candidate, avgLat, jitter, score))
                AppLogger.log("ConfigTuner: [${candidate.shortLabel}] avg=${avgLat}ms jitter=${jitter}ms score=${"%.2f".format(score)}")

                delay(300)
            }

            if (!isActive) return@launch

            val best = results.maxByOrNull { it.score } ?: return@launch
            val markedResults = results.map { it.copy(isBest = it.candidate.label == best.candidate.label) }

            // Auto-apply best config to PrefsManager
            PrefsManager.setMuxEnabled(context, best.candidate.muxEnabled)
            if (best.candidate.muxEnabled) {
                PrefsManager.setMuxTcpConcurrency(context, best.candidate.muxConcurrency)
                PrefsManager.setMuxUdpConcurrency(context, best.candidate.muxConcurrency)
            }
            PrefsManager.setFragmentationEnabled(context, best.candidate.fragEnabled)
            if (best.candidate.fragEnabled) {
                PrefsManager.setFragmentationMode(context, best.candidate.fragMode)
            }

            AppLogger.log("ConfigTuner: best=[${best.candidate.label}] applied automatically")

            // Auto-reconnect VPN if it was active before calibration
            if (wasVpnActive && isActive) {
                _state.value = TunerState.PreparingVpn("Восстанавливаем VPN с лучшими настройками...")
                AppLogger.log("ConfigTuner: auto-reconnecting VPN after calibration")
                delay(800)
                val connectIntent = Intent(context, CarheliaVpnService::class.java).apply {
                    action = CarheliaVpnService.ACTION_CONNECT
                    putExtra(CarheliaVpnService.EXTRA_CONFIG, server)
                }
                context.startService(connectIntent)
                delay(1000)
            }

            _state.value = TunerState.Done(best, markedResults, appliedAutomatically = true, vpnReconnected = wasVpnActive)
        }
    }

    fun cancel() {
        XrayCoreManager.stopTestCore()
        tuneJob?.cancel()
        _state.value = TunerState.Idle
    }

    fun reset() {
        _state.value = TunerState.Idle
    }

    /**
     * Makes a real HTTP request through the test Xray SOCKS5 proxy.
     * Returns round-trip latency in ms, or -1 on failure.
     */
    private suspend fun probeViaSocks5(): Long = withContext(Dispatchers.IO) {
        return@withContext try {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", XrayCoreManager.TEST_SOCKS_PORT))
            val conn = URL("http://connectivitycheck.gstatic.com/generate_204").openConnection(proxy) as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = false
            val t0 = System.currentTimeMillis()
            conn.connect()
            conn.responseCode // triggers actual network round-trip
            val lat = System.currentTimeMillis() - t0
            conn.disconnect()
            lat
        } catch (e: Exception) {
            -1L
        }
    }

    /**
     * Direct TCP connect probe — fallback when Xray binary is unavailable.
     */
    private suspend fun directTcpProbe(host: String, port: Int): Long = withContext(Dispatchers.IO) {
        return@withContext try {
            val t0 = System.currentTimeMillis()
            java.net.Socket().use { s -> s.connect(InetSocketAddress(host, port), 5000) }
            System.currentTimeMillis() - t0
        } catch (e: Exception) { -1L }
    }

    private fun computeScore(candidate: TuneCandidate, avgLat: Long, jitter: Long): Float {
        if (avgLat <= 0) return 0f

        val baseScore = 1000f / avgLat.toFloat()
        // Jitter penalty: high jitter reduces score
        val jitterFactor = 1f / (1f + jitter / 60f)

        val configMult = when {
            // Slow/hostile network (>200ms): MUX high concurrency + frag wins
            avgLat > 200 && candidate.muxEnabled && candidate.muxConcurrency >= 8 -> 1.45f
            avgLat > 200 && candidate.fragEnabled && !candidate.muxEnabled -> 1.30f
            avgLat > 200 && candidate.muxEnabled && candidate.fragEnabled -> 1.20f
            avgLat > 200 && !candidate.muxEnabled && !candidate.fragEnabled -> 0.80f
            // Medium network (80-200ms): MUX 4 is sweet spot
            avgLat in 80..200 && candidate.muxEnabled && candidate.muxConcurrency == 4 && !candidate.fragEnabled -> 1.30f
            avgLat in 80..200 && candidate.muxEnabled && candidate.muxConcurrency == 8 && !candidate.fragEnabled -> 1.20f
            avgLat in 80..200 && candidate.fragMode == "light" -> 1.15f
            avgLat in 80..200 && candidate.muxEnabled && candidate.fragEnabled -> 0.95f
            // Fast network (<80ms): minimal overhead
            avgLat < 80 && !candidate.muxEnabled && !candidate.fragEnabled -> 1.35f
            avgLat < 80 && candidate.muxEnabled && candidate.muxConcurrency <= 2 -> 1.15f
            avgLat < 80 && candidate.muxEnabled && candidate.muxConcurrency <= 4 -> 0.90f
            avgLat < 80 && candidate.muxEnabled -> 0.70f
            avgLat < 80 && candidate.fragEnabled -> 0.80f
            // Generic penalties
            candidate.muxEnabled && candidate.fragEnabled -> 0.85f
            else -> 1.0f
        }

        return baseScore * jitterFactor * configMult
    }
}
