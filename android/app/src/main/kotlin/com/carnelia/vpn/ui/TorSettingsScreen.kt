package com.carnelia.vpn.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.carnelia.vpn.utils.PrefsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    
    // TOR State
    var isTorEnabled by remember { mutableStateOf(PrefsManager.isTorEnabled(context)) }
    var useBridges by remember { mutableStateOf(PrefsManager.isTorUseBridges(context)) }
    var bridgeType by remember { mutableStateOf(PrefsManager.getTorBridgeType(context)) }
    var customBridges by remember { mutableStateOf(PrefsManager.getCustomTorBridges(context)) }
    var socksPort by remember { mutableStateOf(PrefsManager.getTorSocksPort(context)) }
    var httpPort by remember { mutableStateOf(PrefsManager.getTorHttpPort(context)) }
    
    // I2P State
    var isI2PEnabled by remember { mutableStateOf(PrefsManager.isI2pEnabled(context)) }
    
    // Check I2P Installation
    val isI2PInstalled = remember {
        try {
            context.packageManager.getPackageInfo("net.i2p.android.router", 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки Tor & I2P") },
                navigationIcon = {
                    Button(onClick = onNavigateBack) { Text("Назад") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Tor Configuration", style = MaterialTheme.typography.headlineSmall)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Включить Tor")
                Switch(
                    checked = isTorEnabled, 
                    onCheckedChange = { 
                        isTorEnabled = it
                        PrefsManager.setTorEnabled(context, it)
                    }
                )
            }

            if (isTorEnabled) {
                OutlinedTextField(
                    value = socksPort,
                    onValueChange = { 
                        socksPort = it 
                        PrefsManager.setTorSocksPort(context, it)
                    },
                    label = { Text("SOCKS Port (Default: 9050)") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
                 OutlinedTextField(
                    value = httpPort,
                    onValueChange = { 
                        httpPort = it
                        PrefsManager.setTorHttpPort(context, it)
                    },
                    label = { Text("HTTP Tunnel Port (Default: 8118)") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )

                Text("Мосты (Bridges)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top=16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Использовать мосты")
                    Switch(
                        checked = useBridges, 
                        onCheckedChange = { 
                            useBridges = it
                            PrefsManager.setTorUseBridges(context, it)
                        }
                    )
                }

                if (useBridges) {
                    var expanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { expanded = true }) {
                            Text("Тип мостов: $bridgeType")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            listOf("obfs4", "meek-azure", "snowflake").forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = { 
                                        bridgeType = type
                                        PrefsManager.setTorBridgeType(context, type)
                                        expanded = false 
                                    }
                                )
                            }
                        }
                    }
                    
                    OutlinedTextField(
                        value = customBridges,
                        onValueChange = { 
                            customBridges = it
                            PrefsManager.setCustomTorBridges(context, it)
                        },
                        label = { Text("Свои мосты (Custom Bridges)") },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        maxLines = 5
                    )
                }
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp))
            Text("I2P Configuration", style = MaterialTheme.typography.headlineSmall)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Включить I2P")
                Switch(
                    checked = isI2PEnabled, 
                    onCheckedChange = { 
                        isI2PEnabled = it
                        PrefsManager.setI2pEnabled(context, it)
                    }
                )
            }
            if (isI2PEnabled) {
                if (isI2PInstalled) {
                    Text("HTTP Proxy: 127.0.0.1:4444", style = MaterialTheme.typography.bodyMedium)
                    Text("Статус: I2P Router обнаружен", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        try {
                            val launchIntent = context.packageManager.getLaunchIntentForPackage("net.i2p.android.router")
                            context.startActivity(launchIntent)
                        } catch (e: Exception) {}
                    }) {
                        Text("Открыть I2P App")
                    }
                } else {
                    Text("Статус: Приложение I2P не установлено", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Text("Для работы I2P требуется установить I2P Android из F-Droid или Google Play.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
