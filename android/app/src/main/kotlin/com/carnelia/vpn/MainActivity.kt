package com.carnelia.vpn

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
                title = { 
                    Text(
                        "CARNELIA VPN",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp,
                        color = Color(0xFFFF1744)
                    )
                },
                actions = {
                    IconButton(onClick = { /* TODO: Settings */ }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color(0xFFFF1744)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0A0A0A)
                ),
                modifier = Modifier
                    .shadow(elevation = 8.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1A1A1A),
                                Color(0xFF0A0A0A)
                            )
                        )
                    )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0A0A))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                // Мрачный статус карточка с красными акцентами
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .shadow(elevation = 12.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1A1A1A)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.diagonalGradient(
                                    colors = listOf(
                                        Color(0xFF1A1A1A),
                                        Color(0xFF0D0D0D)
                                    )
                                )
                            )
                            .border(
                                width = 2.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFCC0000),
                                        Color(0xFF8B0000)
                                    )
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Статус индикатор
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        when (connectionState) {
                                            ConnectionState.CONNECTED -> Color(0xFF00FF00)
                                            ConnectionState.ERROR -> Color(0xFFFF1744)
                                            else -> Color(0xFF424242)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PowerSettingsNew,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = when (connectionState) {
                                    ConnectionState.CONNECTED -> "● АКТИВНО"
                                    ConnectionState.CONNECTING -> "⟳ ПОДКЛЮЧЕНИЕ"
                                    ConnectionState.DISCONNECTING -> "⟳ ОТКЛЮЧЕНИЕ"
                                    ConnectionState.ERROR -> "✕ ОШИБКА"
                                    else -> "● ОТКЛЮЧЕНО"
                                },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = when (connectionState) {
                                    ConnectionState.CONNECTED -> Color(0xFF00FF00)
                                    ConnectionState.ERROR -> Color(0xFFFF1744)
                                    else -> Color(0xFFFFFFFF)
                                },
                                letterSpacing = 1.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = selectedServer,
                                fontSize = 12.sp,
                                color = Color(0xFFFF6B6B),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Кнопка подключения - БОЛЬШАЯ И МРАЧНАЯ
                Button(
                    onClick = { 
                        if (connectionState == ConnectionState.CONNECTED) {
                            onDisconnect()
                        } else {
                            onConnect()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .shadow(elevation = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (connectionState == ConnectionState.CONNECTED) 
                            Color(0xFFCC0000)
                        else 
                            Color(0xFFE53935),
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 8.dp,
                        pressedElevation = 12.dp
                    )
                ) {
                    Text(
                        text = if (connectionState == ConnectionState.CONNECTED) 
                            "ОТКЛЮЧИТЬ" 
                        else 
                            "ПОДКЛЮЧИТЬ",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Карточка протокола
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .shadow(elevation = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF2D2D2D)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            "ПРОТОКОЛ",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFF6B6B),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedButton(
                            onClick = { /* TODO: Protocol selection */ },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFFFFFFF),
                                containerColor = Color(0xFF1A1A1A)
                            )
                        ) {
                            Text(
                                "Outline (Shadowsocks)",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Статистика - МИНИМАЛИСТИЧНО
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatBox("↓ Получено", formatBytes(stats.bytesReceived))
                    Divider(
                        modifier = Modifier
                            .width(1.dp)
                            .height(50.dp),
                        color = Color(0xFF8B0000)
                    )
                    StatBox("↑ Отправлено", formatBytes(stats.bytesSent))
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun StatBox(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(8.dp)
            .weight(1f)
    ) {
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFFFF1744)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color(0xFFFFFFFF),
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
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
