package com.carnelia.vpn.ui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.app.AppOpsManager
import android.content.Intent
import android.net.TrafficStats
import android.app.usage.NetworkStatsManager
import android.app.usage.NetworkStats
import android.net.ConnectivityManager
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carnelia.vpn.R
import com.carnelia.vpn.core.VpnGlobalState
import com.carnelia.vpn.core.ConnectionState
import com.carnelia.vpn.utils.PrefsManager
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.*

data class AppTrafficEntry(
    val uid: Int,
    val packageName: String,
    val label: String,
    val rxBytes: Long,
    val txBytes: Long,
    val iconBitmap: ImageBitmap?
)

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "${bytes} Б"
    if (bytes < 1024 * 1024) return "${"%.1f".format(bytes / 1024f)} КБ"
    if (bytes < 1024 * 1024 * 1024) return "${"%.1f".format(bytes / 1024f / 1024f)} МБ"
    return "${"%.2f".format(bytes / 1024f / 1024f / 1024f)} ГБ"
}

// ─── Routing state ───────────────────────────────────────────────────────────

enum class AppRoutingState {
    VPN_PROXY,      // goes through VPN tunnel
    DIRECT_BYPASS,  // bypasses VPN, direct internet
    FIREWALL_BLOCKED // no internet at all
}

fun getAppRoutingState(context: Context, pkg: String): AppRoutingState {
    if (pkg in PrefsManager.getFirewallBlockedApps(context)) return AppRoutingState.FIREWALL_BLOCKED
    val splitEnabled = PrefsManager.isSplitTunnelingEnabled(context)
    val mode = PrefsManager.getSplitTunnelMode(context)
    val selected = PrefsManager.getSelectedApps(context)
    if (splitEnabled) {
        if (mode == "disallow" && pkg in selected) return AppRoutingState.DIRECT_BYPASS
        if (mode == "allow" && pkg !in selected) return AppRoutingState.DIRECT_BYPASS
    }
    return AppRoutingState.VPN_PROXY
}

// ─── Xray access log parser ─────────────────────────────────────────────────
// Format: 2025/01/01 00:00:00 [Info] 127.0.0.1:PORT accepted tcp:HOST:PORT [proxy_out] email

data class XrayConnection(val dest: String, val tag: String, val time: Long)

fun parseXrayAccessLog(context: Context, limitLines: Int = 500): List<XrayConnection> {
    val file = java.io.File(context.filesDir, "xray_access.log")
    if (!file.exists()) return emptyList()
    return try {
        file.readLines()
            .takeLast(limitLines)
            .reversed()
            .mapNotNull { line ->
                // e.g.: 2025/01/01 12:34:56 [Info] 127.0.0.1:40000 accepted tcp:example.com:443 [proxy_out]
                val m = Regex("""accepted (?:tcp|udp):([^\s]+)\s+\[([^\]]+)\]""").find(line)
                    ?: return@mapNotNull null
                val dest = m.groupValues[1]  // host:port
                val tag = m.groupValues[2]
                XrayConnection(dest = dest, tag = tag, time = System.currentTimeMillis())
            }
    } catch (e: Exception) { emptyList() }
}

// Returns top-N unique destination hosts from access log
fun getTopDestinations(context: Context, top: Int = 6): List<Pair<String, Int>> {
    val conns = parseXrayAccessLog(context, 1000)
    return conns
        .groupBy { it.dest.substringBeforeLast(":") } // strip port
        .mapValues { it.value.size }
        .entries
        .sortedByDescending { it.value }
        .take(top)
        .map { it.key to it.value }
}

// Add a domain block rule to xray routing (stored in prefs, applied on rebuild)
fun addBlockedDomain(context: Context, domain: String) {
    val current = PrefsManager.getBlockedDomains(context).toMutableSet()
    current.add(domain)
    PrefsManager.setBlockedDomains(context, current)
}

fun removeBlockedDomain(context: Context, domain: String) {
    val current = PrefsManager.getBlockedDomains(context).toMutableSet()
    current.remove(domain)
    PrefsManager.setBlockedDomains(context, current)
}

/** Returns total device rx bytes, or -1 if TrafficStats is completely unsupported */
fun getDeviceTotalRxBytes(): Long = TrafficStats.getTotalRxBytes()

fun isUsageStatsGranted(context: Context): Boolean {
    return try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) { false }
}

fun getTopTrafficApps(context: Context, limit: Int = 20): List<AppTrafficEntry> {
    val pm = context.packageManager
    val myUid = android.os.Process.myUid()

    // Try NetworkStatsManager first (Android 6+, more reliable on Android 12+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        try {
            val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
            val uidMap = mutableMapOf<Int, Long>() // uid -> total bytes

            // Query WIFI + MOBILE for last 7 days
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 7L * 24 * 60 * 60 * 1000

            for (networkType in listOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) {
                try {
                    val stats: NetworkStats = nsm.querySummary(networkType, null, startTime, endTime)
                    val bucket = NetworkStats.Bucket()
                    while (stats.hasNextBucket()) {
                        stats.getNextBucket(bucket)
                        val uid = bucket.uid
                        if (uid == myUid || uid <= 0) continue
                        val total = bucket.rxBytes + bucket.txBytes
                        if (total > 0) uidMap[uid] = (uidMap[uid] ?: 0L) + total
                    }
                    stats.close()
                } catch (e: Exception) { /* skip network type */ }
            }

            if (uidMap.isNotEmpty()) {
                return uidMap.entries
                    .sortedByDescending { it.value }
                    .take(limit)
                    .mapNotNull { (uid, total) ->
                        val pkgs = pm.getPackagesForUid(uid) ?: return@mapNotNull null
                        val pkg = pkgs.firstOrNull() ?: return@mapNotNull null
                        val appInfo = try { pm.getApplicationInfo(pkg, 0) } catch (e: Exception) { return@mapNotNull null }
                        val label = pm.getApplicationLabel(appInfo).toString()
                        val iconBmp = try { drawableToBitmap(pm.getApplicationIcon(pkg))?.asImageBitmap() } catch (e: Exception) { null }
                        // split total roughly 70/30 rx/tx for display (real split not available without per-UID query)
                        val rx = TrafficStats.getUidRxBytes(uid).let { if (it < 0L) (total * 0.7).toLong() else it }
                        val tx = TrafficStats.getUidTxBytes(uid).let { if (it < 0L) (total * 0.3).toLong() else it }
                        AppTrafficEntry(uid, pkg, label, rx, tx, iconBmp)
                    }
            }
        } catch (e: SecurityException) {
            // PACKAGE_USAGE_STATS not granted — fall through to TrafficStats
        } catch (e: Exception) { /* fall through */ }
    }

    // Fallback: TrafficStats per-UID (works on most devices)
    return try {
        pm.getInstalledApplications(0)
            .mapNotNull { appInfo ->
                val uid = appInfo.uid
                if (uid == myUid) return@mapNotNull null
                val rx = TrafficStats.getUidRxBytes(uid)
                val tx = TrafficStats.getUidTxBytes(uid)
                val rxSafe = if (rx < 0L) 0L else rx
                val txSafe = if (tx < 0L) 0L else tx
                if (rx < 0L && tx < 0L) return@mapNotNull null
                if (rxSafe == 0L && txSafe == 0L) return@mapNotNull null
                val label = pm.getApplicationLabel(appInfo).toString()
                val iconBmp = try { drawableToBitmap(pm.getApplicationIcon(appInfo.packageName))?.asImageBitmap() } catch (e: Exception) { null }
                AppTrafficEntry(uid, appInfo.packageName, label, rxSafe, txSafe, iconBmp)
            }
            .sortedByDescending { it.rxBytes + it.txBytes }
            .take(limit)
    } catch (e: Exception) { emptyList() }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap? {
    return try {
        val bmp = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, 48, 48)
        drawable.draw(canvas)
        bmp
    } catch (e: Exception) { null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrafficMapScreen(context: Context) {
    var apps by remember { mutableStateOf<List<AppTrafficEntry>>(emptyList()) }
    var prevApps by remember { mutableStateOf<List<AppTrafficEntry>>(emptyList()) }
    // uid → TrafficStats total bytes from previous tick (for real-time speed)
    var prevTsBytes by remember { mutableStateOf(mapOf<Int, Long>()) }
    // pkg → speed bytes/sec (avg over last 2s)
    var speedMap by remember { mutableStateOf(mapOf<String, Long>()) }
    var selectedApp by remember { mutableStateOf<AppTrafficEntry?>(null) }
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    var usageGranted by remember { mutableStateOf(isUsageStatsGranted(context)) }
    // User-configurable app count: 20 / 50 / 100 / Все
    var maxApps by remember { mutableStateOf(20) }
    // Zoom / pan state — held as MutableState so pointerInput(Unit) always reads fresh values
    val mapScaleState = remember { mutableStateOf(1f) }
    val mapOffsetState = remember { mutableStateOf(Offset.Zero) }
    val isConnected = VpnGlobalState.connectionState.collectAsState().value == ConnectionState.CONNECTED

    // Re-check permission when screen becomes active (user returns from Settings)
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                usageGranted = isUsageStatsGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Pulse animation for the central VPN node
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(1000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "pulseScale"
    )
    // Dash offset animation for traffic lines
    val dashOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 40f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "dashOffset"
    )

    // Refresh stats every 2 seconds
    LaunchedEffect(Unit) {
        while (isActive) {
            val newApps = getTopTrafficApps(context, maxApps)
            val newTsBytes = mutableMapOf<Int, Long>()
            val newSpeed = mutableMapOf<String, Long>()
            for (app in newApps) {
                // TrafficStats updates in real-time (unlike NSM 7-day buckets)
                val tsRx = TrafficStats.getUidRxBytes(app.uid)
                val tsTx = TrafficStats.getUidTxBytes(app.uid)
                val tsCurrent = if (tsRx >= 0L || tsTx >= 0L)
                    tsRx.coerceAtLeast(0L) + tsTx.coerceAtLeast(0L)
                else -1L
                if (tsCurrent >= 0L) {
                    newTsBytes[app.uid] = tsCurrent
                    val prevTs = prevTsBytes[app.uid]
                    if (prevTs != null) {
                        newSpeed[app.packageName] = (tsCurrent - prevTs).coerceAtLeast(0L) / 2L
                    }
                } else {
                    // Fallback: diff of NSM totals (less accurate but better than nothing)
                    val prev = prevApps.find { it.packageName == app.packageName }
                    if (prev != null) {
                        newSpeed[app.packageName] = ((app.rxBytes + app.txBytes) - (prev.rxBytes + prev.txBytes))
                            .coerceAtLeast(0L) / 2L
                    }
                }
            }
            prevTsBytes = newTsBytes
            prevApps = newApps
            speedMap = newSpeed
            apps = newApps
            // Keep selectedApp in sync (update bytes)
            selectedApp?.let { sel ->
                selectedApp = newApps.find { it.packageName == sel.packageName } ?: sel
            }
            delay(2000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.traffic_map_title),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Box(
                modifier = Modifier
                    .background(
                        if (isConnected) Color(0xFF1B5E20) else Color(0xFF4A0000),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isConnected) stringResource(R.string.connected_status)
                    else stringResource(R.string.disconnected_status),
                    color = if (isConnected) Color(0xFF4CAF50) else Color(0xFFE53935),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.traffic_map_subtitle),
            color = Color(0xFF888888),
            fontSize = 12.sp
        )

            Spacer(modifier = Modifier.height(8.dp))

            // App count selector chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Показывать:",
                    color = Color(0xFF666666),
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                listOf(20 to "20", 50 to "50", 100 to "100", Int.MAX_VALUE to "Все").forEach { (value, label) ->
                    val selected = maxApps == value
                    Box(
                        modifier = Modifier
                            .background(
                                if (selected) Color(0xFF1565C0) else Color(0xFF1A1A1A),
                                RoundedCornerShape(12.dp)
                            )
                            .then(Modifier.pointerInput(value) {
                                detectTapGestures { maxApps = value }
                            })
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (selected) Color.White else Color(0xFF888888),
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

        // Permission banner
        if (!usageGranted) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1200), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Нужен доступ к статистике использования",
                        color = Color(0xFFFFC107),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Разрешить", color = Color.Black, fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (apps.isEmpty()) {
            val totalRx = remember { getDeviceTotalRxBytes() }
            val statsSupported = totalRx != -1L
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = if (!isConnected) stringResource(R.string.traffic_map_not_connected)
                               else stringResource(R.string.traffic_map_no_traffic),
                        color = Color(0xFF666666),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (!statsSupported) {
                        Text(
                            text = "TrafficStats не поддерживается на этом устройстве",
                            color = Color(0xFFE53935),
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Функция работает на реальных устройствах с Android 6+",
                            color = Color(0xFF444444),
                            fontSize = 11.sp
                        )
                    } else {
                        Text(
                            text = "Трафик устройства: ${totalRx / 1024 / 1024} МБ (с момента загрузки)",
                            color = Color(0xFF4CAF50),
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Откройте браузер или YouTube — данные появятся через 2 сек",
                            color = Color(0xFF444444),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        } else {
            // Pre-compute routing states outside Canvas (needs context)
            val routingStates = remember(apps) {
                apps.associate { it.packageName to getAppRoutingState(context, it.packageName) }
            }

            // Canvas Visualization
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    // Single pointerInput handles BOTH pan/zoom and tap.
                    // graphicsLayer is NOT used — transform lives inside withTransform in draw,
                    // so rawOffset is always in real canvas-layout space (no hit-test mismatch).
                    .pointerInput(Unit) {
                        coroutineScope {
                            // Gesture 1: pinch-zoom + pan
                            launch {
                                detectTransformGestures(panZoomLock = false) { _, pan, zoom, _ ->
                                    mapScaleState.value = (mapScaleState.value * zoom).coerceIn(0.4f, 4f)
                                    mapOffsetState.value += pan
                                }
                            }
                            // Gesture 2: tap to select app node
                            launch {
                                detectTapGestures { rawOffset ->
                                    val scale = mapScaleState.value
                                    val offset = mapOffsetState.value
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    // Invert withTransform: translate(offset) then scale(scale, pivot=center)
                                    // visual = cx + (draw - cx)*scale + offset
                                    // draw  = (visual - cx - offset) / scale + cx
                                    val tapX = (rawOffset.x - cx - offset.x) / scale + cx
                                    val tapY = (rawOffset.y - cy - offset.y) / scale + cy
                                    val radius = minOf(cx, cy) * 0.72f
                                    var tapped = false
                                    val maxTotal = (apps.maxOfOrNull { it.rxBytes + it.txBytes } ?: 1L).coerceAtLeast(1L)
                                    apps.forEachIndexed { index, app ->
                                        val angle = (2 * PI / apps.size * index - PI / 2).toFloat()
                                        val ax = cx + radius * cos(angle)
                                        val ay = cy + radius * sin(angle)
                                        val nodeR = 20f + (app.rxBytes + app.txBytes).toFloat() / maxTotal * 8f
                                        val dx = tapX - ax
                                        val dy = tapY - ay
                                        if (!tapped && dx * dx + dy * dy <= (nodeR + 14f) * (nodeR + 14f)) {
                                            selectedApp = if (selectedApp?.packageName == app.packageName) null else app
                                            tapped = true
                                        }
                                    }
                                    // Tap center VPN node to deselect
                                    if (!tapped) {
                                        val dx = tapX - cx; val dy = tapY - cy
                                        if (dx * dx + dy * dy <= 55f * 55f) selectedApp = null
                                    }
                                }
                            }
                        }
                    }
            ) {
                canvasSize = size
                val scale = mapScaleState.value
                val offset = mapOffsetState.value
                val cx = size.width / 2f
                val cy = size.height / 2f
                withTransform({
                    translate(left = offset.x, top = offset.y)
                    scale(scaleX = scale, scaleY = scale, pivot = Offset(cx, cy))
                }) {
                    drawTrafficMap(apps, pulseScale, dashOffset, isConnected, selectedApp?.packageName, routingStates)
                }
            }
        }

        // Legend
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LegendItem(color = Color(0xFF4CAF50), label = stringResource(R.string.traffic_low))
            LegendItem(color = Color(0xFFFFC107), label = stringResource(R.string.traffic_medium))
            LegendItem(color = Color(0xFFE53935), label = stringResource(R.string.traffic_high))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // App count
        Text(
            text = stringResource(R.string.traffic_map_app_count, apps.size),
            color = Color(0xFF555555),
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Bottom sheet for selected app
    selectedApp?.let { app ->
        ModalBottomSheet(
            onDismissRequest = { selectedApp = null },
            containerColor = Color(0xFF111111),
            tonalElevation = 0.dp
        ) {
            val serverName = remember {
                try {
                    com.carnelia.vpn.data.ServerRepository(context).getLastUsedServer()
                        ?.let { "${it.name} (${it.host})" } ?: "неизвестно"
                } catch (e: Exception) { "неизвестно" }
            }
            AppTrafficDetailSheet(
                context = context,
                app = app,
                speed = speedMap[app.packageName] ?: 0L,
                maxTotal = (apps.maxOfOrNull { it.rxBytes + it.txBytes } ?: 1L).coerceAtLeast(1L),
                serverName = serverName,
                onDismiss = { selectedApp = null }
            )
        }
    }
}

@Composable
private fun AppTrafficDetailSheet(
    context: Context,
    app: AppTrafficEntry,
    speed: Long,
    maxTotal: Long,
    serverName: String = "",
    onDismiss: () -> Unit
) {
    val total = app.rxBytes + app.txBytes
    val ratio = (total.toFloat() / maxTotal.toFloat()).coerceIn(0f, 1f)

    val isConnected = VpnGlobalState.connectionState.collectAsState().value == ConnectionState.CONNECTED

    var isBypassed by remember {
        val bypassed = PrefsManager.isSplitTunnelingEnabled(context) &&
            PrefsManager.getSplitTunnelMode(context) == "disallow" &&
            app.packageName in PrefsManager.getSelectedApps(context)
        mutableStateOf(bypassed)
    }
    var isBlocked by remember {
        mutableStateOf(app.packageName in PrefsManager.getFirewallBlockedApps(context))
    }

    // Routing chain: source rule → state → destination
    val routingState = remember(isBypassed, isBlocked) {
        getAppRoutingState(context, app.packageName)
    }
    val splitMode = remember { PrefsManager.getSplitTunnelMode(context) }
    val splitEnabled = remember { PrefsManager.isSplitTunnelingEnabled(context) }
    val routeRule = when {
        isBlocked -> "Правило: Блокировка интернета"
        isBypassed -> "Правило: Обход VPN"
        splitEnabled && splitMode == "allow" && app.packageName !in PrefsManager.getSelectedApps(context)
                    -> "Правило: Не в списке allow"
        !splitEnabled -> "Правило: Глобальный прокси"
        else -> "Правило: Глобальный прокси"
    }
    val routeState = when (routingState) {
        AppRoutingState.VPN_PROXY        -> "→  VPN Proxy"
        AppRoutingState.DIRECT_BYPASS    -> "→  Прямой выход"
        AppRoutingState.FIREWALL_BLOCKED -> "→  Заблокирован"
    }
    val routeStateColor = when (routingState) {
        AppRoutingState.VPN_PROXY        -> Color(0xFF4CAF50)
        AppRoutingState.DIRECT_BYPASS    -> Color(0xFFFFC107)
        AppRoutingState.FIREWALL_BLOCKED -> Color(0xFFE53935)
    }
    val routeDest = when (routingState) {
        AppRoutingState.VPN_PROXY     -> if (serverName.isNotBlank()) "→  $serverName" else ""
        AppRoutingState.DIRECT_BYPASS -> "→  Интернет (без туннеля)"
        AppRoutingState.FIREWALL_BLOCKED -> "→  Андроид /dev/null"
    }

    fun rebuildIfConnected() {
        if (isConnected) {
            val intent = Intent(context, com.carnelia.vpn.service.CarheliaVpnService::class.java).apply {
                action = com.carnelia.vpn.service.CarheliaVpnService.ACTION_REBUILD_INTERFACE
            }
            context.startService(intent)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .navigationBarsPadding()
    ) {
        // Handle bar
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .background(Color(0xFF444444), RoundedCornerShape(2.dp))
                .align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(16.dp))

        // App header row
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (app.iconBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = app.iconBitmap,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF222222), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(app.label.first().toString(), color = Color.White, fontSize = 20.sp)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(app.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(app.packageName, color = Color(0xFF555555), fontSize = 10.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Цепочка маршрута: Апп → Правило → Состояние → Цель
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141414), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Маршрут трафика", color = Color(0xFF555555), fontSize = 10.sp)
                Spacer(modifier = Modifier.height(2.dp))
                // Line 1: app name
                Text("\uD83D\uDCF1  ${app.label}", color = Color(0xFFCCCCCC), fontSize = 12.sp)
                // Line 2: rule
                Text(routeRule, color = Color(0xFF666666), fontSize = 11.sp)
                // Line 3: routing state (colored)
                Text(routeState, color = routeStateColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                // Line 4: final destination
                if (routeDest.isNotBlank()) {
                    Text(routeDest, color = Color(0xFF888888), fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Traffic stats row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TrafficStatChip(label = "Входящий", value = formatBytes(app.rxBytes), color = Color(0xFF4CAF50))
            TrafficStatChip(label = "Исходящий", value = formatBytes(app.txBytes), color = Color(0xFF2196F3))
            TrafficStatChip(label = "Скорость", value = "${formatBytes(speed)}/с", color = Color(0xFFFFC107))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Progress bar
        val barColor = when {
            ratio > 0.6f -> Color(0xFFE53935)
            ratio > 0.25f -> Color(0xFFFFC107)
            else -> Color(0xFF4CAF50)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(Color(0xFF222222), RoundedCornerShape(3.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio)
                    .height(6.dp)
                    .background(barColor, RoundedCornerShape(3.dp))
            )
        }
        Text(
            text = "Доля трафика: ${"%.0f".format(ratio * 100)}% от топ-приложения",
            color = Color(0xFF555555),
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(color = Color(0xFF222222))
        Spacer(modifier = Modifier.height(16.dp))

        // Bypass VPN toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Обход VPN", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Трафик идёт напрямую, без туннеля",
                    color = Color(0xFF666666), fontSize = 11.sp
                )
            }
            Switch(
                checked = isBypassed,
                onCheckedChange = { checked ->
                    isBypassed = checked
                    if (checked) isBlocked = false
                    val currentSelected = PrefsManager.getSelectedApps(context).toMutableSet()
                    if (checked) {
                        currentSelected.add(app.packageName)
                        PrefsManager.setSplitTunnelingEnabled(context, true)
                        PrefsManager.setSplitTunnelMode(context, "disallow")
                        PrefsManager.removeFirewallBlockedApp(context, app.packageName)
                    } else {
                        currentSelected.remove(app.packageName)
                    }
                    PrefsManager.setSelectedApps(context, currentSelected)
                    rebuildIfConnected()
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFFFFC107),
                    checkedTrackColor = Color(0xFF3D2F00)
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Block ALL internet toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Заблокировать интернет", color = Color(0xFFE53935), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Text(
                    "Полная блокировка (VPN + прямой трафик)",
                    color = Color(0xFF666666), fontSize = 11.sp
                )
            }
            Switch(
                checked = isBlocked,
                onCheckedChange = { checked ->
                    isBlocked = checked
                    if (checked) isBypassed = false
                    if (checked) {
                        PrefsManager.addFirewallBlockedApp(context, app.packageName)
                        // also remove from bypass list if present
                        val sel = PrefsManager.getSelectedApps(context).toMutableSet()
                        sel.remove(app.packageName)
                        PrefsManager.setSelectedApps(context, sel)
                    } else {
                        PrefsManager.removeFirewallBlockedApp(context, app.packageName)
                    }
                    rebuildIfConnected()
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFFE53935),
                    checkedTrackColor = Color(0xFF3D0000)
                )
            )
        }

        // Status banner
        Spacer(modifier = Modifier.height(12.dp))
        if (!isConnected && (isBypassed || isBlocked)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A0000), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text(
                    "VPN не включён — блокировка/обход сохранены и применятся при подключении",
                    color = Color(0xFFE53935),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else if (isConnected && (isBypassed || isBlocked)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A1A0A), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text(
                    "✓ Применено без перезапуска VPN",
                    color = Color(0xFF4CAF50),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun TrafficStatChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(label, color = Color(0xFF666666), fontSize = 10.sp)
    }
}

private fun DrawScope.drawTrafficMap(
    apps: List<AppTrafficEntry>,
    pulseScale: Float,
    dashOffset: Float,
    isConnected: Boolean,
    selectedPkg: String? = null,
    routingStates: Map<String, AppRoutingState> = emptyMap()
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val outerRadius = minOf(cx, cy) * 0.72f
    val innerRadius = minOf(cx, cy) * 0.38f
    val centerRadius = 40f * pulseScale
    val radius = outerRadius  // alias for app nodes

    // ─── Inner routing ring — 3 fixed nodes ───────────────────────────
    // Positions: VPN_PROXY (top), DIRECT_BYPASS (bottom-left), FIREWALL_BLOCKED (bottom-right)
    val routingNodeAngles = mapOf(
        AppRoutingState.VPN_PROXY        to (-PI / 2).toFloat(),
        AppRoutingState.DIRECT_BYPASS    to (PI / 6).toFloat(),
        AppRoutingState.FIREWALL_BLOCKED to (5 * PI / 6).toFloat()
    )
    val routingNodeColors = mapOf(
        AppRoutingState.VPN_PROXY        to Color(0xFF4CAF50),
        AppRoutingState.DIRECT_BYPASS    to Color(0xFFFFC107),
        AppRoutingState.FIREWALL_BLOCKED to Color(0xFFE53935)
    )
    val routingNodeLabels = mapOf(
        AppRoutingState.VPN_PROXY        to "VPN",
        AppRoutingState.DIRECT_BYPASS    to "Direct",
        AppRoutingState.FIREWALL_BLOCKED to "Block"
    )

    // Draw app → routing-node connection lines (thin, colored)
    apps.forEachIndexed { index, app ->
        val state = routingStates[app.packageName] ?: AppRoutingState.VPN_PROXY
        val rAngle = routingNodeAngles[state] ?: return@forEachIndexed
        val rx = cx + innerRadius * cos(rAngle)
        val ry = cy + innerRadius * sin(rAngle)
        val appAngle = (2 * PI / apps.size * index - PI / 2).toFloat()
        val ax = cx + outerRadius * cos(appAngle)
        val ay = cy + outerRadius * sin(appAngle)
        val routeColor = routingNodeColors[state] ?: Color(0xFF4CAF50)
        drawLine(
            color = routeColor.copy(alpha = 0.22f),
            start = Offset(ax, ay),
            end = Offset(rx, ry),
            strokeWidth = 1.2f
        )
    }

    // Draw inner routing ring circle (dashed)
    drawCircle(
        color = Color(0xFF2A2A2A),
        radius = innerRadius,
        center = Offset(cx, cy),
        style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
    )

    // Draw 3 routing nodes
    AppRoutingState.entries.forEach { state ->
        val rAngle = routingNodeAngles[state] ?: return@forEach
        val rx = cx + innerRadius * cos(rAngle)
        val ry = cy + innerRadius * sin(rAngle)
        val nodeColor = routingNodeColors[state] ?: Color.Gray
        drawCircle(color = Color(0xFF0D0D0D), radius = 18f, center = Offset(rx, ry))
        drawCircle(color = nodeColor.copy(alpha = 0.25f), radius = 18f, center = Offset(rx, ry))
        drawCircle(color = nodeColor, radius = 18f, center = Offset(rx, ry), style = Stroke(width = 1.8f))
        // Small icon dot in center of routing node
        drawCircle(color = nodeColor, radius = 5f, center = Offset(rx, ry))
    }

    // Draw app nodes around the circle
    apps.forEachIndexed { index, app ->
        val angle = (2 * PI / apps.size * index - PI / 2).toFloat()
        val ax = cx + radius * cos(angle)
        val ay = cy + radius * sin(angle)

        val total = app.rxBytes + app.txBytes
        val maxTotal = (apps.maxOfOrNull { it.rxBytes + it.txBytes } ?: 1L).coerceAtLeast(1L)
        val ratio = total.toFloat() / maxTotal.toFloat()

        val lineColor = when {
            ratio > 0.6f -> Color(0xFFE53935)
            ratio > 0.25f -> Color(0xFFFFC107)
            else -> Color(0xFF4CAF50)
        }
        val lineWidth = 1.5f + ratio * 3.5f

        // Draw dashed animated line from app to center
        if (isConnected) {
            val path = Path().apply {
                moveTo(ax, ay)
                lineTo(cx, cy)
            }
            drawPath(
                path = path,
                color = lineColor.copy(alpha = 0.6f),
                style = Stroke(
                    width = lineWidth,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(12f, 8f), phase = -dashOffset
                    )
                )
            )
        } else {
            // Static line when disconnected
            drawLine(
                color = Color(0xFF333333),
                start = Offset(ax, ay),
                end = Offset(cx, cy),
                strokeWidth = 1f
            )
        }

        // App icon or letter circle
        val nodeR = 20f + ratio * 8f
        val isSelected = app.packageName == selectedPkg
        // Selection glow ring
        if (isSelected) {
            drawCircle(
                color = Color(0xFFFFFFFF).copy(alpha = 0.18f),
                radius = nodeR + 10f,
                center = Offset(ax, ay)
            )
            drawCircle(
                color = Color(0xFFFFFFFF).copy(alpha = 0.5f),
                radius = nodeR + 4f,
                center = Offset(ax, ay),
                style = Stroke(width = 2f)
            )
        }
        drawCircle(color = Color(0xFF1A1A2E), radius = nodeR + 2f, center = Offset(ax, ay))
        drawCircle(color = lineColor.copy(alpha = if (isSelected) 0.6f else 0.3f), radius = nodeR, center = Offset(ax, ay))
        drawCircle(
            color = lineColor,
            radius = nodeR,
            center = Offset(ax, ay),
            style = Stroke(width = if (isSelected) 2.5f else 1.5f)
        )

        // Draw app icon if available
        if (app.iconBitmap != null) {
            val iconSize = (nodeR * 1.5f).toInt().coerceAtLeast(1)
            drawImage(
                image = app.iconBitmap,
                srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                srcSize = androidx.compose.ui.unit.IntSize(app.iconBitmap.width, app.iconBitmap.height),
                dstOffset = androidx.compose.ui.unit.IntOffset(
                    (ax - iconSize / 2).toInt(), (ay - iconSize / 2).toInt()
                ),
                dstSize = androidx.compose.ui.unit.IntSize(iconSize, iconSize)
            )
        }

        // Routing badge — small colored dot in bottom-right corner of node
        val state = routingStates[app.packageName] ?: AppRoutingState.VPN_PROXY
        val badgeColor = when (state) {
            AppRoutingState.VPN_PROXY        -> Color(0xFF4CAF50)
            AppRoutingState.DIRECT_BYPASS    -> Color(0xFFFFC107)
            AppRoutingState.FIREWALL_BLOCKED -> Color(0xFFE53935)
        }
        val badgeX = ax + nodeR * 0.65f
        val badgeY = ay + nodeR * 0.65f
        drawCircle(color = Color(0xFF0A0A0A), radius = 7f, center = Offset(badgeX, badgeY))
        drawCircle(color = badgeColor, radius = 5f, center = Offset(badgeX, badgeY))
    }

    // Central VPN node
    val glowColor = if (isConnected) Color(0xFF4CAF50) else Color(0xFFE53935)
    // Outer glow rings
    for (i in 3 downTo 1) {
        drawCircle(
            color = glowColor.copy(alpha = 0.06f * i),
            radius = centerRadius * (1f + 0.3f * i),
            center = Offset(cx, cy)
        )
    }
    drawCircle(color = Color(0xFF0D1B2A), radius = centerRadius, center = Offset(cx, cy))
    drawCircle(
        color = glowColor,
        radius = centerRadius,
        center = Offset(cx, cy),
        style = Stroke(width = 2.5f)
    )
    // VPN label inside center node handled separately (Canvas doesn't easily draw text)
    // Draw "VPN" using small circle dots pattern — just colored fill
    drawCircle(color = glowColor.copy(alpha = 0.25f), radius = centerRadius * 0.6f, center = Offset(cx, cy))
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, color = Color(0xFF888888), fontSize = 11.sp)
    }
}
