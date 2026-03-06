package com.carnelia.vpn

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.carnelia.vpn.utils.PrefsManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carnelia.vpn.ui.theme.CarheliaTheme
import com.carnelia.vpn.ui.ManualEntryDialog
import com.carnelia.vpn.core.*
import com.carnelia.vpn.service.CarheliaVpnService
import com.carnelia.vpn.data.ServerRepository
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.BorderStroke
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.carnelia.vpn.ui.theme.AppTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.window.Dialog
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.google.zxing.BarcodeFormat
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.Image
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.result.ActivityResultLauncher

class MainActivity : ComponentActivity() {

    private lateinit var vpnManager: VpnManager

    // QR Code Scanner Launcher
    private val qrCodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            importConfig(result.contents)
        }
    }

    private val vpnPrepareLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "VPN permission granted", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "VPN permission denied", Toast.LENGTH_LONG).show()
        }
    }

    private fun importConfig(configStr: String) {
        val config = com.carnelia.vpn.utils.ConfigParser.parse(configStr)
        if (config != null) {
            val repository = ServerRepository(this)
            repository.addServer(config)
            Toast.makeText(this, "Server imported: ${config.name}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Invalid config format", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vpnManager = VpnManager(this)

        setContent {
            val context = LocalContext.current
            var themeIndex by remember { mutableStateOf(PrefsManager.getThemeIndex(context)) }
            
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            DisposableEffect(lifecycle) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        themeIndex = PrefsManager.getThemeIndex(context)
                    }
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }

            CarheliaTheme(themeIndex = themeIndex) {
                val currentTheme = AppTheme.values().getOrElse(themeIndex) { AppTheme.CARNELIA }
                
                CarheliaApp(
                    vpnManager, 
                    ::startVpn, 
                    ::stopVpn, 
                    currentTheme, 
                    onScanQr = { 
                        val options = ScanOptions()
                        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        options.setPrompt("Scan VPN QR Code")
                        options.setBeepEnabled(false)
                        qrCodeLauncher.launch(options)
                    },
                    onImportClipboard = {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clipData = clipboard.primaryClip
                        if (clipData != null && clipData.itemCount > 0) {
                            val text = clipData.getItemAt(0).text.toString()
                            importConfig(text)
                        } else {
                            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

    private fun startVpn(config: VpnServerConfig) {
        try {
            val intent = android.net.VpnService.prepare(this)
            if (intent != null) {
                vpnPrepareLauncher.launch(intent)
                return
            }
            if (config.protocol == VpnProtocol.OPENVPN) {
                com.carnelia.vpn.utils.OpenVpnHelper.startVpn(this, config)
                return
            }
            val serviceIntent = Intent(this, CarheliaVpnService::class.java).apply {
                action = CarheliaVpnService.ACTION_CONNECT
                putExtra(CarheliaVpnService.EXTRA_CONFIG, config)
            }
            startService(serviceIntent)
        } catch (e: Exception) {
             Toast.makeText(this, "Error: " + e.message, Toast.LENGTH_LONG).show()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarheliaApp(
    vpnManager: VpnManager,
    onConnect: (VpnServerConfig) -> Unit,
    onDisconnect: () -> Unit,
    currentTheme: AppTheme,
    onScanQr: () -> Unit,
    onImportClipboard: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    // Data
    val connectionState by VpnGlobalState.connectionState.collectAsState()
    val stats by VpnGlobalState.stats.collectAsState()
    val repository = remember { ServerRepository(context) }
    var activeConfig by remember { 
        mutableStateOf(repository.getLastUsedServer() ?: repository.getServers().firstOrNull())
    }
    
    // Server List Dialog State
    var showServerList by remember { mutableStateOf(false) }

    LaunchedEffect(showServerList) {
        if (!showServerList) {
             val current = repository.getLastUsedServer()
             if (current != null) activeConfig = current
        }
    }
    
    // QR Share State
    var showShareDialog by remember { mutableStateOf(false) }
    var shareContent by remember { mutableStateOf("") }

    if (showShareDialog && shareContent.isNotEmpty()) {
        QrCodeDialog(content = shareContent, onDismiss = { showShareDialog = false })
    }
    
    // Server Selection Dialog
    if (showServerList) {
        ServerSelectionDialog(
            repository = repository,
            onServerSelected = { server ->
                activeConfig = server
                repository.setLastUsedServer(server)
                showServerList = false
            },
            onDismiss = { showServerList = false },
            onImportClipboard = {
                onImportClipboard()
                // Ideally refresh here
                showServerList = false 
            },
            onScanQr = {
                onScanQr()
                showServerList = false
            },
            currentTheme = currentTheme,
            activeInfo = activeConfig
        )
    }

    // Toggle State for Tor
    var isTorEnabled by remember { mutableStateOf(PrefsManager.isTorEnabled(context)) }

    // Connection Duration Timer
    var connectionDuration by remember { mutableStateOf("00:00:00") }
    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.CONNECTED) {
            while (true) {
                val duration = if (stats.connectionTime > 0) (System.currentTimeMillis() - stats.connectionTime) / 1000 else 0L
                val h = duration / 3600
                val m = (duration % 3600) / 60
                val s = duration % 60
                connectionDuration = String.format("%02d:%02d:%02d", h, m, s)
                delay(1000)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF1A1A1A),
                drawerContentColor = Color.White
            ) {
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(R.string.menu_title),
                    modifier = Modifier.padding(start = 24.dp, bottom = 16.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = Color(0xFF333333))
                Spacer(Modifier.height(16.dp))
                
                // Browser
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.private_browser_title)) },
                    selected = false,
                    onClick = {
                        try {
                            context.startActivity(Intent(context, PrivateBrowserActivity::class.java))
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.browser_opening), Toast.LENGTH_SHORT).show()
                        }
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = Color.White
                    )
                )
                
                // Settings
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.settings_title_menu)) },
                    selected = false,
                    onClick = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = Color.White
                    )
                )
            }
        }
    ) {

        // CONTENT (Clean UI Style)
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (currentTheme == AppTheme.TON) stringResource(R.string.ton_vpn_title) else stringResource(R.string.carnelia_vpn_title),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp,
                            color = if (currentTheme == AppTheme.TON) Color(0xFF0088CC) else Color(0xFFFF1744)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "Menu",
                                tint = if (currentTheme == AppTheme.TON) Color(0xFF0088CC) else Color(0xFFFF1744)
                            )
                        }
                    },
                    actions = {
                        // Share Button integrated in header
                        val canShare = activeConfig != null
                        if (canShare) {
                            IconButton(onClick = {
                                shareContent = "${activeConfig?.protocol?.name?.lowercase()}://${activeConfig?.host}:${activeConfig?.port}" 
                                showShareDialog = true
                            }) {
                                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share_tooltip), tint = Color.White)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0A0A0A)
                    ),
                    modifier = Modifier.shadow(elevation = 8.dp)
                )
            },
            containerColor = Color(0xFF0A0A0A)
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF1A1A1A), Color(0xFF0A0A0A))
                        )
                    )
                    .padding(paddingValues)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.weight(1f))

                    // 1. Connection Status Text (Small & Clean)
                    Text(
                        text = when (connectionState) {
                            ConnectionState.CONNECTED -> stringResource(R.string.status_secured)
                            ConnectionState.CONNECTING -> stringResource(R.string.status_connecting)
                            else -> stringResource(R.string.status_not_protected)
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (connectionState) {
                            ConnectionState.CONNECTED -> Color(0xFF00FF00)
                            ConnectionState.ERROR -> Color(0xFFFF1744)
                            else -> Color.Gray
                        },
                        letterSpacing = 1.sp
                    )
                    if (connectionState == ConnectionState.CONNECTED) {
                        Text(text = connectionDuration, fontSize = 24.sp, fontWeight = FontWeight.Light, color = Color.White, modifier = Modifier.padding(top = 8.dp))
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // 2. Central Connect Button (Circular Modern Look)
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .shadow(24.dp, CircleShape)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = if (connectionState == ConnectionState.CONNECTED) {
                                         if (currentTheme == AppTheme.TON) listOf(Color(0xFF0088CC), Color(0xFF003D5C))
                                         else listOf(Color(0xFFFF1744), Color(0xFFB71C1C))
                                    } else {
                                        listOf(Color(0xFF2C2C2C), Color(0xFF1A1A1A))
                                    }
                                )
                            )
                            .clickable {
                                if (connectionState == ConnectionState.CONNECTED) {
                                    onDisconnect()
                                } else {
                                    val server = activeConfig ?: repository.getServers().firstOrNull()
                                    if (server != null) {
                                        onConnect(server)
                                    } else if (isTorEnabled) {
                                        val torConfig = VpnServerConfig(
                                            id = "tor_standalone",
                                            name = "Tor Network",
                                            protocol = com.carnelia.vpn.core.VpnProtocol.SOCKS,
                                            host = "127.0.0.1",
                                            port = 9050,
                                            config = mapOf("tor_mode" to "true")
                                        )
                                        onConnect(torConfig)
                                    } else {
                                        showServerList = true
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Connect",
                            modifier = Modifier.size(80.dp),
                            tint = Color.White.copy(alpha = if(connectionState == ConnectionState.CONNECTED) 1f else 0.5f)
                        )
                         Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(4.dp, 
                                    if (connectionState == ConnectionState.CONNECTED) Color.White.copy(alpha=0.2f)
                                    else Color.White.copy(alpha=0.1f), 
                                    CircleShape
                                )
                         )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // 3. Stats Row (Minimalist)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                         Column(horizontalAlignment = Alignment.CenterHorizontally) {
                             Icon(Icons.Default.ArrowDownward, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                             Text(formatBytes(stats.bytesReceived), color = Color.White, fontSize = 12.sp)
                         }
                         Column(horizontalAlignment = Alignment.CenterHorizontally) {
                             Icon(Icons.Default.ArrowUpward, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                             Text(formatBytes(stats.bytesSent), color = Color.White, fontSize = 12.sp)
                         }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // 4. Server Selection Pill
                    Surface(
                         onClick = { showServerList = true },
                         shape = RoundedCornerShape(50),
                         color = Color(0xFF1F1F1F),
                         border = BorderStroke(1.dp, Color(0xFF333333)),
                         modifier = Modifier.height(56.dp).fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Box(modifier = Modifier.size(8.dp).background(
                                color = if(activeConfig != null) Color.Green else Color.Red,
                                shape = CircleShape
                            ))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                activeConfig?.name ?: stringResource(R.string.select_server_btn),
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.KeyboardArrowUp, null, tint = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatBox(label: String, value: String, currentTheme: AppTheme) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(
            label, 
            fontSize = 11.sp, 
            color = if (currentTheme == AppTheme.TON) Color(0xFF0088CC) else Color(0xFFCC0000), 
            fontWeight = FontWeight.Bold, 
            letterSpacing = 1.sp
        )
    }
}


fun formatBytes(bytes: Long): String {
    return when {
         bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024 * 1024.0))
    }
}

@Composable
fun QrCodeDialog(content: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.scan_to_import), style = MaterialTheme.typography.titleMedium, color = Color.Black)
                Spacer(modifier = Modifier.height(16.dp))
                
                val bitmap = remember(content) {
                    try {
                        val encoder = BarcodeEncoder()
                        encoder.encodeBitmap(content, BarcodeFormat.QR_CODE, 600, 600)
                    } catch (e: Exception) {
                        null
                    }
                }
                
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier.size(200.dp)
                    )
                } else {
                    Text(stringResource(R.string.error_generating_qr), color = Color.Red)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onDismiss) { Text(stringResource(R.string.close_button)) }
            }
        }
    }
}

@Composable
fun ServerSelectionDialog(
    repository: ServerRepository,
    onServerSelected: (VpnServerConfig) -> Unit,
    onDismiss: () -> Unit,
    onImportClipboard: () -> Unit,
    onScanQr: () -> Unit,
    currentTheme: AppTheme,
    activeInfo: VpnServerConfig?
) {
    val servers = remember { repository.getServers() }
    var showManualAdd by remember { mutableStateOf(false) }

    if (showManualAdd) {
        ManualEntryDialog(
            onDismiss = { showManualAdd = false },
            onSave = { config ->
                repository.addServer(config)
                onServerSelected(config)
                showManualAdd = false
            }
        )
    } else {
        Dialog(onDismissRequest = onDismiss) {
            Card(
                 modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
                 shape = RoundedCornerShape(24.dp),
                 colors = CardDefaults.cardColors(containerColor = Color(0xFF121212))
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(), 
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.select_server_btn), style = MaterialTheme.typography.titleLarge, color = Color.White)
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, null, tint = Color.Gray)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Add Tools
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onImportClipboard,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                             Icon(Icons.Default.ContentPaste, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        Button(
                            onClick = onScanQr,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                             Icon(Icons.Default.QrCodeScanner, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        Button(
                            onClick = { showManualAdd = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                             Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFF333333))
                    Spacer(modifier = Modifier.height(16.dp))

                    // List
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(servers) { server ->
                            val isSelected = activeInfo?.id == server.id
                            Card(
                                onClick = { onServerSelected(server) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) 
                                        (if (currentTheme == AppTheme.TON) Color(0xFF003D5C) else Color(0xFF5F0000))
                                        else Color(0xFF1F1F1F)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                 Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                     Column(modifier = Modifier.weight(1f)) {
                                         Text(server.name, color = Color.White, fontWeight = FontWeight.Bold)
                                         Text(server.host, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                                     }
                                     if (isSelected) {
                                         Icon(Icons.Default.Check, null, tint = Color.White)
                                     }
                                 }
                            }
                        }
                        if (servers.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("No servers found. Add one!", color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

