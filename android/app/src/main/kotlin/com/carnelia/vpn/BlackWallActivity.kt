package com.carnelia.vpn

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carnelia.vpn.R
import com.carnelia.vpn.core.BlackWallEngine
import com.carnelia.vpn.core.BlackWallEngine.StealthLevel
import com.carnelia.vpn.core.BypassProbe
import com.carnelia.vpn.core.ConnectionState
import com.carnelia.vpn.core.VpnGlobalState
import com.carnelia.vpn.core.VpnProtocol
import com.carnelia.vpn.core.VpnServerConfig
import com.carnelia.vpn.service.ChimeraVpnService
import com.carnelia.vpn.ui.theme.ChimeraTheme
import com.carnelia.vpn.utils.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BlackWallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themeIndex = PrefsManager.getThemeIndex(this)
        setContent {
            ChimeraTheme(themeIndex = themeIndex) {
                BlackWallScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlackWallScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(BlackWallEngine.isEnabled(context)) }
    var level   by remember { mutableStateOf(BlackWallEngine.getLevel(context)) }

    // Serverless bypass: a direct connection with TLS fragmentation, no upstream server.
    val connState by VpnGlobalState.connectionState.collectAsState()
    val bypassRunning = connState == ConnectionState.CONNECTED ||
        connState == ConnectionState.CONNECTING ||
        connState == ConnectionState.RECONNECTING
    val freedomConfig = remember {
        VpnServerConfig(
            id = "blackwall-direct",
            name = context.getString(R.string.xbw_direct_name),
            protocol = VpnProtocol.FREEDOM,
            host = "direct",
            port = 443,
            config = emptyMap()
        )
    }
    fun startBypass() {
        context.startService(Intent(context, ChimeraVpnService::class.java).apply {
            action = ChimeraVpnService.ACTION_CONNECT
            putExtra(ChimeraVpnService.EXTRA_CONFIG, freedomConfig)
        })
    }
    val vpnConsentLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) startBypass()
        else Toast.makeText(context, context.getString(R.string.xbw_vpn_permission_needed), Toast.LENGTH_SHORT).show()
    }
    fun toggleBypass() {
        if (bypassRunning) {
            context.startService(Intent(context, ChimeraVpnService::class.java).apply {
                action = ChimeraVpnService.ACTION_DISCONNECT
            })
        } else {
            val prep = VpnService.prepare(context)
            if (prep != null) vpnConsentLauncher.launch(prep) else startBypass()
        }
    }

    // Block detection: local DNS/TCP/TLS probe that tunes the fragmentation.
    val scope = rememberCoroutineScope()
    var probing by remember { mutableStateOf(false) }
    var hostsText by remember {
        mutableStateOf(
            PrefsManager.getProbeHosts(context).ifBlank { BypassProbe.DEFAULT_HOSTS.joinToString("\n") }
        )
    }
    fun probeHosts(): List<String> = BypassProbe.parseHosts(hostsText).ifEmpty { BypassProbe.DEFAULT_HOSTS }
    var probeResults by remember { mutableStateOf(listOf<BypassProbe.SiteResult>()) }
    var probeTotal by remember { mutableStateOf(0) }
    var probeSummary by remember { mutableStateOf<String?>(null) }
    fun runProbe() {
        if (bypassRunning) {
            Toast.makeText(context, context.getString(R.string.xbw_probe_vpn_on), Toast.LENGTH_SHORT).show()
            return
        }
        val hosts = probeHosts()
        probing = true
        probeResults = emptyList()
        probeTotal = hosts.size
        probeSummary = null
        scope.launch {
            val report = withContext(Dispatchers.IO) {
                BypassProbe.run(hosts) { site ->
                    probeResults = probeResults + site
                }
            }
            PrefsManager.setProbeDohNeeded(
                context, report.sites.any { it.verdict == BypassProbe.Verdict.DNS_TAMPERED }
            )
            val best = report.best
            if (best != null) {
                PrefsManager.setProbedFragment(context, best.xrayLength, best.xrayInterval)
                probeSummary = context.getString(R.string.xbw_probe_applied, best.label)
            } else {
                probeSummary = context.getString(R.string.xbw_probe_none)
            }
            probing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Black Wall", color = MaterialTheme.colorScheme.onSurface)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null,
                            tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Main toggle card ──────────────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (enabled)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Security, null,
                            tint = if (enabled) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "BLACK WALL",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (enabled) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Anti-DPI Stealth Engine",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = {
                                enabled = it
                                BlackWallEngine.setEnabled(context, it)
                            }
                        )
                    }

                    if (enabled) {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))

                        Text(
                            stringResource(R.string.xbw_protection_level),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        StealthLevel.entries
                            .filter { it != StealthLevel.OFF }
                            .forEach { lvl ->
                                val selected = level == lvl
                                Card(
                                    onClick = {
                                        level = lvl
                                        BlackWallEngine.setLevel(context, lvl)
                                    },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selected)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else
                                            MaterialTheme.colorScheme.surface
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                lvl.label,
                                                fontWeight = if (selected) FontWeight.Bold
                                                             else FontWeight.Normal,
                                                color = if (selected)
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                else
                                                    MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                stringResource(lvl.descRes),
                                                fontSize = 11.sp,
                                                color = if (selected)
                                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                                else
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (selected) {
                                            Icon(
                                                Icons.Default.Check, null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                        Spacer(Modifier.height(8.dp))
                        if (enabled && level != StealthLevel.OFF) {
                            Text(
                                text = when (level) {
                                    StealthLevel.GHOST   -> stringResource(R.string.xbw_ghost_details)
                                    StealthLevel.PHANTOM -> stringResource(R.string.xbw_phantom_details)
                                    StealthLevel.WRAITH  -> stringResource(R.string.xbw_wraith_details)
                                    else -> ""
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }

                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { toggleBypass() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (bypassRunning) MaterialTheme.colorScheme.errorContainer
                                                 else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                if (bypassRunning) stringResource(R.string.xbw_stop)
                                else stringResource(R.string.xbw_start_serverless),
                                color = if (bypassRunning) MaterialTheme.colorScheme.onErrorContainer
                                        else MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Text(
                            stringResource(R.string.xbw_direct_desc),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }

            // ── Block detection card ──────────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.xbw_probe_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.xbw_probe_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                    OutlinedTextField(
                        value = hostsText,
                        onValueChange = {
                            hostsText = it
                            PrefsManager.setProbeHosts(context, it)
                        },
                        label = { Text(stringResource(R.string.xbw_probe_hosts)) },
                        minLines = 3,
                        maxLines = 8,
                        enabled = !probing,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { runProbe() },
                        enabled = !probing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(if (probing) R.string.xbw_probe_running else R.string.xbw_probe_run))
                    }
                    if (probing && probeTotal > 0) {
                        LinearProgressIndicator(
                            progress = { probeResults.size / probeTotal.toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    probeResults.sortedBy { it.host }.forEach { ProbeResultRow(it) }
                    probeSummary?.let {
                        Text(it, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // ── Info card ─────────────────────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.xbw_how_it_works),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.xbw_how_details),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                    Text(
                        stringResource(R.string.xbw_settings_apply_note),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProbeResultRow(site: BypassProbe.SiteResult) {
    val verdictRes = when (site.verdict) {
        BypassProbe.Verdict.OK           -> R.string.xbw_verdict_ok
        BypassProbe.Verdict.DNS_TAMPERED -> R.string.xbw_verdict_dns
        BypassProbe.Verdict.IP_BLOCKED   -> R.string.xbw_verdict_ip
        BypassProbe.Verdict.SNI_BLOCKED  -> R.string.xbw_verdict_sni
        BypassProbe.Verdict.UNREACHABLE  -> R.string.xbw_verdict_unreachable
    }
    val color = when (site.verdict) {
        BypassProbe.Verdict.OK -> MaterialTheme.colorScheme.primary
        BypassProbe.Verdict.SNI_BLOCKED, BypassProbe.Verdict.DNS_TAMPERED -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            if (site.verdict == BypassProbe.Verdict.OK || site.verdict == BypassProbe.Verdict.SNI_BLOCKED ||
                site.verdict == BypassProbe.Verdict.DNS_TAMPERED
            ) Icons.Default.Check else Icons.Default.Warning,
            contentDescription = null, tint = color, modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(site.host, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(site.detail, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(stringResource(verdictRes), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = color)
    }
}
