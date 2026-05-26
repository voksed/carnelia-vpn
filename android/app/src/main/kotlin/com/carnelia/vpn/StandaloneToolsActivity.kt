package com.carnelia.vpn

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carnelia.vpn.service.DnsOnlyService
import com.carnelia.vpn.service.NetworkBoostService
import com.carnelia.vpn.ui.theme.CarheliaTheme
import com.carnelia.vpn.utils.AppLogger
import com.carnelia.vpn.utils.PrefsManager
import kotlinx.coroutines.*

/**
 * StandaloneToolsActivity — v2.4.0
 *
 * A standalone screen for network tools that work WITHOUT a full VPN connection:
 *
 *  1. DNS Protection  — lightweight DNS-only VPN (encrypts & filters DNS queries)
 *  2. Network Boost   — keeps WiFi + Mobile Data active simultaneously
 *  3. Private DNS     — opens Android system setting for DoT (Android 9+)
 *  4. Network Info    — shows current IP family, type, ping to common hosts
 */
class StandaloneToolsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeIndex = PrefsManager.getThemeIndex(this)
            CarheliaTheme(themeIndex = themeIndex) {
                StandaloneToolsScreen(activity = this)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandaloneToolsScreen(activity: StandaloneToolsActivity) {
    val context = activity as Context
    val scope = rememberCoroutineScope()

    // ── DNS Protection state ──────────────────────────────────────
    var dnsRunning by remember { mutableStateOf(DnsOnlyService.isRunning) }
    var selectedDnsLabel by remember { mutableStateOf("adguard") }
    var customDnsIp by remember { mutableStateOf("") }
    var showDnsCustom by remember { mutableStateOf(false) }

    // Update running states periodically
    LaunchedEffect(Unit) {
        while (isActive) {
            dnsRunning = DnsOnlyService.isRunning
            delay(1000)
        }
    }

    // ── Network Boost state ───────────────────────────────────────
    var boostRunning by remember { mutableStateOf(NetworkBoostService.isRunning) }
    LaunchedEffect(Unit) {
        while (isActive) {
            boostRunning = NetworkBoostService.isRunning
            delay(1000)
        }
    }

    // ── Network Ping state ────────────────────────────────────────
    var pingResults by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val pingHosts = listOf("1.1.1.1", "8.8.8.8", "94.140.14.14", "9.9.9.9")
    fun runPing() {
        scope.launch(Dispatchers.IO) {
            val results = mutableMapOf<String, String>()
            for (host in pingHosts) {
                val ms = try {
                    val start = System.currentTimeMillis()
                    java.net.Socket().use { it.connect(java.net.InetSocketAddress(host, 53), 2000) }
                    "${System.currentTimeMillis() - start} ms"
                } catch (e: Exception) { "timeout" }
                results[host] = ms
                withContext(Dispatchers.Main) { pingResults = results.toMap() }
            }
        }
    }
    LaunchedEffect(Unit) { runPing() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_title), color = MaterialTheme.colorScheme.onSurface) },
                navigationIcon = {
                    IconButton(onClick = { activity.finish() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ══════════════════════════════════════════════════════
            //  1. DNS Protection
            // ══════════════════════════════════════════════════════
            SectionLabel(stringResource(R.string.dns_protection_section))

            ToolCard(
                icon = Icons.Default.Lock,
                title = stringResource(R.string.dns_only_title),
                subtitle = if (dnsRunning)
                    stringResource(R.string.dns_only_active, DnsOnlyService.activeDnsLabel.ifBlank { selectedDnsLabel }.replaceFirstChar { it.uppercase() })
                else
                    stringResource(R.string.dns_only_desc),
                running = dnsRunning,
                accentGreen = dnsRunning
            ) {
                if (dnsRunning) {
                    // Stop
                    val stopIntent = Intent(context, DnsOnlyService::class.java).apply {
                        action = DnsOnlyService.ACTION_STOP
                    }
                    context.startService(stopIntent)
                    dnsRunning = false
                } else {
                    // Need VPN permission
                    val vpnIntent = android.net.VpnService.prepare(context)
                    if (vpnIntent != null) {
                        Toast.makeText(context, context.getString(R.string.dns_vpn_permission_needed), Toast.LENGTH_LONG).show()
                        return@ToolCard
                    }
                    val ip = if (selectedDnsLabel == "custom") customDnsIp
                             else DnsOnlyService.DNS_PRESETS[selectedDnsLabel]?.first ?: "1.1.1.1"
                    val startIntent = Intent(context, DnsOnlyService::class.java).apply {
                        action = DnsOnlyService.ACTION_START
                        putExtra(DnsOnlyService.EXTRA_DNS_LABEL, selectedDnsLabel)
                        putExtra(DnsOnlyService.EXTRA_DNS_IP, ip)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(startIntent)
                    } else context.startService(startIntent)
                    dnsRunning = true
                }
            }

            // DNS Preset Selector (only when not running)
            AnimatedVisibility(visible = !dnsRunning) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DnsPresetRow(
                        presets = listOf("cloudflare", "adguard", "google", "quad9", "custom"),
                        selected = selectedDnsLabel,
                        onSelect = { label ->
                            selectedDnsLabel = label
                            showDnsCustom = (label == "custom")
                        }
                    )
                    AnimatedVisibility(visible = showDnsCustom) {
                        OutlinedTextField(
                            value = customDnsIp,
                            onValueChange = { customDnsIp = it },
                            label = { Text(stringResource(R.string.custom_dns_ip_label)) },
                            placeholder = { Text("192.168.1.1") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Private DNS button (Android 9+ system setting — no VPN needed)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent("android.settings.PRIVATE_DNS_SETTINGS")
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open settings", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.open_private_dns_settings))
                }
            }

            // ══════════════════════════════════════════════════════
            //  2. Network Boost (WiFi + Mobile)
            // ══════════════════════════════════════════════════════
            SectionLabel(stringResource(R.string.network_boost_section_standalone))

            ToolCard(
                icon = Icons.Default.Speed,
                title = stringResource(R.string.dual_network_standalone_title),
                subtitle = if (boostRunning) stringResource(R.string.dual_network_standalone_active)
                           else stringResource(R.string.dual_network_standalone_desc),
                running = boostRunning,
                accentGreen = boostRunning
            ) {
                if (boostRunning) {
                    val stopIntent = Intent(context, NetworkBoostService::class.java).apply {
                        action = NetworkBoostService.ACTION_STOP
                    }
                    context.startService(stopIntent)
                    boostRunning = false
                } else {
                    val startIntent = Intent(context, NetworkBoostService::class.java).apply {
                        action = NetworkBoostService.ACTION_START
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(startIntent)
                    } else context.startService(startIntent)
                    boostRunning = true
                }
            }

            // ══════════════════════════════════════════════════════
            //  3. DNS / Network ping info
            // ══════════════════════════════════════════════════════
            SectionLabel(stringResource(R.string.network_info_section))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.dns_ping_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = { pingResults = emptyMap(); runPing() }) {
                            Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    pingHosts.forEach { host ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(host, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            val result = pingResults[host]
                            val color = when {
                                result == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                result == "timeout" -> Color(0xFFFF4444)
                                (result.removeSuffix(" ms").toLongOrNull() ?: 999) < 50 -> Color(0xFF44DD44)
                                else -> Color(0xFFFFAA00)
                            }
                            Text(
                                result ?: "…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = color,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  Reusable Composables
// ─────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun ToolCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    running: Boolean,
    accentGreen: Boolean = false,
    onToggle: () -> Unit
) {
    val accent = if (accentGreen) Color(0xFF44DD66) else MaterialTheme.colorScheme.primary

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(accent.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = running,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accent
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DnsPresetRow(
    presets: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    val labels = mapOf(
        "cloudflare" to "Cloudflare",
        "adguard"    to "AdGuard",
        "google"     to "Google",
        "quad9"      to "Quad9",
        "custom"     to "Custom"
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        presets.forEachIndexed { idx, key ->
            SegmentedButton(
                selected = selected == key,
                onClick = { onSelect(key) },
                shape = SegmentedButtonDefaults.itemShape(index = idx, count = presets.size),
                label = { Text(labels[key] ?: key, fontSize = 10.sp) }
            )
        }
    }
}
