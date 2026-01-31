package com.carnelia.vpn

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carnelia.vpn.ui.theme.CarheliaTheme
import com.carnelia.vpn.core.*
import com.carnelia.vpn.service.CarheliaVpnService

class MainActivity : ComponentActivity() {
    
    private lateinit var vpnManager: VpnManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        vpnManager = VpnManager()
        
        setContent {
            CarheliaTheme {
                CarheliaApp(vpnManager, ::startVpn, ::stopVpn)
            }
        }
    }
    
    private fun startVpn() {
        // Create test Outline config
        val config = VpnServerConfig(
            id = "outline-test",
            name = "Test Outline Server",
            protocol = VpnProtocol.OUTLINE,
            host = "vpn.example.com",
            port = 1234,
            config = mapOf(
                "method" to "chacha20-ietf-poly1305",
                "password" to "testpassword"
            ),
            country = "US"
        )
        
        // Start VPN service
        val intent = Intent(this, CarheliaVpnService::class.java).apply {
            action = CarheliaVpnService.ACTION_CONNECT
            putExtra(CarheliaVpnService.EXTRA_CONFIG, config)
        }
        startService(intent)
        
        vpnManager.connect(config)
    }
    
    private fun stopVpn() {
        val intent = Intent(this, CarheliaVpnService::class.java).apply {
            action = CarheliaVpnService.ACTION_DISCONNECT
        }
        startService(intent)
        
        vpnManager.disconnect()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        vpnManager.destroy()
    }
}

@Composable
fun CarheliaApp(
    vpnManager: VpnManager,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    var connectionState by remember { mutableStateOf(ConnectionState.DISCONNECTED) }
    var stats by remember { mutableStateOf(VpnStats()) }
    var selectedServer by remember { mutableStateOf("Outline • Auto") }

    // Listen to VPN state changes
    LaunchedEffect(vpnManager) {
        vpnManager.onStateChanged { state ->
            connectionState = state
        }
        vpnManager.onStatsChanged { newStats ->
            stats = newStats
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Carnelia VPN") },
                actions = {
                    IconButton(onClick = { /* TODO: Settings */ }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = when (connectionState) {
                            ConnectionState.CONNECTED -> "ПОДКЛЮЧЕНО"
                            ConnectionState.CONNECTING -> "ПОДКЛЮЧЕНИЕ..."
                            ConnectionState.DISCONNECTING -> "ОТКЛЮЧЕНИЕ..."
                            ConnectionState.ERROR -> "ОШИБКА"
                            else -> "ОТКЛЮЧЕНО"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when (connectionState) {
                            ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
                            ConnectionState.ERROR -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = selectedServer,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Connect Button
            Button(
                onClick = { 
                    if (connectionState == ConnectionState.CONNECTED) {
                        onDisconnect()
                    } else {
                        onConnect()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (connectionState == ConnectionState.CONNECTED) 
                        MaterialTheme.colorScheme.error 
                    else 
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (connectionState == ConnectionState.CONNECTED) "ОТКЛЮЧИТЬ" else "ПОДКЛЮЧИТЬ",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Server Selection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Протокол",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedButton(
                        onClick = { /* TODO: Protocol selection */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Outline (Shadowsocks)")
                    }
                }
            }

            // Bottom Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("Отправлено", formatBytes(stats.bytesSent))
                StatItem("Получено", formatBytes(stats.bytesReceived))
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}
