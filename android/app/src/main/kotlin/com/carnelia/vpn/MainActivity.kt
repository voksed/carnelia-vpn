package com.carnelia.vpn

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import com.carnelia.vpn.utils.PrefsManager // Added

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carnelia.vpn.ui.theme.CarheliaTheme
import com.carnelia.vpn.core.*
import com.carnelia.vpn.utils.ConfigParser
import com.carnelia.vpn.service.CarheliaVpnService
import com.carnelia.vpn.data.ServerRepository
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalClipboardManager
import com.carnelia.vpn.utils.NetworkUtils
import com.carnelia.vpn.utils.AppLogger

import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

import androidx.compose.ui.res.stringResource
import com.carnelia.vpn.R

class MainActivity : ComponentActivity() {
    
    private lateinit var vpnManager: VpnManager
    
    private val vpnPrepareLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            AppLogger.log("VPN permission granted")
            Toast.makeText(this, getString(R.string.permission_granted_toast), Toast.LENGTH_LONG).show()
        } else {
            AppLogger.error("VPN permission denied by user")
            Toast.makeText(this, getString(R.string.permission_denied_toast), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        AppLogger.log("App Started. Board: ${android.os.Build.BOARD}, Android: ${android.os.Build.VERSION.SDK_INT}")
        
        vpnManager = VpnManager(this) // Pass context for prefs
        
        setContent {
            val context = LocalContext.current
            // Use Long state then convert to Color(Int)
            var themeColorLong by remember { mutableStateOf(PrefsManager.getThemeColor(context)) }
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            
            DisposableEffect(lifecycle) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        themeColorLong = PrefsManager.getThemeColor(context)
                    }
                }
                lifecycle.addObserver(observer)
                onDispose {
                    lifecycle.removeObserver(observer)
                }
            }

            val themeColor = Color(themeColorLong.toInt())

            CarheliaTheme(accentColor = themeColor) {
                CarheliaApp(vpnManager, ::startVpn, ::stopVpn)
            }
        }
    }
    
    private fun startVpn(config: VpnServerConfig) {
        try {
            AppLogger.log("Requesting VPN connection to ${config.name} (${config.host})")
            val intent = android.net.VpnService.prepare(this)
            if (intent != null) {
                AppLogger.log("VPN permission required, launching intent")
                vpnPrepareLauncher.launch(intent)
                return
            }

            val serviceIntent = Intent(this, CarheliaVpnService::class.java).apply {
                action = CarheliaVpnService.ACTION_CONNECT
                putExtra(CarheliaVpnService.EXTRA_CONFIG, config)
            }
            startService(serviceIntent)
            AppLogger.log("Service start command sent")
        } catch (e: Exception) {
            AppLogger.error("Failed to start VPN", e)
             Toast.makeText(this, getString(R.string.vpn_start_error, e.message), Toast.LENGTH_LONG).show()
        }
    }
    
    private fun stopVpn() {
        val intent = Intent(this, CarheliaVpnService::class.java).apply {
            action = CarheliaVpnService.ACTION_DISCONNECT
        }
        startService(intent)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        vpnManager.destroy()
    }
}

// removed invalid imports

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarheliaApp(
    vpnManager: VpnManager,
    onConnect: (VpnServerConfig) -> Unit,
    onDisconnect: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val repository = remember { ServerRepository(context) }
    
    val connectionState by VpnGlobalState.connectionState.collectAsState()
    val stats by VpnGlobalState.stats.collectAsState()
    var serverList by remember { mutableStateOf(repository.getServers()) }
    var activeConfig by remember { 
        mutableStateOf(repository.getLastUsedServer())
    }
    
    // Stats / Speed
    var lastStats by remember { mutableStateOf<VpnStats?>(null) }
    var lastTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var downloadSpeed by remember { mutableStateOf(0L) }
    var uploadSpeed by remember { mutableStateOf(0L) }
    val downloadHistory = remember { mutableStateListOf<Long>() }
    val uploadHistory = remember { mutableStateListOf<Long>() }
    
    val selectedServer = activeConfig?.name ?: stringResource(R.string.select_server_hint)
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (PrefsManager.isAutoConnectEnabled(context) && activeConfig != null) {
            // Auto Connect logic - delay slightly to ensure UI is ready
            // Ideally should check if already connected
            // Using a small delay to avoid race conditions on fresh start
            kotlinx.coroutines.delay(500) 
            if (connectionState == ConnectionState.DISCONNECTED) {
                onConnect(activeConfig!!)
            }
        }
    }

    LaunchedEffect(stats) {
         val now = System.currentTimeMillis()
         val timeDiff = now - lastTime
         if (timeDiff >= 1000) {
             if (lastStats != null) {
                 val rxDiff = stats.bytesReceived - lastStats!!.bytesReceived
                 val txDiff = stats.bytesSent - lastStats!!.bytesSent
                 // Ensure non-negative (can happen on reconnect)
                 downloadSpeed = if (rxDiff >= 0) rxDiff * 1000 / timeDiff else 0
                 uploadSpeed = if (txDiff >= 0) txDiff * 1000 / timeDiff else 0
                 
                 downloadHistory.add(downloadSpeed)
                 if (downloadHistory.size > 50) downloadHistory.removeAt(0)
                 
                 uploadHistory.add(uploadSpeed)
                 if (uploadHistory.size > 50) uploadHistory.removeAt(0)
             }
             lastStats = stats
             lastTime = now
         }
    }

    LaunchedEffect(vpnManager) {
        vpnManager.onError { errorMsg ->
            android.widget.Toast.makeText(context, context.getString(R.string.error_prefix, errorMsg), android.widget.Toast.LENGTH_LONG).show()
        }
    }

    if (showAddDialog) {
        var keyText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.add_server_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.add_server_instruction))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = keyText,
                        onValueChange = { keyText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.access_key_label)) },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val clipText = clipboardManager.getText()?.text
                                    if (!clipText.isNullOrBlank()) {
                                        keyText = clipText
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.clipboard_empty), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(Icons.Default.ContentPaste, stringResource(R.string.paste_button)) // Fallback icon if Paste missing or just Add
                            }
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            val config = ConfigParser.parse(keyText)
                            if (config != null) {
                                repository.addServer(config)
                                serverList = repository.getServers()
                                activeConfig = config
                                repository.setLastUsedServer(config)
                                
                                Toast.makeText(context, context.getString(R.string.server_added, config.name), Toast.LENGTH_SHORT).show()
                                showAddDialog = false
                            } else {
                                Toast.makeText(context, context.getString(R.string.invalid_key_format), Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(context, context.getString(R.string.parsing_error, e.message), Toast.LENGTH_LONG).show()
                        }
                    }
                ) {
                    Text(stringResource(R.string.add_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.cancel_action))
                }
            }
        )
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
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    IconButton(onClick = { 
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                            tint = MaterialTheme.colorScheme.primary
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_server_title))
            }
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
                        .height(280.dp)
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
                                brush = Brush.linearGradient(
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
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.primaryContainer
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
                                            ConnectionState.ERROR -> MaterialTheme.colorScheme.error
                                            else -> Color(0xFF424242)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = when (connectionState) {
                                    ConnectionState.CONNECTED -> stringResource(R.string.status_active)
                                    ConnectionState.CONNECTING -> stringResource(R.string.connecting_status)
                                    ConnectionState.DISCONNECTING -> stringResource(R.string.disconnecting_status)
                                    ConnectionState.ERROR -> stringResource(R.string.status_error)
                                    else -> stringResource(R.string.status_inactive)
                                },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = when (connectionState) {
                                    ConnectionState.CONNECTED -> Color(0xFF00FF00)
                                    ConnectionState.ERROR -> MaterialTheme.colorScheme.error
                                    else -> Color(0xFFFFFFFF)
                                },
                                letterSpacing = 1.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = selectedServer,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, // Actually primary variant often
                                fontWeight = FontWeight.SemiBold
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            if (connectionState == ConnectionState.CONNECTED) {
                                SpeedGraph(
                                    downloadHistory = downloadHistory, 
                                    uploadHistory = uploadHistory,
                                    primaryColor = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("↓ ${formatBytes(downloadSpeed)}/s", color = Color(0xFF00E676), fontSize = 10.sp)
                                    Text("↑ ${formatBytes(uploadSpeed)}/s", color = Color(0xFF2979FF), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Кнопка подключения - БОЛЬШАЯ И МРАЧНАЯ
                Button(
                    onClick = { 
                        if (connectionState == ConnectionState.CONNECTED || connectionState == ConnectionState.CONNECTING || connectionState == ConnectionState.PREPARING) {
                            onDisconnect()
                        } else {
                            if (activeConfig != null) {
                                onConnect(activeConfig!!)
                            } else {
                                Toast.makeText(context, context.getString(R.string.please_add_server), Toast.LENGTH_SHORT).show()
                                showAddDialog = true
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .shadow(elevation = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (connectionState == ConnectionState.CONNECTED || connectionState == ConnectionState.CONNECTING || connectionState == ConnectionState.PREPARING) 
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
                        text = when (connectionState) {
                            ConnectionState.CONNECTED -> stringResource(R.string.disconnect_button)
                            ConnectionState.CONNECTING, ConnectionState.PREPARING -> stringResource(R.string.cancel_upper)
                            else -> stringResource(R.string.connect_button)
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // СПИСОК СЕРВЕРОВ
                Text(
                    stringResource(R.string.your_servers),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF888888),
                    letterSpacing = 1.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(serverList) { server ->
                        ServerItem(
                            server = server,
                            isActive = server.id == activeConfig?.id,
                            onSelect = { 
                                activeConfig = server
                                repository.setLastUsedServer(server)
                            },
                            onDelete = {
                                repository.removeServer(server.id)
                                serverList = repository.getServers()
                                if (activeConfig?.id == server.id) {
                                    activeConfig = null
                                }
                            }
                        )
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
                    StatBox(stringResource(R.string.download_label), formatSpeed(downloadSpeed), formatBytes(stats.bytesReceived))
                    Divider(
                        modifier = Modifier
                            .width(1.dp)
                            .height(50.dp),
                        color = Color(0xFF8B0000)
                    )
                    StatBox(stringResource(R.string.upload_label), formatSpeed(uploadSpeed), formatBytes(stats.bytesSent))
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun RowScope.StatBox(label: String, speed: String, total: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(8.dp)
            .weight(1f)
    ) {
        Text(
            text = speed,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFFFF1744)
        )
        Text(
            text = "$label: $total",
            fontSize = 10.sp,
            color = Color(0xFFAAAAAA),
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
    }
}

fun formatSpeed(bytesPerSec: Long): String {
    return when {
        bytesPerSec < 1024 -> "$bytesPerSec B/s"
        bytesPerSec < 1024 * 1024 -> "${bytesPerSec / 1024} KB/s"
        else -> String.format("%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
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

@Composable
fun ServerItem(
    server: VpnServerConfig,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    var pingMs by remember { mutableStateOf<Long?>(null) }
    var isPinging by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Initial ping check
        isPinging = true
        pingMs = NetworkUtils.pingServer(server.host, server.port)
        isPinging = false
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isActive) 1.dp else 0.dp,
                color = if (isActive) Color(0xFFFF1744) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1
                )
                Text(
                    text = "${server.protocol} • ${server.host}",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }

            // Ping Badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPinging) {
                     // Temporary fix for CircularProgressIndicator crash on some devices/Compose versions
                     Text(
                        "...",
                        color = Color.Gray,
                        modifier = Modifier.padding(end = 8.dp),
                        fontWeight = FontWeight.Bold
                     )
                } else if (pingMs != null) {
                    val color = when {
                        pingMs!! < 0 -> Color.Red
                        pingMs!! < 150 -> Color.Green
                        pingMs!! < 300 -> Color.Yellow
                        else -> Color(0xFFFF9800) // Orange
                    }
                    val text = if (pingMs!! < 0) "TIMEOUT" else "${pingMs}ms"
                    
                    Text(
                        text = text,
                        color = color,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFF888888),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SpeedGraph(
    downloadHistory: List<Long>,
    uploadHistory: List<Long>,
    primaryColor: Color
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .height(80.dp)
            .background(Color(0xFF0F0F0F), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
    ) {
        val width = size.width
        val height = size.height
        val maxPoints = 51 // History size usually 50 + 1
        
        // Compute max value for scaling
        val maxVal = maxOf(
            downloadHistory.maxOrNull() ?: 1L,
            uploadHistory.maxOrNull() ?: 1L
        ).coerceAtLeast(1024L) // At least 1KB scaling
        
        val stepX = width / (maxPoints - 1)
        
        // Download Path (Green)
        val dlPath = Path()
        if (downloadHistory.isNotEmpty()) {
            dlPath.moveTo(0f, height - (downloadHistory[0] / maxVal.toFloat()) * height)
            downloadHistory.forEachIndexed { i, speed ->
                val x = i * stepX
                val y = height - (speed / maxVal.toFloat()) * height
                dlPath.lineTo(x, y)
            }
        }
        drawPath(
            path = dlPath,
            color = Color(0xFF00E676),
            style = Stroke(width = 2.dp.toPx())
        )
        
        // Upload Path (Blue)
        val ulPath = Path()
        if (uploadHistory.isNotEmpty()) {
            ulPath.moveTo(0f, height - (uploadHistory[0] / maxVal.toFloat()) * height)
            uploadHistory.forEachIndexed { i, speed ->
                val x = i * stepX
                val y = height - (speed / maxVal.toFloat()) * height
                ulPath.lineTo(x, y)
            }
        }
        drawPath(
            path = ulPath,
            color = Color(0xFF2979FF),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}
