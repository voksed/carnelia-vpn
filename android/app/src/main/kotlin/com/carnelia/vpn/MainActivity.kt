package com.carnelia.vpn

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import com.carnelia.vpn.ui.theme.AppTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Bolt
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.foundation.Image
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.result.ActivityResultLauncher

class MainActivity : AppCompatActivity() {

    private lateinit var vpnManager: VpnManager

    // Biometric lock state (v2.4.0)
    private val isAuthenticated = androidx.compose.runtime.mutableStateOf(false)

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

        // v2.4.0: Biometric / PIN lock
        if (PrefsManager.isBiometricLockEnabled(this)) {
            launchBiometricPrompt()
        } else {
            isAuthenticated.value = true
        }

        setContent {
            val context = LocalContext.current
            val authenticated by isAuthenticated
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

                if (!authenticated) {
                    // Lock screen � biometric prompt is shown on top automatically
                    androidx.compose.material3.Surface(
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {}
                } else {
                CarheliaApp(
                    vpnManager,
                    ::startVpn,
                    ::stopVpn,
                    ::switchVpn,
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
                            val text = clipData.getItemAt(0).coerceToText(this).toString()
                            importConfig(text)
                        } else {
                            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                } // end auth check
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

    private fun switchVpn(config: VpnServerConfig) {
        try {
            val serviceIntent = Intent(this, CarheliaVpnService::class.java).apply {
                action = CarheliaVpnService.ACTION_RECONNECT
                putExtra(CarheliaVpnService.EXTRA_CONFIG, config)
            }
            startService(serviceIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Error: " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        vpnManager.destroy()
    }

    private fun launchBiometricPrompt() {
        val biometricManager = androidx.biometric.BiometricManager.from(this)
        val canAuth = biometricManager.canAuthenticate(
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
            androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        if (canAuth != androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
            // Biometric/PIN not configured on device � skip lock
            isAuthenticated.value = true
            return
        }
        val executor = androidx.core.content.ContextCompat.getMainExecutor(this)
        val callback = object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                isAuthenticated.value = true
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // User cancelled or too many attempts � close the app
                finish()
            }
        }
        val prompt = androidx.biometric.BiometricPrompt(this, executor, callback)
        val info = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_prompt_title))
            .setSubtitle(getString(R.string.biometric_prompt_subtitle))
            .setAllowedAuthenticators(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(info)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarheliaApp(
    vpnManager: VpnManager,
    onConnect: (VpnServerConfig) -> Unit,
    onDisconnect: () -> Unit,
    onSwitch: (VpnServerConfig) -> Unit,
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
             if (current != null) {
                 activeConfig = current
             } else {
                 // Active server might have been deleted, fallback to first available
                 activeConfig = repository.getServers().firstOrNull()
             }
        }
    }
    
    // QR Share State
    var showShareDialog by remember { mutableStateOf(false) }
    var shareContent by remember { mutableStateOf("") }

    if (showShareDialog && shareContent.isNotEmpty()) {
        QrCodeDialog(content = shareContent, onDismiss = { showShareDialog = false })
    }

    // Security Threat Dialog
    val app = context.applicationContext as? com.carnelia.vpn.CarheliaApplication
    var showSecurityDialog by remember {
        mutableStateOf(app?.detectedThreats?.isNotEmpty() == true)
    }
    if (showSecurityDialog && app != null) {
        val threats = app.detectedThreats
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSecurityDialog = false },
            icon = {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.Warning,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color(0xFFFF6B35)
                )
            },
            title = {
                androidx.compose.material3.Text(
                    "?? ������ ������������ (${threats.size})",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                )
            },
            text = {
                androidx.compose.foundation.lazy.LazyColumn {
                    items(threats.size) { i ->
                        val threat = threats[i]
                        androidx.compose.foundation.layout.Column(
                            modifier = androidx.compose.ui.Modifier.padding(bottom = 12.dp)
                        ) {
                            androidx.compose.material3.Text(
                                text = threat.title,
                                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                                color = androidx.compose.ui.graphics.Color(0xFFFF6B35)
                            )
                            androidx.compose.material3.Text(
                                text = threat.description,
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showSecurityDialog = false }) {
                    androidx.compose.material3.Text("�������, ����������")
                }
            }
        )
    }
    
    // Server Selection Dialog
    if (showServerList) {
        ServerSelectionDialog(
            repository = repository,
            onServerSelected = { server ->
                activeConfig = server
                repository.setLastUsedServer(server)
                showServerList = false
                // If VPN is active, hot-switch without dropping the tunnel
                if (connectionState == ConnectionState.CONNECTED ||
                    connectionState == ConnectionState.RECONNECTING) {
                    onSwitch(server)
                }
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
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(R.string.menu_title),
                    modifier = Modifier.padding(start = 24.dp, bottom = 16.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(16.dp))
                
                // Settings
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.settings_title_menu)) },
                    selected = false,
                    onClick = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )

                // Geolocation Spoofing
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.geo_spoof_title)) },
                    selected = false,
                    onClick = {
                        context.startActivity(Intent(context, GeoSpoofActivity::class.java))
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )

                // Standalone Tools
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.tools_title)) },
                    selected = false,
                    onClick = {
                        context.startActivity(Intent(context, StandaloneToolsActivity::class.java))
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurface
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
                            text = when (currentTheme) {
                                AppTheme.TON -> stringResource(R.string.ton_vpn_title)
                                AppTheme.SECRET -> stringResource(R.string.voks_vpn_title)
                                else -> stringResource(R.string.carnelia_vpn_title)
                            },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "Menu",
                                tint = MaterialTheme.colorScheme.primary
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
                                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share_tooltip), tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    modifier = Modifier.shadow(elevation = 8.dp)
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.background)
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
                            ConnectionState.RECONNECTING -> stringResource(R.string.status_switching)
                            else -> stringResource(R.string.status_not_protected)
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (connectionState) {
                            ConnectionState.CONNECTED -> Color(0xFF00FF00)
                            ConnectionState.RECONNECTING -> Color(0xFFFFAA00)
                            ConnectionState.ERROR -> Color(0xFFFF1744)
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f)
                        },
                        letterSpacing = 1.sp
                    )
                    if (connectionState == ConnectionState.CONNECTED) {
                        Text(text = connectionDuration, fontSize = 24.sp, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 8.dp))
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
                                         listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
                                    } else {
                                        listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface)
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
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if(connectionState == ConnectionState.CONNECTED) 1f else 0.5f)
                        )
                         Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(4.dp, 
                                    if (connectionState == ConnectionState.CONNECTED) MaterialTheme.colorScheme.onSurface.copy(alpha=0.2f)
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha=0.1f), 
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
                             Icon(Icons.Default.ArrowDownward, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f), modifier = Modifier.size(16.dp))
                             Text(formatBytes(stats.bytesReceived), color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                         }
                         Column(horizontalAlignment = Alignment.CenterHorizontally) {
                             Icon(Icons.Default.ArrowUpward, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f), modifier = Modifier.size(16.dp))
                             Text(formatBytes(stats.bytesSent), color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                         }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // 4. Server Selection Pill
                    Surface(
                         onClick = { showServerList = true },
                         shape = RoundedCornerShape(50),
                         color = MaterialTheme.colorScheme.surfaceVariant,
                         border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.KeyboardArrowUp, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f))
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
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(
            label, 
            fontSize = 11.sp, 
            color = MaterialTheme.colorScheme.primary, 
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.scan_to_import), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
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
    var servers by remember { mutableStateOf(repository.getServers()) }
    var showManualAdd by remember { mutableStateOf(false) }
    var serverToRename by remember { mutableStateOf<VpnServerConfig?>(null) }
    var renameText by remember { mutableStateOf("") }
    var renameGroupText by remember { mutableStateOf("") }
    var pingResults by remember { mutableStateOf<Map<String, Int?>>(emptyMap()) }
    var isPinging by remember { mutableStateOf(false) }
    // v2.4.0: Search & Group filter
    var searchQuery by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf<String?>(null) } // null = All
    val pingScope = rememberCoroutineScope()
    val allGroups = remember(servers) { servers.mapNotNull { it.group }.distinct().sorted() }
    val filteredServers = remember(servers, searchQuery, selectedGroup) {
        servers.filter { server ->
            (selectedGroup == null || server.group == selectedGroup) &&
            (searchQuery.isBlank() || server.name.contains(searchQuery, ignoreCase = true) || server.host.contains(searchQuery, ignoreCase = true))
        }
    }

    fun pingServers(list: List<VpnServerConfig>) {
        if (isPinging) return
        isPinging = true
        pingResults = emptyMap()
        pingScope.launch(Dispatchers.IO) {
            val results = mutableMapOf<String, Int?>()
            for (server in list) {
                if (!isActive) break
                val ping = try {
                    val start = System.currentTimeMillis()
                    java.net.Socket().use { it.connect(java.net.InetSocketAddress(server.host, server.port), 3000) }
                    (System.currentTimeMillis() - start).toInt()
                } catch (e: Exception) { null }
                results[server.id] = ping
                withContext(Dispatchers.Main) { pingResults = results.toMap() }
            }
            withContext(Dispatchers.Main) { isPinging = false }
        }
    }

    LaunchedEffect(servers) { delay(300); pingServers(servers) }

    serverToRename?.let { server ->
        AlertDialog(
            onDismissRequest = { serverToRename = null },
            title = { Text(stringResource(R.string.rename_server_title), color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text(stringResource(R.string.rename_server_hint), color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    OutlinedTextField(
                        value = renameGroupText,
                        onValueChange = { renameGroupText = it },
                        label = { Text(stringResource(R.string.server_group_hint), color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = renameText.trim()
                    if (trimmed.isNotBlank()) {
                        repository.updateServer(server.copy(name = trimmed, group = renameGroupText.trim().ifBlank { null }))
                        servers = repository.getServers()
                    }
                    serverToRename = null
                }) { Text(stringResource(R.string.save_action), color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { serverToRename = null }) {
                    Text(stringResource(R.string.cancel_action), color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showManualAdd) {
        ManualEntryDialog(
            onDismiss = { showManualAdd = false },
            onSave = { config ->
                repository.addServer(config)
                onServerSelected(config)
                servers = repository.getServers()
                showManualAdd = false
            }
        )
    } else {
        Dialog(onDismissRequest = onDismiss) {
            Card(
                 modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
                 shape = RoundedCornerShape(24.dp),
                 colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(), 
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.select_server_btn), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Auto-Best: pick server with lowest ping
                            IconButton(
                                onClick = {
                                    val bestId = pingResults.entries.filter { it.value != null }.minByOrNull { it.value!! }?.key
                                    val best = servers.find { it.id == bestId }
                                    if (best != null) onServerSelected(best)
                                },
                                modifier = Modifier.size(32.dp),
                                enabled = pingResults.any { it.value != null }
                            ) {
                                Icon(Icons.Default.Bolt, null,
                                    tint = if (pingResults.any { it.value != null }) Color(0xFFFFCC00) else MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f),
                                    modifier = Modifier.size(18.dp))
                            }
                            if (isPinging) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f))
                                Spacer(Modifier.width(4.dp))
                            } else {
                                IconButton(onClick = { pingServers(servers) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f), modifier = Modifier.size(18.dp))
                                }
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f))
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    // Search field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.search_servers_hint), color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )

                    // Group filter chips
                    if (allGroups.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedGroup == null,
                                    onClick = { selectedGroup = null },
                                    label = { Text(stringResource(R.string.filter_all), fontSize = 12.sp) }
                                )
                            }
                            items(allGroups) { g ->
                                FilterChip(
                                    selected = selectedGroup == g,
                                    onClick = { selectedGroup = if (selectedGroup == g) null else g },
                                    label = { Text(g, fontSize = 12.sp) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Add Tools
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onImportClipboard,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                             Icon(Icons.Default.ContentPaste, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                        }
                        Button(
                            onClick = onScanQr,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                             Icon(Icons.Default.QrCodeScanner, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                        }
                        Button(
                            onClick = { showManualAdd = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                             Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(16.dp))

                    // List
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filteredServers, key = { it.id }) { server ->
                            val isSelected = activeInfo?.id == server.id
                            Card(
                                onClick = { onServerSelected(server) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) 
                                        (MaterialTheme.colorScheme.primary)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(server.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                        val pingMs = pingResults[server.id]
                                        val pingText = when {
                                            !pingResults.containsKey(server.id) -> server.host
                                            pingMs == null -> "? timeout"
                                            pingMs < 100 -> "? ${pingMs}ms"
                                            pingMs < 300 -> "? ${pingMs}ms"
                                            else -> "? ${pingMs}ms"
                                        }
                                        val pingColor = when {
                                            !pingResults.containsKey(server.id) -> MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f)
                                            pingMs == null -> Color(0xFFFF4444)
                                            pingMs < 100 -> Color(0xFF00CC66)
                                            pingMs < 300 -> Color(0xFFFFAA00)
                                            else -> Color(0xFFFF4444)
                                        }
                                        Text(pingText, color = pingColor, fontSize = 12.sp, maxLines = 1)
                                    }

                                    // Rename Button
                                    IconButton(
                                        onClick = {
                                            renameText = server.name
                                            renameGroupText = server.group ?: ""
                                            serverToRename = server
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = stringResource(R.string.rename_tooltip),
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f).copy(alpha = 0.7f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Delete Button
                                    IconButton(
                                        onClick = { 
                                            repository.removeServer(server.id)
                                            servers = repository.getServers()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete, 
                                            contentDescription = "Delete", 
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f).copy(alpha = 0.7f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                        if (filteredServers.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("No servers found. Add one!", color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

