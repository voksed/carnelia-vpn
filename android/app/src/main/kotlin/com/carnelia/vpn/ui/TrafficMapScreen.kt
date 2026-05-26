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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.ui.text.font.FontFamily
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.json.JSONObject

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
    // Active tab: 0 = Карта, 1 = Трассировка
    var activeTab by remember { mutableStateOf(0) }
    // App search query
    var searchQuery by remember { mutableStateOf("") }
    // Zoom / pan state — held as MutableState so pointerInput(Unit) always reads fresh values
    val mapScaleState = remember { mutableStateOf(1f) }
    val mapOffsetState = remember { mutableStateOf(Offset.Zero) }
    val isConnected = VpnGlobalState.connectionState.collectAsState().value == ConnectionState.CONNECTED
    // pkg → free position in draw-space (null = default circle orbit)
    val nodePositions = remember { androidx.compose.runtime.snapshots.SnapshotStateMap<String, Offset>() }
    // which node is currently being dragged
    val draggingPkgState = remember { mutableStateOf<String?>(null) }
    // top recent destinations from xray_access.log
    var recentHosts by remember { mutableStateOf<List<String>>(emptyList()) }

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
    // Particle phase (0..1) — drives animated dots on active connection lines
    val particlePhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "particle"
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
            // Top destinations from xray access log (system-wide, no root needed)
            recentHosts = getTopDestinations(context, 6).map { it.first }
            delay(2000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                color = MaterialTheme.colorScheme.onSurface,
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
            color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.8f),
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Tab bar ───────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("Карта трафика", "Соединения", "Трассировка").forEachIndexed { idx, label ->
                val selected = activeTab == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .then(Modifier.pointerInput(idx) {
                            detectTapGestures { activeTab = idx }
                        })
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f),
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (activeTab == 2) {
            // ── Трассировка пакетов ─────────────────────────────
            PacketTraceContent(context)
        } else if (activeTab == 1) {
            // ── Live connections: xray_access.log ───────────────
            LiveConnectionsContent(context)
        } else {

            Spacer(modifier = Modifier.height(0.dp))

            // ── Поиск приложений ────────────────────────────────
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Поиск приложения…", color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f), fontSize = 13.sp) },
                leadingIcon = { androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f),
                    modifier = Modifier.size(18.dp)
                ) },
                trailingIcon = if (searchQuery.isNotEmpty()) {{
                    androidx.compose.material3.IconButton(onClick = { searchQuery = "" }) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }} else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    cursorColor = Color(0xFFB84629)
                ),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // App count selector chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Показывать:",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f),
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
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha=0.8f),
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
                        Text("Разрешить", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Filter apps by search query
        val displayedApps = remember(apps, searchQuery) {
            if (searchQuery.isBlank()) apps
            else apps.filter {
                it.label.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }

        if (displayedApps.isEmpty() && searchQuery.isNotBlank()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("Приложений не найдено: «$searchQuery»", color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f), fontSize = 14.sp)
            }
        } else if (apps.isEmpty()) {
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
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f),
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
            val routingStates = remember(displayedApps) {
                displayedApps.associate { it.packageName to getAppRoutingState(context, it.packageName) }
            }

            if (searchQuery.isNotBlank()) {
                // ── Search result list mode — much more usable than spinning 1 dot on Canvas ──
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(displayedApps) { _, app ->
                        val speed = speedMap[app.packageName] ?: 0L
                        val state = routingStates[app.packageName] ?: AppRoutingState.VPN_PROXY
                        val stateColor = when (state) {
                            AppRoutingState.VPN_PROXY        -> Color(0xFF4CAF50)
                            AppRoutingState.DIRECT_BYPASS    -> Color(0xFFFFC107)
                            AppRoutingState.FIREWALL_BLOCKED -> Color(0xFFE53935)
                        }
                        val stateLabel = when (state) {
                            AppRoutingState.VPN_PROXY        -> "VPN"
                            AppRoutingState.DIRECT_BYPASS    -> "Прямой"
                            AppRoutingState.FIREWALL_BLOCKED -> "Блок"
                        }
                        val isSelected = app.packageName == selectedApp?.packageName
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) Color(0xFF0A1830) else MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(10.dp)
                                )
                                .pointerInput(app.packageName) {
                                    detectTapGestures {
                                        selectedApp = if (selectedApp?.packageName == app.packageName) null else app
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (app.iconBitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = app.iconBitmap,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) { Text(app.label.first().toString(), color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp) }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.label, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(app.packageName, color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f), fontSize = 10.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (speed > 512L) {
                                    Text("↓ ${formatBytes(speed)}/с", color = Color(0xFF4CAF50), fontSize = 10.sp)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = stateColor.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, stateColor.copy(alpha = 0.4f))
                                ) {
                                    Text(stateLabel, color = stateColor, fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                val total = app.rxBytes + app.txBytes
                                Text(formatBytes(total), color = Color(0xFF777777), fontSize = 10.sp)
                            }
                        }
                    }
                }
            } else {
            // Canvas Visualization
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds()
                    // Three concurrent gesture handlers:
                    // 1. pinch-zoom + pan (suppress pan while dragging a node)
                    // 2. long-press → drag individual node freely
                    // 3. tap → select / deselect node
                    .pointerInput(Unit) {
                        coroutineScope {
                            // ── Gesture 1: pinch-zoom + pan ───────────────
                            launch {
                                detectTransformGestures(panZoomLock = false) { _, pan, zoom, _ ->
                                    mapScaleState.value = (mapScaleState.value * zoom).coerceIn(0.3f, 5f)
                                    if (draggingPkgState.value == null) {
                                        mapOffsetState.value += pan
                                    }
                                }
                            }
                            // ── Gesture 2: long-press then drag node ──────
                            launch {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { rawOffset ->
                                        val scale = mapScaleState.value
                                        val offset = mapOffsetState.value
                                        val cx = size.width / 2f
                                        val cy = size.height / 2f
                                        val outerRadius = minOf(cx, cy) * 0.72f
                                        val tapX = (rawOffset.x - cx - offset.x) / scale + cx
                                        val tapY = (rawOffset.y - cy - offset.y) / scale + cy
                                        val maxTotal = (displayedApps.maxOfOrNull { it.rxBytes + it.txBytes } ?: 1L).coerceAtLeast(1L)
                                        displayedApps.forEachIndexed { index, app ->
                                            val pos = nodePositions[app.packageName] ?: run {
                                                val a = (2 * PI / displayedApps.size * index - PI / 2).toFloat()
                                                Offset(cx + outerRadius * cos(a), cy + outerRadius * sin(a))
                                            }
                                            val nodeR = 20f + (app.rxBytes + app.txBytes).toFloat() / maxTotal * 8f + 18f
                                            val dx = tapX - pos.x; val dy = tapY - pos.y
                                            if (dx * dx + dy * dy <= nodeR * nodeR) {
                                                draggingPkgState.value = app.packageName
                                                return@detectDragGesturesAfterLongPress
                                            }
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val pkg = draggingPkgState.value ?: return@detectDragGesturesAfterLongPress
                                        val scale = mapScaleState.value
                                        val cx = size.width / 2f
                                        val cy = size.height / 2f
                                        val outerRadius = minOf(cx, cy) * 0.72f
                                        val idx = displayedApps.indexOfFirst { it.packageName == pkg }
                                        val currentPos = nodePositions[pkg] ?: if (idx >= 0) {
                                            val a = (2 * PI / displayedApps.size * idx - PI / 2).toFloat()
                                            Offset(cx + outerRadius * cos(a), cy + outerRadius * sin(a))
                                        } else return@detectDragGesturesAfterLongPress
                                        // Convert screen delta → draw-space delta
                                        nodePositions[pkg] = currentPos + dragAmount / scale
                                    },
                                    onDragEnd = { draggingPkgState.value = null },
                                    onDragCancel = { draggingPkgState.value = null }
                                )
                            }
                            // ── Gesture 3: tap to select / deselect ──────
                            launch {
                                detectTapGestures { rawOffset ->
                                    if (draggingPkgState.value != null) return@detectTapGestures
                                    val scale = mapScaleState.value
                                    val offset = mapOffsetState.value
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    val outerRadius = minOf(cx, cy) * 0.72f
                                    val tapX = (rawOffset.x - cx - offset.x) / scale + cx
                                    val tapY = (rawOffset.y - cy - offset.y) / scale + cy
                                    var tapped = false
                                    val maxTotal = (displayedApps.maxOfOrNull { it.rxBytes + it.txBytes } ?: 1L).coerceAtLeast(1L)
                                    displayedApps.forEachIndexed { index, app ->
                                        val pos = nodePositions[app.packageName] ?: run {
                                            val a = (2 * PI / displayedApps.size * index - PI / 2).toFloat()
                                            Offset(cx + outerRadius * cos(a), cy + outerRadius * sin(a))
                                        }
                                        val nodeR = 20f + (app.rxBytes + app.txBytes).toFloat() / maxTotal * 8f
                                        val dx = tapX - pos.x; val dy = tapY - pos.y
                                        if (!tapped && dx * dx + dy * dy <= (nodeR + 14f) * (nodeR + 14f)) {
                                            selectedApp = if (selectedApp?.packageName == app.packageName) null else app
                                            tapped = true
                                        }
                                    }
                                    if (!tapped) {
                                        val dx = tapX - cx; val dy = tapY - cy
                                        if (dx * dx + dy * dy <= 55f * 55f) selectedApp = null
                                        // Double-tap center = reset all node positions
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
                    drawTrafficMap(
                        apps = displayedApps,
                        pulseScale = pulseScale,
                        dashOffset = dashOffset,
                        particlePhase = particlePhase,
                        isConnected = isConnected,
                        selectedPkg = selectedApp?.packageName,
                        draggingPkg = draggingPkgState.value,
                        routingStates = routingStates,
                        speedMap = speedMap,
                        nodePositions = nodePositions,
                        recentHosts = recentHosts
                    )
                }
            }
            } // end searchQuery branch
        }

        // Active packets panel — apps currently transmitting
        val activeNow = remember(displayedApps, speedMap) {
            displayedApps.filter { (speedMap[it.packageName] ?: 0L) > 512L }
                .sortedByDescending { speedMap[it.packageName] ?: 0L }
        }
        if (activeNow.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Передают прямо сейчас (${activeNow.size})",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f),
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(activeNow) { app ->
                    val speed = speedMap[app.packageName] ?: 0L
                    ActiveAppPacketChip(
                        app = app,
                        speed = speed,
                        isSelected = app.packageName == selectedApp?.packageName,
                        onSelect = {
                            selectedApp = if (selectedApp?.packageName == app.packageName) null else app
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
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
            text = stringResource(R.string.traffic_map_app_count, displayedApps.size),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f),
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(16.dp))
        } // end tab == 0
    }

    // Bottom sheet for selected app
    selectedApp?.let { app ->
        ModalBottomSheet(
            onDismissRequest = { selectedApp = null },
            containerColor = MaterialTheme.colorScheme.surface,
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
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(app.label.first().toString(), color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(app.label, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(app.packageName, color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f), fontSize = 10.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Цепочка маршрута: Апп → Правило → Состояние → Цель
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Маршрут трафика", color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f), fontSize = 10.sp)
                Spacer(modifier = Modifier.height(2.dp))
                // Line 1: app name
                Text("\uD83D\uDCF1  ${app.label}", color = Color(0xFFCCCCCC), fontSize = 12.sp)
                // Line 2: rule
                Text(routeRule, color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f), fontSize = 11.sp)
                // Line 3: routing state (colored)
                Text(routeState, color = routeStateColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                // Line 4: final destination
                if (routeDest.isNotBlank()) {
                    Text(routeDest, color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.8f), fontSize = 11.sp)
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
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))
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
            color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f),
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))

        // Bypass VPN toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Обход VPN", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Трафик идёт напрямую, без туннеля",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f), fontSize = 11.sp
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
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f), fontSize = 11.sp
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
        Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f), fontSize = 10.sp)
    }
}

private fun DrawScope.drawTrafficMap(
    apps: List<AppTrafficEntry>,
    pulseScale: Float,
    dashOffset: Float,
    particlePhase: Float,
    isConnected: Boolean,
    selectedPkg: String? = null,
    draggingPkg: String? = null,
    routingStates: Map<String, AppRoutingState> = emptyMap(),
    speedMap: Map<String, Long> = emptyMap(),
    nodePositions: Map<String, Offset> = emptyMap(),
    recentHosts: List<String> = emptyList()
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

    // ─── helper: actual position of an app node in draw-space ────────────────
    fun appPos(index: Int, app: AppTrafficEntry): Offset =
        nodePositions[app.packageName] ?: run {
            val angle = (2 * PI / apps.size * index - PI / 2).toFloat()
            Offset(cx + outerRadius * cos(angle), cy + outerRadius * sin(angle))
        }

    // Draw app → routing-node connection lines + animated packet particles
    apps.forEachIndexed { index, app ->
        val state = routingStates[app.packageName] ?: AppRoutingState.VPN_PROXY
        val rAngle = routingNodeAngles[state] ?: return@forEachIndexed
        val rx = cx + innerRadius * cos(rAngle)
        val ry = cy + innerRadius * sin(rAngle)
        val pos = appPos(index, app)
        val ax = pos.x; val ay = pos.y
        val routeColor = routingNodeColors[state] ?: Color(0xFF4CAF50)
        val speed = speedMap[app.packageName] ?: 0L
        val isActive = speed > 512L

        // Base routing line (thicker + brighter when active)
        drawLine(
            color = routeColor.copy(alpha = if (isActive) 0.45f else 0.18f),
            start = Offset(ax, ay),
            end = Offset(rx, ry),
            strokeWidth = if (isActive) 2.2f else 1.2f
        )

        // Animated packet particles travelling from app → routing node
        if (isActive) {
            repeat(4) { i ->
                val t = ((particlePhase + i / 4f) % 1f)
                val px = ax + (rx - ax) * t
                val py = ay + (ry - ay) * t
                val pR = 5f * (1f - t * 0.5f)
                drawCircle(
                    color = routeColor.copy(alpha = 0.9f * (1f - t * 0.3f)),
                    radius = pR,
                    center = Offset(px, py)
                )
            }
            // Speed label floating at line midpoint
            val midX = (ax + rx) / 2f
            val midY = (ay + ry) / 2f
            val speedLabelPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(200, 120, 220, 120)
                textSize = 20f
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
                isFakeBoldText = true
            }
            drawContext.canvas.nativeCanvas.drawText(
                formatBytes(speed) + "/с", midX, midY - 10f, speedLabelPaint
            )
        }
    }

    // Draw inner routing ring circle (dashed)
    drawCircle(
        color = Color(0xFF2A2A2A),
        radius = innerRadius,
        center = Offset(cx, cy),
        style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
    )

    // Draw 3 routing nodes with labels
    AppRoutingState.entries.forEach { state ->
        val rAngle = routingNodeAngles[state] ?: return@forEach
        val rx = cx + innerRadius * cos(rAngle)
        val ry = cy + innerRadius * sin(rAngle)
        val nodeColor = routingNodeColors[state] ?: Color(0xFF888888)
        drawCircle(color = Color(0xFF0D0D0D), radius = 18f, center = Offset(rx, ry))
        drawCircle(color = nodeColor.copy(alpha = 0.25f), radius = 18f, center = Offset(rx, ry))
        drawCircle(color = nodeColor, radius = 18f, center = Offset(rx, ry), style = Stroke(width = 1.8f))
        drawCircle(color = nodeColor, radius = 5f, center = Offset(rx, ry))
        // Routing node label
        val label = routingNodeLabels[state] ?: ""
        val routeLabelPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(200, 200, 200, 200)
            textSize = 18f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
        drawContext.canvas.nativeCanvas.drawText(label, rx, ry - 24f, routeLabelPaint)
    }

    // Draw app nodes
    apps.forEachIndexed { index, app ->
        val pos = appPos(index, app)
        val ax = pos.x; val ay = pos.y

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
            val path = Path().apply { moveTo(ax, ay); lineTo(cx, cy) }
            drawPath(
                path = path,
                color = lineColor.copy(alpha = 0.6f),
                style = Stroke(
                    width = lineWidth,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), phase = -dashOffset)
                )
            )
        } else {
            drawLine(color = Color(0xFF333333), start = Offset(ax, ay), end = Offset(cx, cy), strokeWidth = 1f)
        }

        val nodeR = 20f + ratio * 8f
        val isSelected = app.packageName == selectedPkg
        val isDragging = app.packageName == draggingPkg

        // Drag glow — gold ring when held
        if (isDragging) {
            drawCircle(color = Color(0xFFFFD700).copy(alpha = 0.28f), radius = nodeR + 16f, center = Offset(ax, ay))
            drawCircle(color = Color(0xFFFFD700).copy(alpha = 0.75f), radius = nodeR + 6f, center = Offset(ax, ay), style = Stroke(width = 2.5f))
        }
        // Selection glow ring
        if (isSelected) {
            drawCircle(color = Color(0xFFFFFFFF).copy(alpha = 0.18f), radius = nodeR + 10f, center = Offset(ax, ay))
            drawCircle(color = Color(0xFFFFFFFF).copy(alpha = 0.5f), radius = nodeR + 4f, center = Offset(ax, ay), style = Stroke(width = 2f))
        }
        drawCircle(color = Color(0xFF1A1A2E), radius = nodeR + 2f, center = Offset(ax, ay))
        drawCircle(color = lineColor.copy(alpha = if (isSelected || isDragging) 0.6f else 0.3f), radius = nodeR, center = Offset(ax, ay))
        drawCircle(
            color = if (isDragging) Color(0xFFFFD700) else lineColor,
            radius = nodeR,
            center = Offset(ax, ay),
            style = Stroke(width = if (isSelected || isDragging) 2.5f else 1.5f)
        )

        // App icon
        if (app.iconBitmap != null) {
            val iconSize = (nodeR * 1.5f).toInt().coerceAtLeast(1)
            drawImage(
                image = app.iconBitmap,
                srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                srcSize = androidx.compose.ui.unit.IntSize(app.iconBitmap.width, app.iconBitmap.height),
                dstOffset = androidx.compose.ui.unit.IntOffset((ax - iconSize / 2).toInt(), (ay - iconSize / 2).toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(iconSize, iconSize)
            )
        }

        // Routing badge — bottom-right corner
        val state = routingStates[app.packageName] ?: AppRoutingState.VPN_PROXY
        val badgeColor = when (state) {
            AppRoutingState.VPN_PROXY        -> Color(0xFF4CAF50)
            AppRoutingState.DIRECT_BYPASS    -> Color(0xFFFFC107)
            AppRoutingState.FIREWALL_BLOCKED -> Color(0xFFE53935)
        }
        val badgeX = ax + nodeR * 0.65f; val badgeY = ay + nodeR * 0.65f
        drawCircle(color = Color(0xFF0A0A0A), radius = 7f, center = Offset(badgeX, badgeY))
        drawCircle(color = badgeColor, radius = 5f, center = Offset(badgeX, badgeY))

        // Label — direction-aware (always points away from center, even when dragged)
        val speed = speedMap[app.packageName] ?: 0L
        val shortLabel = if (app.label.length > 13) app.label.take(12) + "…" else app.label
        val dirX = ax - cx; val dirY = ay - cy
        val dirLen = sqrt(dirX * dirX + dirY * dirY).coerceAtLeast(0.001f)
        val labelDist = nodeR + 22f
        val lax = ax + dirX / dirLen * labelDist
        val lay = ay + dirY / dirLen * labelDist
        val labelAlpha = if (speed > 512L || app.packageName == selectedPkg || isDragging) 230 else 120
        val namePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(labelAlpha, 255, 255, 255)
            textSize = if (speed > 512L || app.packageName == selectedPkg || isDragging) 24f else 20f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = (speed > 512L || app.packageName == selectedPkg || isDragging)
        }
        drawContext.canvas.nativeCanvas.drawText(shortLabel, lax, lay, namePaint)
        if (speed > 512L) {
            val speedPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(220, 76, 175, 80)
                textSize = 19f
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }
            drawContext.canvas.nativeCanvas.drawText("↓ ${formatBytes(speed)}/с", lax, lay + 22f, speedPaint)
        }
        // Drag hint label
        if (isDragging) {
            val hintPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(180, 255, 215, 0)
                textSize = 17f
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }
            drawContext.canvas.nativeCanvas.drawText("✦ зажми и тяни", lax, lay + (if (speed > 512L) 42f else 22f), hintPaint)
        }
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
    drawCircle(color = glowColor.copy(alpha = 0.25f), radius = centerRadius * 0.6f, center = Offset(cx, cy))
    // "VPN" text inside center node
    val vpnPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(230, 255, 255, 255)
        textSize = 22f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }
    drawContext.canvas.nativeCanvas.drawText(
        if (isConnected) "VPN" else "OFF", cx, cy + 8f, vpnPaint
    )

    // Recent connection destinations from xray_access.log — shown below VPN center
    if (recentHosts.isNotEmpty()) {
        val hostPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(155, 100, 200, 120)
            textSize = 18f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        val baseY = cy + centerRadius + 22f
        recentHosts.take(5).forEachIndexed { i, host ->
            val shortHost = if (host.length > 28) host.take(27) + "…" else host
            drawContext.canvas.nativeCanvas.drawText("→ $shortHost", cx, baseY + i * 22f, hostPaint)
        }
    }
}

@Composable
private fun ActiveAppPacketChip(
    app: AppTrafficEntry,
    speed: Long,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(
                if (isSelected) Color(0xFF0A1830) else Color(0xFF141414),
                RoundedCornerShape(10.dp)
            )
            .pointerInput(app.packageName) { detectTapGestures { onSelect() } }
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (app.iconBitmap != null) {
                Image(
                    bitmap = app.iconBitmap,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(Color(0xFF1E1E1E), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(app.label.first().toString(), color = Color.White, fontSize = 10.sp)
                }
            }
            Column {
                Text(
                    text = app.label,
                    color = if (isSelected) Color.White else Color(0xFFCCCCCC),
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "↓ ${formatBytes(speed)}/с",
                        color = Color(0xFF4CAF50),
                        fontSize = 10.sp
                    )
                }
                Text(
                    text = app.packageName,
                    color = Color(0xFF444444),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
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
        Text(text = label, color = Color.White.copy(alpha=0.8f), fontSize = 11.sp)
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Packet Trace (встроено в карту трафика)
// ══════════════════════════════════════════════════════════════════════════════

@Composable
internal fun PacketTraceContent(context: Context) {
    val serverRepo = remember { com.carnelia.vpn.data.ServerRepository(context) }
    val defaultHost = remember { serverRepo.getLastUsedServer()?.host ?: "8.8.8.8" }

    var inputText by remember { mutableStateOf(defaultHost) }
    var isRunning by remember { mutableStateOf(false) }
    val hops = remember { mutableStateListOf<TraceHop>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var traceJob by remember { mutableStateOf<Job?>(null) }
    var errorMsg by remember { mutableStateOf("") }
    var methodUsed by remember { mutableStateOf("") }

    fun startTrace(host: String) {
        traceJob?.cancel()
        hops.clear()
        errorMsg = ""
        methodUsed = ""
        isRunning = true
        traceJob = scope.launch {
            try {
                runTraceInternal(
                    host = host.trim(),
                    onHop = { hop ->
                        val existingIdx = hops.indexOfFirst { it.index == hop.index }
                        if (existingIdx >= 0) hops[existingIdx] = hop else hops.add(hop)
                        scope.launch { runCatching { listState.animateScrollToItem(hops.size - 1) } }
                        if (hop.ip != "*" && hop.status != TraceHopStatus.TIMEOUT) {
                            scope.launch {
                                val resolved = resolveHopGeo(hop)
                                val idx = hops.indexOfFirst { it.index == hop.index }
                                if (idx >= 0) hops[idx] = resolved
                            }
                        }
                    },
                    onMethod = { methodUsed = it }
                )
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { errorMsg = e.message ?: "Ошибка" }
            finally { isRunning = false }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Input row ─────────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Цель (IP или домен)", fontSize = 12.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = !isRunning,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFB84629),
                    focusedLabelColor = Color(0xFFB84629),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (isRunning) {
                IconButton(onClick = { traceJob?.cancel(); isRunning = false }) {
                    Icon(Icons.Default.Stop, contentDescription = null, tint = Color(0xFFFF5252))
                }
            } else {
                IconButton(
                    onClick = { startTrace(inputText) },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF1A1A1A))
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF4CAF50))
                }
            }
        }

        if (methodUsed.isNotEmpty()) {
            Text("Метод: $methodUsed", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f),
                modifier = Modifier.padding(top = 2.dp))
        }
        if (errorMsg.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2A0000)),
                modifier = Modifier.fillMaxWidth()) {
                Text("⚠ $errorMsg", color = Color(0xFFFF6B6B), modifier = Modifier.padding(10.dp), fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Empty state ───────────────────────────────────────
        if (hops.isEmpty() && !isRunning) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Route, contentDescription = null, tint = Color(0xFF444444), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Трассировка пакетов", color = Color(0xFFCCCCCC), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Показывает через какие узлы и ДЦ\nпроходит трафик с пингом по каждому",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f), fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { startTrace(inputText) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB84629))) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Запустить трассировку", fontSize = 13.sp)
                    }
                }
            }
        } else {
            // Device node
            TraceHopRow(TraceHop(0, "Устройство", 0L, TraceHopStatus.OK, "Это устройство"), isFirst = true)
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                itemsIndexed(hops) { _, hop ->
                    AnimatedVisibility(visible = true, enter = fadeIn() + expandVertically()) {
                        TraceHopRow(hop = hop, isFirst = false)
                    }
                }
                if (isRunning) {
                    item {
                        Row(modifier = Modifier.padding(start = 36.dp, top = 4.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = Color(0xFFB84629))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Сканирование…", color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// ── Hop row ───────────────────────────────────────────────

@Composable
private fun TraceHopRow(hop: TraceHop, isFirst: Boolean) {
    val dotColor = when (hop.status) {
        TraceHopStatus.OK, TraceHopStatus.DESTINATION -> Color(0xFF4CAF50)
        TraceHopStatus.TIMEOUT -> Color(0xFFFFA726)
        TraceHopStatus.ERROR -> Color(0xFFFF5252)
        TraceHopStatus.RESOLVING -> MaterialTheme.colorScheme.onSurface.copy(alpha=0.8f)
    }
    val cardBg = when (hop.status) {
        TraceHopStatus.TIMEOUT -> Color(0xFF1F1800)
        TraceHopStatus.ERROR -> Color(0xFF200000)
        TraceHopStatus.DESTINATION -> Color(0xFF002010)
        else -> MaterialTheme.colorScheme.surface
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(30.dp)) {
            if (!isFirst) Spacer(modifier = Modifier.width(2.dp).height(8.dp).background(Color(0xFF2A2A2A)))
            Box(modifier = Modifier.size(if (hop.index == 0) 12.dp else 9.dp).background(dotColor, CircleShape))
            Spacer(modifier = Modifier.width(2.dp).height(8.dp).background(Color(0xFF2A2A2A)))
        }
        Spacer(modifier = Modifier.width(6.dp))
        Card(colors = CardDefaults.cardColors(containerColor = cardBg),
            modifier = Modifier.fillMaxWidth().padding(bottom = 3.dp),
            shape = RoundedCornerShape(7.dp)) {
            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    if (hop.index > 0) Text("Узел ${hop.index}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f))
                    val mainLabel = when {
                        hop.ip == "Устройство" -> "Это устройство"
                        hop.status == TraceHopStatus.TIMEOUT -> "★ ★ ★  Нет ответа"
                        hop.country.isNotEmpty() -> buildString {
                            append(hop.country)
                            if (hop.city.isNotEmpty()) append(" · ${hop.city}")
                        }
                        else -> hop.ip
                    }
                    Text(mainLabel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFFDDDDDD))
                    if (hop.isp.isNotEmpty() && hop.status != TraceHopStatus.TIMEOUT)
                        Text(hop.isp, fontSize = 11.sp, color = Color(0xFF777777))
                    if (hop.ip != "*" && hop.ip != "Устройство" && hop.status !in listOf(TraceHopStatus.RESOLVING, TraceHopStatus.TIMEOUT))
                        Text(hop.ip, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF484848))
                    if (hop.status == TraceHopStatus.DESTINATION) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF003318)) {
                            Text("ЦЕЛЬ", color = Color(0xFF66FF99), fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                when {
                    hop.index == 0 -> {}
                    hop.status == TraceHopStatus.TIMEOUT -> Text("—", color = Color(0xFF444444), fontSize = 15.sp)
                    hop.status == TraceHopStatus.RESOLVING -> CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f))
                    hop.latencyMs > 0 -> {
                        val lc = when { hop.latencyMs < 50L -> Color(0xFF4CAF50); hop.latencyMs < 150L -> Color(0xFFFFA726); else -> Color(0xFFFF5252) }
                        Surface(shape = RoundedCornerShape(10.dp), color = lc.copy(alpha = 0.15f), border = BorderStroke(1.dp, lc.copy(alpha = 0.4f))) {
                            Text("${hop.latencyMs} мс", color = lc, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── Data model ────────────────────────────────────────────

data class TraceHop(
    val index: Int,
    val ip: String,
    val latencyMs: Long,
    val status: TraceHopStatus,
    val country: String = "",
    val region: String = "",
    val city: String = "",
    val isp: String = ""
)

enum class TraceHopStatus { RESOLVING, OK, TIMEOUT, ERROR, DESTINATION }

// ── Traceroute logic ──────────────────────────────────────

private val TRACE_BINARIES = listOf(
    "/system/bin/traceroute", "/system/xbin/traceroute",
    "/system/bin/tracepath", "/system/xbin/tracepath"
)

private suspend fun runTraceInternal(
    host: String,
    onHop: suspend (TraceHop) -> Unit,
    onMethod: (String) -> Unit
) = withContext(Dispatchers.IO) {
    val binary = TRACE_BINARIES.firstOrNull { java.io.File(it).exists() }
    if (binary != null) {
        onMethod(if (binary.contains("tracepath")) "tracepath" else "traceroute")
        runBinaryTrace(binary, host, onHop)
    } else {
        onMethod("TCP probe (traceroute недоступен)")
        runTcpTrace(host, onHop)
    }
}

private suspend fun runBinaryTrace(
    binary: String,
    host: String,
    onHop: suspend (TraceHop) -> Unit
) = withContext(Dispatchers.IO) {
    val isPath = binary.contains("tracepath")
    val args = if (isPath) arrayOf(binary, "-n", "-m", "20", host)
               else arrayOf(binary, "-n", "-m", "20", "-w", "2", host)
    val process = Runtime.getRuntime().exec(args)
    val reader = BufferedReader(InputStreamReader(process.inputStream))
    val hopLineRegex = Regex("""^\s*(\d+)[: ]+(.+)$""")
    val ipRegex = Regex("""\b(\d{1,3}(?:\.\d{1,3}){3})\b""")
    val msRegex = Regex("""(\d+\.?\d*)\s*ms""")
    var line: String? = null
    while (isActive && reader.readLine().also { line = it } != null) {
        val l = line ?: continue
        val mLine = hopLineRegex.find(l) ?: continue
        val hopIdx = mLine.groupValues[1].toIntOrNull() ?: continue
        val rest = mLine.groupValues[2]
        if (rest.trimStart().startsWith("*")) {
            onHop(TraceHop(hopIdx, "*", -1L, TraceHopStatus.TIMEOUT)); continue
        }
        val ip = ipRegex.find(rest)?.value ?: continue
        val latencies = msRegex.findAll(rest).mapNotNull { it.groupValues[1].toDoubleOrNull() }.toList()
        val avgMs = if (latencies.isNotEmpty()) (latencies.sum() / latencies.size).toLong() else -1L
        val isTarget = runCatching {
            InetAddress.getByName(ip).hostAddress == InetAddress.getByName(host).hostAddress
        }.getOrDefault(ip == host)
        val status = if (isTarget) TraceHopStatus.DESTINATION else TraceHopStatus.RESOLVING
        onHop(TraceHop(hopIdx, ip, avgMs, status))
        if (status == TraceHopStatus.DESTINATION) break
    }
    runCatching { process.destroy() }
}

private suspend fun runTcpTrace(
    host: String,
    onHop: suspend (TraceHop) -> Unit
) = withContext(Dispatchers.IO) {
    val resolvedIp = try { InetAddress.getByName(host).hostAddress ?: host }
    catch (e: Exception) { onHop(TraceHop(1, host, -1L, TraceHopStatus.ERROR)); return@withContext }
    val t0 = System.currentTimeMillis()
    val connected = listOf(443, 80).any { port ->
        try { Socket().use { s -> s.connect(InetSocketAddress(resolvedIp, port), 5000); true } }
        catch (_: Exception) { false }
    }
    onHop(TraceHop(1, resolvedIp, System.currentTimeMillis() - t0,
        if (connected) TraceHopStatus.DESTINATION else TraceHopStatus.ERROR))
}

private suspend fun resolveHopGeo(hop: TraceHop): TraceHop = withContext(Dispatchers.IO) {
    try {
        val conn = (URL("https://ipwho.is/${hop.ip}").openConnection() as HttpURLConnection).apply {
            connectTimeout = 4000; readTimeout = 4000; requestMethod = "GET"
            setRequestProperty("User-Agent", "CarneliaVPN/2.4")
        }
        if (conn.responseCode != 200) return@withContext hop.copy(
            status = if (hop.status == TraceHopStatus.RESOLVING) TraceHopStatus.OK else hop.status)
        val obj = JSONObject(conn.inputStream.bufferedReader().readText())
        if (!obj.optBoolean("success", false)) return@withContext hop.copy(
            status = if (hop.status == TraceHopStatus.RESOLVING) TraceHopStatus.OK else hop.status)
        hop.copy(
            country = obj.optString("country", ""),
            region = obj.optString("region", ""),
            city = obj.optString("city", ""),
            isp = obj.optJSONObject("connection")?.optString("org", "") ?: "",
            status = if (hop.status == TraceHopStatus.RESOLVING) TraceHopStatus.OK else hop.status
        )
    } catch (_: Exception) {
        hop.copy(status = if (hop.status == TraceHopStatus.RESOLVING) TraceHopStatus.OK else hop.status)
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Live Connections — вкладка "Соединения": real-time xray_access.log monitor
// Показывает на какие серверы идут пакеты в реальном времени
// ══════════════════════════════════════════════════════════════════════════════

data class LiveConnection(
    val dest: String,      // host:port
    val host: String,      // host only
    val port: String,      // port only
    val tag: String,
    val timeLabel: String  // "только что" / "5 сек назад" etc.
)

@Composable
internal fun LiveConnectionsContent(context: Context) {
    val connections = remember { mutableStateListOf<LiveConnection>() }
    var filterQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val isConnected = VpnGlobalState.connectionState.collectAsState().value == ConnectionState.CONNECTED

    // Refresh from xray_access.log every 1.5 seconds
    LaunchedEffect(Unit) {
        while (isActive) {
            val rawConns = parseXrayAccessLog(context, 200)
            val now = System.currentTimeMillis()
            val fresh = rawConns.take(80).map { c ->
                val parts = c.dest.split(":")
                val host = parts.dropLast(1).joinToString(":")
                val port = parts.lastOrNull() ?: ""
                val secondsAgo = (now - c.time) / 1000L
                val timeLabel = when {
                    secondsAgo < 5  -> "только что"
                    secondsAgo < 60 -> "${secondsAgo}с"
                    secondsAgo < 3600 -> "${secondsAgo / 60}м"
                    else -> "${secondsAgo / 3600}ч"
                }
                LiveConnection(c.dest, host, port, c.tag, timeLabel)
            }
            val filtered = if (filterQuery.isBlank()) fresh
            else fresh.filter { it.host.contains(filterQuery, ignoreCase = true) || it.tag.contains(filterQuery, ignoreCase = true) }
            connections.clear()
            connections.addAll(filtered)
            delay(1500)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search / filter bar
        OutlinedTextField(
            value = filterQuery,
            onValueChange = { filterQuery = it },
            placeholder = { Text("Фильтр по хосту или тегу…", color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f), fontSize = 12.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f),
                    modifier = Modifier.size(18.dp))
            },
            trailingIcon = if (filterQuery.isNotEmpty()) {{
                IconButton(onClick = { filterQuery = "" }) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f), modifier = Modifier.size(16.dp))
                }
            }} else null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                cursorColor = Color(0xFFB84629)
            ),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Status row
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (connections.isEmpty()) "Нет данных" else "${connections.size} соединений",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f),
                fontSize = 11.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier.size(6.dp).background(
                        if (isConnected) Color(0xFF4CAF50) else Color(0xFF444444), CircleShape
                    )
                )
                Text(
                    text = if (isConnected) "VPN активен" else "VPN отключён",
                    color = if (isConnected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f),
                    fontSize = 11.sp
                )
            }
        }

        if (connections.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null,
                        tint = Color(0xFF444444), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (!isConnected) "Включите VPN — соединения появятся здесь"
                        else "Нет записей в xray_access.log\nГенерируйте трафик (браузер, YouTube…)",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f), fontSize = 13.sp,
                        textAlign = TextAlign.Center, lineHeight = 18.sp
                    )
                }
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                itemsIndexed(connections) { _, conn ->
                    LiveConnectionRow(conn)
                }
            }
        }
    }
}

@Composable
private fun LiveConnectionRow(conn: LiveConnection) {
    val tagColor = when {
        conn.tag.contains("proxy") -> Color(0xFF4CAF50)
        conn.tag.contains("direct") -> Color(0xFFFFC107)
        conn.tag.contains("block") -> Color(0xFFE53935)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha=0.8f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F0F0F), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Tag badge
        Surface(
            shape = RoundedCornerShape(5.dp),
            color = tagColor.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, tagColor.copy(alpha = 0.35f)),
            modifier = Modifier.widthIn(min = 52.dp)
        ) {
            Text(
                text = when {
                    conn.tag.contains("proxy") -> "VPN"
                    conn.tag.contains("direct") -> "Прямой"
                    conn.tag.contains("block") -> "Блок"
                    else -> conn.tag.take(8)
                },
                color = tagColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
            )
        }
        // Host
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conn.host,
                color = Color(0xFFCCCCCC),
                fontSize = 12.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (conn.port.isNotBlank()) {
                Text(":${conn.port}", color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f), fontSize = 10.sp)
            }
        }
        // Time
        Text(conn.timeLabel, color = Color(0xFF444444), fontSize = 10.sp)
    }
}
