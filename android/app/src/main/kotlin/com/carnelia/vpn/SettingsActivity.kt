package com.carnelia.vpn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.carnelia.vpn.ui.theme.CarheliaTheme
import com.carnelia.vpn.core.VpnServerConfig
import com.carnelia.vpn.core.VpnProtocol
import com.carnelia.vpn.utils.AppLogger
import com.carnelia.vpn.utils.LogLevel

import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import com.carnelia.vpn.utils.PrefsManager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Send

import androidx.compose.ui.res.stringResource
import android.content.Context
import android.widget.Toast
import androidx.compose.material.icons.filled.Email // For Bug Report

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
             SettingsScreen()
        }
    }
    
    companion object {
        fun reportBug(context: Context) {
            try {
                val logs = AppLogger.getLogsAsString()
                val deviceInfo = "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android: ${android.os.Build.VERSION.RELEASE}"
                val report = "Bug Report:\n$deviceInfo\n\nLogs:\n$logs"
                
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_EMAIL, arrayOf("carneliavpn@gmail.com")) 
                    putExtra(Intent.EXTRA_SUBJECT, "Bug Report Carnelia VPN")
                    putExtra(Intent.EXTRA_TEXT, report)
                }
                context.startActivity(Intent.createChooser(intent, "Send Report"))
            } catch (e: Exception) {
                Toast.makeText(context, "Error sending report: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    // State for Theme Color
    var themeColorLong by remember { mutableStateOf(PrefsManager.getThemeColor(context)) }
    val themeColor = Color(themeColorLong.toInt())
    
    CarheliaTheme(accentColor = themeColor) {
        SettingsContent(
            context = context,
            themeColor = themeColor,
            onThemeChange = { newColor ->
                val newColorInt = newColor.toArgb()
                themeColorLong = newColorInt.toLong()
                PrefsManager.setThemeColor(context, themeColorLong)
            }
        )
    }
}

@Composable
fun SettingsContent(
    context: android.content.Context,
    themeColor: Color,
    onThemeChange: (Color) -> Unit
) {
    var dnsServer by remember { mutableStateOf(PrefsManager.getDnsServer(context)) }
    var splitTunneling by remember { mutableStateOf(PrefsManager.isSplitTunnelingEnabled(context)) }
    var splitTunnelMode by remember { mutableStateOf(PrefsManager.getSplitTunnelMode(context)) }
    var bypassRu by remember { mutableStateOf(PrefsManager.isBypassRuEnabled(context)) }
    var autoConnect by remember { mutableStateOf(PrefsManager.isAutoConnectEnabled(context)) }
    var killSwitch by remember { mutableStateOf(PrefsManager.isKillSwitchEnabled(context)) }
    var showLogs by remember { mutableStateOf(false) }
    
    // Advanced Settings State
    var fragEnabled by remember { mutableStateOf(PrefsManager.isFragmentationEnabled(context)) }
    var fragMode by remember { mutableStateOf(PrefsManager.getFragmentationMode(context)) }

    if (showLogs) {
        LogViewerDialog(onDismiss = { showLogs = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E)
                ),
                navigationIcon = {
                    IconButton(onClick = { (context as? android.app.Activity)?.finish() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                }
            )
        },
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Theme Section
            Text(
                stringResource(R.string.appearance_section),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.accent_color), color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(), 
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val colors = listOf(
                            Color(0xFFFF1744), // Red
                            Color(0xFF2979FF), // Blue
                            Color(0xFF00E676), // Green
                            Color(0xFFAA00FF), // Purple
                            Color(0xFFFF9100)  // Orange
                        )
                        
                        colors.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(color, CircleShape)
                                    .border(
                                        width = if (themeColor == color) 3.dp else 0.dp,
                                        color = Color.White,
                                        shape = CircleShape
                                    )
                                    .clickable { onThemeChange(color) }
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        
            // Раздел Общие
            Text(
                stringResource(R.string.general_section),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // DNS Настройка
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.dns_server_label), color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // DNS Presets
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                         Button(
                            onClick = {
                                dnsServer = "8.8.8.8"
                                PrefsManager.setDnsServer(context, "8.8.8.8")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = if (dnsServer == "8.8.8.8") MaterialTheme.colorScheme.primary else Color(0xFF333333))
                        ) { Text(stringResource(R.string.google_dns), fontSize = 10.sp) }
                        
                        Button(
                            onClick = {
                                dnsServer = "1.1.1.1"
                                PrefsManager.setDnsServer(context, "1.1.1.1")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = if (dnsServer == "1.1.1.1") MaterialTheme.colorScheme.primary else Color(0xFF333333))
                        ) { Text("1.1.1.1", fontSize = 10.sp) }
                        
                        Button(
                            onClick = {
                                // Just visual selection, user has to type
                                if (dnsServer == "8.8.8.8" || dnsServer == "1.1.1.1") {
                                    dnsServer = ""
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = if (dnsServer != "8.8.8.8" && dnsServer != "1.1.1.1") MaterialTheme.colorScheme.primary else Color(0xFF333333))
                        ) { Text(stringResource(R.string.custom_dns_label), fontSize = 10.sp) }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = dnsServer,
                        onValueChange = { 
                            dnsServer = it 
                            PrefsManager.setDnsServer(context, it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                        label = { Text(stringResource(R.string.dns_ip_label), color = Color.Gray) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Auto Connect
             Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.auto_connect_title), color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.auto_connect_summary), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = autoConnect,
                        onCheckedChange = { 
                            autoConnect = it 
                            PrefsManager.setAutoConnectEnabled(context, it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Smart Routing
             Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.smart_routing_title), color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.smart_routing_summary), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = bypassRu,
                        onCheckedChange = { 
                            bypassRu = it 
                            PrefsManager.setBypassRuEnabled(context, it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Advanced Connection Settings
            Text(
                stringResource(R.string.bypass_advanced_section),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

             Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.fragmentation_title), color = Color.White, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.fragmentation_summary), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = fragEnabled,
                            onCheckedChange = { 
                                fragEnabled = it 
                                PrefsManager.setFragmentationEnabled(context, it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                    
                    if (fragEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Divider(color = Color.Gray.copy(alpha=0.2f))
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(stringResource(R.string.frag_mode_label), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Preset Buttons
                            Button(
                                onClick = {
                                    fragMode = "balanced"
                                    PrefsManager.setFragmentationMode(context, "balanced")
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = if (fragMode == "balanced") MaterialTheme.colorScheme.primary else Color(0xFF333333))
                            ) { Text(stringResource(R.string.frag_mode_balanced), fontSize = 10.sp) }

                            Button(
                                onClick = {
                                    fragMode = "aggressive"
                                    PrefsManager.setFragmentationMode(context, "aggressive")
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = if (fragMode == "aggressive") MaterialTheme.colorScheme.primary else Color(0xFF333333))
                            ) { Text(stringResource(R.string.frag_mode_aggressive), fontSize = 10.sp) }
                            
                             Button(
                                onClick = {
                                    fragMode = "light"
                                    PrefsManager.setFragmentationMode(context, "light")
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = if (fragMode == "light") MaterialTheme.colorScheme.primary else Color(0xFF333333))
                            ) { Text(stringResource(R.string.frag_mode_light), fontSize = 10.sp) }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Раздел Безопасность
            Text(
                stringResource(R.string.security_section),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.kill_switch_internal), color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.kill_switch_internal_desc),
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                         Text(
                             text = if (killSwitch) stringResource(R.string.enabled_status) else stringResource(R.string.disabled_status),
                             color = Color.White
                         )
                         Switch(
                            checked = killSwitch,
                            onCheckedChange = { 
                                killSwitch = it 
                                PrefsManager.setKillSwitchEnabled(context, it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = Color.Gray.copy(alpha=0.2f))
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(stringResource(R.string.kill_switch_system), color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.kill_switch_system_desc),
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(android.provider.Settings.ACTION_VPN_SETTINGS)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Fallback
                                val intent = Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(intent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                    ) {
                        Text(stringResource(R.string.open_android_settings))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // Раздел Подключение
            Text(
                stringResource(R.string.connection_section),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.split_tunneling_title), color = Color.White, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.split_tunneling_summary), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = splitTunneling,
                            onCheckedChange = { 
                                splitTunneling = it 
                                PrefsManager.setSplitTunnelingEnabled(context, it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                    
                    if (splitTunneling) {
                        HorizontalDivider(color = Color(0xFF2C2C2C))
                        
                        // Mode Selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                             Button(
                                onClick = {
                                    splitTunnelMode = "allow"
                                    PrefsManager.setSplitTunnelMode(context, "allow")
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = if (splitTunnelMode == "allow") MaterialTheme.colorScheme.primary else Color(0xFF333333))
                            ) { Text(stringResource(R.string.mode_allow), fontSize = 10.sp) }

                            Button(
                                onClick = {
                                    splitTunnelMode = "disallow"
                                    PrefsManager.setSplitTunnelMode(context, "disallow")
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = if (splitTunnelMode == "disallow") MaterialTheme.colorScheme.primary else Color(0xFF333333))
                            ) { Text(stringResource(R.string.mode_disallow), fontSize = 10.sp) }
                        }

                        Button(
                            onClick = { 
                                context.startActivity(Intent(context, AppSelectionActivity::class.java))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.select_apps), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                stringResource(R.string.debug_section),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Button(
                onClick = { showLogs = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.show_app_logs), color = Color.White)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Support / Bug Report
            Text(
                stringResource(R.string.support_section),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Bug Report Button
            Button(
                onClick = { SettingsActivity.reportBug(context) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCD3C1A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.report_bug), color = Color.White)
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            // Support / Telegram
             Card(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/mistervoksed"))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // ignore
                    }
                },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0088CC)),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Send, // Use the imported Send icon
                        contentDescription = "Telegram",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.telegram_channel),
                        color = Color.White,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Reset Button
            Button(
                onClick = { 
                    PrefsManager.resetSettings(context)
                    // Refresh state from defaults
                    dnsServer = "8.8.8.8"
                    splitTunneling = false
                    splitTunnelMode = "allow"
                    bypassRu = false
                    autoConnect = false
                    killSwitch = false
                    fragEnabled = true
                    fragMode = "balanced"
                    // Force set balanced values through setter as resetSettings clears keys
                    PrefsManager.setFragmentationMode(context, "balanced")
                    
                    onThemeChange(Color(0xFFFF1744)) // Reset theme color in UI state
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B0000)),
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Text(stringResource(R.string.reset_settings_caps), color = MaterialTheme.colorScheme.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 12.sp)
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Text(
                stringResource(R.string.version_fmt, "0.1.2-beta (Build 3)"),
                color = Color.Gray,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun ServerItem(
    server: VpnServerConfig,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${server.protocol.name} • ${server.host}:${server.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun LogViewerDialog(onDismiss: () -> Unit) {
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(600.dp)
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.log_viewer_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        // Using Close icon if available, else Delete/Back
                        Icon(Icons.Default.Delete, contentDescription = "Close", tint = Color.Gray) 
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    reverseLayout = true 
                ) {
                    items(AppLogger.logs.reversed()) { log ->
                        Text(
                            text = "[${AppLogger.getFormattedTime(log.timestamp)}] ${log.message}",
                            color = when(log.level) {
                                LogLevel.ERROR -> MaterialTheme.colorScheme.error
                                LogLevel.DEBUG -> Color.Gray
                                else -> Color.White
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = { 
                         val text = AppLogger.logs.joinToString("\n") { "[${AppLogger.getFormattedTime(it.timestamp)}] ${it.level}: ${it.message}" }
                         clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(text))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                ) {
                     Text(stringResource(R.string.copy_to_clipboard), color = Color.White)
                }
            }
        }
    }
}
