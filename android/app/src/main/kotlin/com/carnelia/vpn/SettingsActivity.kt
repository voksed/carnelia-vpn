package com.carnelia.vpn

import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.carnelia.vpn.core.VpnGlobalState
import com.carnelia.vpn.ui.theme.CarheliaTheme
import com.carnelia.vpn.utils.AppLogger
import com.carnelia.vpn.utils.LogLevel
import com.carnelia.vpn.utils.PrefsManager

class SettingsActivity : AppCompatActivity() {

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

fun changeLanguage(context: Context, languageCode: String) {
    val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(languageCode)
    AppCompatDelegate.setApplicationLocales(appLocale)
}

enum class SettingsPage {
    MAIN,
    APPEARANCE,
    SECURITY,
    CONNECTION,
    CENSORSHIP_BYPASS,
    AUTO_CONNECT,
    TUNNEL,
    LANGUAGE,
    DONATION
}

@Composable
fun SettingsScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var themeIndex by remember { mutableStateOf(PrefsManager.getThemeIndex(context)) }
    
    // Navigation State
    var currentScreen by remember { mutableStateOf(SettingsPage.MAIN) }

    // Handle System Back Button
    BackHandler(enabled = currentScreen != SettingsPage.MAIN) {
        currentScreen = SettingsPage.MAIN
    }

    CarheliaTheme(themeIndex = themeIndex) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Text(
                            text = when(currentScreen) {
                                SettingsPage.MAIN -> stringResource(R.string.settings_title)
                                SettingsPage.APPEARANCE -> stringResource(R.string.appearance_section)
                                SettingsPage.SECURITY -> stringResource(R.string.security_section)
                                SettingsPage.CONNECTION -> stringResource(R.string.connection_section)
                                SettingsPage.CENSORSHIP_BYPASS -> stringResource(R.string.bypass_advanced_section)
                                SettingsPage.AUTO_CONNECT -> stringResource(R.string.smart_auto_connect_title)
                                SettingsPage.TUNNEL -> stringResource(R.string.tunnel_settings_section)
                                SettingsPage.LANGUAGE -> stringResource(R.string.language_title)
                                SettingsPage.DONATION -> stringResource(R.string.donation_section_title)
                            },  
                            color = MaterialTheme.colorScheme.onSurface
                        ) 
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    navigationIcon = {
                        IconButton(onClick = { 
                            if (currentScreen == SettingsPage.MAIN) {
                                (context as? android.app.Activity)?.finish()
                            } else {
                                currentScreen = SettingsPage.MAIN
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                when(currentScreen) {
                    SettingsPage.MAIN -> MainSettingsMenu(
                        context = context,
                        onNavigate = { page -> currentScreen = page }
                    )
                    SettingsPage.APPEARANCE -> AppearanceSettings(
                        themeIndex = themeIndex, 
                        onThemeChange = { newIndex ->
                            themeIndex = newIndex
                            PrefsManager.setThemeIndex(context, newIndex)
                        }
                    )
                    SettingsPage.SECURITY -> SecuritySettings(context)
                    SettingsPage.CONNECTION -> ConnectionSettings(context)
                    SettingsPage.CENSORSHIP_BYPASS -> CensorshipBypassSettings(context)
                    SettingsPage.AUTO_CONNECT -> AutoConnectSettings(context)
                    SettingsPage.TUNNEL -> TunnelSettings(context)
                    SettingsPage.LANGUAGE -> LanguageSettings(context) { currentScreen = SettingsPage.MAIN }
                    SettingsPage.DONATION -> DonationSettings(context)
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MainSettingsMenu(
    context: Context,
    onNavigate: (SettingsPage) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        
        // --- Categories ---
        SettingsCategoryItem(
            icon = Icons.Default.Palette,
            title = stringResource(R.string.appearance_section),
            onClick = { onNavigate(SettingsPage.APPEARANCE) }
        )

        SettingsCategoryItem(
            icon = Icons.Default.Tune,
            title = stringResource(R.string.tunnel_settings_section),
            description = "Mux, IP Strategy, LAN, Auto Start",
            onClick = { onNavigate(SettingsPage.TUNNEL) }
        )
        
        SettingsCategoryItem(
            icon = Icons.Default.Security,
            title = stringResource(R.string.security_section),
            description = stringResource(R.string.netshield_title) + ", " + stringResource(R.string.kill_switch_internal),
            onClick = { onNavigate(SettingsPage.SECURITY) }
        )
        
        SettingsCategoryItem(
            icon = Icons.Default.Wifi,
            title = stringResource(R.string.connection_section),
            description = stringResource(R.string.split_tunneling_title) + ", DNS",
            onClick = { onNavigate(SettingsPage.CONNECTION) }
        )

         SettingsCategoryItem(
            icon = Icons.Default.Bolt,
            title = stringResource(R.string.smart_auto_connect_title),
            onClick = { onNavigate(SettingsPage.AUTO_CONNECT) }
        )

        SettingsCategoryItem(
            icon = Icons.Default.VpnLock,
            title = stringResource(R.string.bypass_advanced_section),
            description = stringResource(R.string.stealth_mode_title) + ", " + stringResource(R.string.fragmentation_title),
            onClick = { onNavigate(SettingsPage.CENSORSHIP_BYPASS) }
        )

        SettingsCategoryItem(
            icon = Icons.Default.Favorite,
            title = stringResource(R.string.donate_dev_title),
            description = stringResource(R.string.donate_dev_desc),
            onClick = { onNavigate(SettingsPage.DONATION) }
        )

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = Color(0xFF2C2C2C))
        Spacer(modifier = Modifier.height(24.dp))

        // --- Other Items (Language, Report, Logs) ---
        
        SettingsCategoryItem(
            icon = Icons.Default.Language,
            title = stringResource(R.string.language_title),
            value = androidx.core.os.LocaleListCompat.getAdjustedDefault().get(0)?.language?.uppercase() ?: "EN",
            onClick = { onNavigate(SettingsPage.LANGUAGE) }
        )

        SettingsCategoryItem(
            icon = Icons.Default.BugReport,
            title = stringResource(R.string.show_app_logs),
            onClick = { context.startActivity(Intent(context, LogsActivity::class.java)) }
        )
        
        // Report Bug
        Button(
            onClick = { SettingsActivity.reportBug(context) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCD3C1A)),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.report_bug), color = MaterialTheme.colorScheme.onPrimary)
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        // Socials
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                     try {
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/mistervoksed"))
                        context.startActivity(intent)
                    } catch (e: Exception) {}
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088CC)),
                modifier = Modifier.weight(1f)
            ) {
                 Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                 Spacer(modifier = Modifier.width(4.dp))
                 Text(stringResource(R.string.telegram_channel), fontSize = 12.sp)
            }
             Button(
                onClick = {
                     try {
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/CarneliaVPN"))
                        context.startActivity(intent)
                    } catch (e: Exception) {}
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088CC)),
                modifier = Modifier.weight(1f)
            ) {
                 Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                 Spacer(modifier = Modifier.width(4.dp))
                 Text(stringResource(R.string.telegram_news_channel), fontSize = 12.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Reset
        Button(
            onClick = { 
                PrefsManager.resetSettings(context)
                // Theme will be reset to default automatically
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth(),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        ) {
            Text(stringResource(R.string.reset_settings_caps), color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Version
        val versionInfo = try {
             val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
             "${packageInfo.versionName} (Build ${if (android.os.Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else packageInfo.versionCode})"
        } catch (e: Exception) { "1.0.0" }
        
        var clickCount by remember { mutableStateOf(0) }
        
        Text(
            text = "Version: $versionInfo",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .combinedClickable(
                    onClick = {
                        clickCount++
                        if (clickCount >= 7) {
                            clickCount = 0
                            // Launch Space Dodger
                            context.startActivity(Intent(context, EasterEggActivity::class.java))
                        }
                    },
                    onLongClick = {
                        // Launch Miner Game
                        context.startActivity(Intent(context, com.carnelia.vpn.games.MinerActivity::class.java))
                    }
                ),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SettingsCategoryItem(
    icon: ImageVector,
    title: String,
    description: String? = null,
    value: String? = null,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                if (description != null) {
                    Text(text = description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (value != null) {
                 Text(text = value, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                 Spacer(modifier = Modifier.width(8.dp))
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TunnelSettings(context: Context) {
    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        
        Text(
            text = "Mux & Protocols",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Use Mux
        var muxEnabled by remember { mutableStateOf(PrefsManager.isMuxEnabled(context)) }
        var muxTcp by remember { mutableStateOf(PrefsManager.getMuxTcpConcurrency(context)) }
        var muxUdp by remember { mutableStateOf(PrefsManager.getMuxUdpConcurrency(context)) }
        var muxQuic by remember { mutableStateOf(PrefsManager.getMuxQuicMode(context)) }
        
        SettingsCategoryItem(
            icon = Icons.Default.Merge,
            title = stringResource(R.string.use_mux_title),
            value = if (muxEnabled) stringResource(R.string.enabled_status) else stringResource(R.string.disabled_status),
            onClick = { 
                muxEnabled = !muxEnabled
                PrefsManager.setMuxEnabled(context, muxEnabled)
            }
        )
        
        AnimatedVisibility(
            visible = muxEnabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.padding(start = 16.dp)) {
                
                NumberPickerItem(
                    title = stringResource(R.string.mux_tcp_concurrency),
                    value = muxTcp,
                    range = -1..1024,
                    onValueChange = { 
                        muxTcp = it
                        PrefsManager.setMuxTcpConcurrency(context, it)
                    }
                )

                NumberPickerItem(
                    title = stringResource(R.string.mux_udp_concurrency),
                    value = muxUdp,
                    range = -1..1024,
                    onValueChange = { 
                        muxUdp = it
                        PrefsManager.setMuxUdpConcurrency(context, it)
                    }
                )

                val quicOptions = listOf(
                    stringResource(R.string.quic_mode_reject) to "reject",
                    stringResource(R.string.quic_mode_allow) to "allow"
                )
                val selectedQuicIdx = quicOptions.indexOfFirst { it.second == muxQuic }.coerceAtLeast(0)
                
                DropdownSettingItem(
                     title = stringResource(R.string.mux_quic_mode),
                     options = quicOptions,
                     selectedOptionIdx = selectedQuicIdx,
                     onOptionSelected = { idx ->
                         val newValue = quicOptions[idx].second
                         muxQuic = newValue
                         PrefsManager.setMuxQuicMode(context, newValue)
                     }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = Color(0xFF2C2C2C))
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Connectivity",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Preferred IP
        var ipType by remember { mutableStateOf(PrefsManager.getPreferredIpType(context)) }
        SettingsCategoryItem(
            icon = Icons.Default.Dns,
            title = stringResource(R.string.preferred_ip_title),
            value = when(ipType) {
                "ipv4" -> stringResource(R.string.ip_type_ipv4)
                "ipv6" -> stringResource(R.string.ip_type_ipv6)
                else -> stringResource(R.string.ip_type_auto)
            },
            onClick = {
                // Cycle: auto -> ipv4 -> ipv6
                val newType = when(ipType) {
                    "auto" -> "ipv4"
                    "ipv4" -> "ipv6"
                    else -> "auto"
                }
                ipType = newType
                PrefsManager.setPreferredIpType(context, newType)
            }
        )
        
        // Allow LAN
        var lanEnabled by remember { mutableStateOf(PrefsManager.isAllowLanEnabled(context)) }
        SettingsCategoryItem(
            icon = Icons.Default.Lan,
            title = stringResource(R.string.allow_lan_title),
            description = stringResource(R.string.allow_lan_summary),
            value = if (lanEnabled) stringResource(R.string.enabled_status) else stringResource(R.string.disabled_status),
            onClick = { 
                lanEnabled = !lanEnabled
                PrefsManager.setAllowLanEnabled(context, lanEnabled)
            }
        )
        
        // Auto Start
        var autoStart by remember { mutableStateOf(PrefsManager.isAppAutoStartEnabled(context)) }
        SettingsCategoryItem(
            icon = Icons.Default.RocketLaunch,
            title = stringResource(R.string.auto_start_title),
            value = if (autoStart) stringResource(R.string.enabled_status) else stringResource(R.string.disabled_status),
            onClick = {
                autoStart = !autoStart
                PrefsManager.setAppAutoStartEnabled(context, autoStart)
            }
        )
    }
}

@Composable
fun AppearanceSettings(themeIndex: Int, onThemeChange: (Int) -> Unit) {
    val context = LocalContext.current
    val isSecretUnlocked = PrefsManager.isSecretThemeUnlocked(context)
    
    val themes = listOf(
        com.carnelia.vpn.ui.theme.AppTheme.CARNELIA,
        com.carnelia.vpn.ui.theme.AppTheme.CYBERPUNK,
        com.carnelia.vpn.ui.theme.AppTheme.MATRIX,
        com.carnelia.vpn.ui.theme.AppTheme.PURPLE,
        com.carnelia.vpn.ui.theme.AppTheme.LIGHT_BLUE,
        com.carnelia.vpn.ui.theme.AppTheme.LIGHT_GREEN,
        com.carnelia.vpn.ui.theme.AppTheme.LIGHT_PINK,
        com.carnelia.vpn.ui.theme.AppTheme.LIGHT_PURPLE,
        com.carnelia.vpn.ui.theme.AppTheme.LIGHT,
        com.carnelia.vpn.ui.theme.AppTheme.DARK,
        com.carnelia.vpn.ui.theme.AppTheme.TON,
        com.carnelia.vpn.ui.theme.AppTheme.SYSTEM
    ) + if (isSecretUnlocked) listOf(com.carnelia.vpn.ui.theme.AppTheme.SECRET) else emptyList()
    
    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.app_theme_title), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                
                themes.forEach { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeChange(theme.ordinal) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = themeIndex == theme.ordinal,
                            onClick = { onThemeChange(theme.ordinal) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = theme.colorScheme.primary,
                                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = stringResource(id = theme.displayNameResId),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        // Preview colors
                        Row {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(theme.colorScheme.primary, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(theme.colorScheme.surface, CircleShape)
                                    .border(1.dp, theme.colorScheme.onSurface, CircleShape)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SecuritySettings(context: Context) {
    var netShieldEnabled by remember { mutableStateOf(PrefsManager.isNetShieldEnabled(context)) }
    var secureKeyCheckEnabled by remember { mutableStateOf(PrefsManager.isSecureKeyCheckEnabled(context)) }
    var killSwitch by remember { mutableStateOf(PrefsManager.isKillSwitchEnabled(context)) }

    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        
        // NetShield
        ToggleCard(
            title = stringResource(R.string.netshield_title),
            description = stringResource(R.string.netshield_desc),
            checked = netShieldEnabled,
            onCheckedChange = { 
                netShieldEnabled = it
                PrefsManager.setNetShieldEnabled(context, it)
                VpnGlobalState.isNetShieldEnabled = it
            }
        )
        
        Spacer(modifier = Modifier.height(12.dp))

        // Secure Key Check
        ToggleCard(
            title = stringResource(R.string.secure_key_title),
            description = stringResource(R.string.secure_key_desc),
            checked = secureKeyCheckEnabled,
            onCheckedChange = { 
                secureKeyCheckEnabled = it
                PrefsManager.setSecureKeyCheckEnabled(context, it)
                VpnGlobalState.isSecureKeyCheckEnabled = it
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Internal Kill Switch
        ToggleCard(
            title = stringResource(R.string.kill_switch_internal),
            description = stringResource(R.string.kill_switch_internal_desc),
            checked = killSwitch,
            onCheckedChange = { 
                killSwitch = it 
                PrefsManager.setKillSwitchEnabled(context, it)
            }
        )

         Spacer(modifier = Modifier.height(16.dp))

        // System Kill Switch
        Button(
            onClick = {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_VPN_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.kill_switch_system), color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
fun NumberPickerItem(
    title: String,
    value: Int,
    range: IntRange = -1..1024,
    onValueChange: (Int) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), // Change color to surfaceVariant
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { 
                        val newValue = value - 1
                        if (newValue >= range.first) onValueChange(newValue) 
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove, // Use internal check if imported
                        contentDescription = "Decrease",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                Text(
                    text = "$value",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                IconButton(
                    onClick = { 
                        val newValue = value + 1
                        if (newValue <= range.last) onValueChange(newValue) 
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add, // Ensure imports
                        contentDescription = "Increase",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun DropdownSettingItem(
    title: String,
    options: List<Pair<String, String>>, // Label, Value
    selectedOptionIdx: Int,
    onOptionSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { expanded = true },
        shape = RoundedCornerShape(12.dp)
    ) {
        Box {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                
                Text(
                    text = options.getOrNull(selectedOptionIdx)?.first ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option.first) },
                        onClick = {
                            onOptionSelected(index)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ConnectionSettings(context: Context) {
    // State
    var bypassRu by remember { mutableStateOf(PrefsManager.isBypassRuEnabled(context)) }
    var splitTunneling by remember { mutableStateOf(PrefsManager.isSplitTunnelingEnabled(context)) }
    var splitTunnelMode by remember { mutableStateOf(PrefsManager.getSplitTunnelMode(context)) }
    var dnsServer by remember { mutableStateOf(PrefsManager.getDnsServer(context)) }

    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        
        // Smart Routing (Bypass RU)
        ToggleCard(
            title = stringResource(R.string.smart_routing_title),
            description = stringResource(R.string.smart_routing_summary),
            checked = bypassRu,
            onCheckedChange = { 
                bypassRu = it 
                PrefsManager.setBypassRuEnabled(context, it)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Split Tunneling
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.split_tunneling_title), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.split_tunneling_summary), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                         Button(
                            onClick = {
                                splitTunnelMode = "allow"
                                PrefsManager.setSplitTunnelMode(context, "allow")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = if (splitTunnelMode == "allow") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        ) { Text(stringResource(R.string.mode_allow), fontSize = 10.sp, color = if (splitTunnelMode == "allow") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) }

                        Button(
                            onClick = {
                                splitTunnelMode = "disallow"
                                PrefsManager.setSplitTunnelMode(context, "disallow")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = if (splitTunnelMode == "disallow") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        ) { Text(stringResource(R.string.mode_disallow), fontSize = 10.sp, color = if (splitTunnelMode == "disallow") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) }
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

        // DNS
        Card(
             colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
             modifier = Modifier.fillMaxWidth()
        ) {
             Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.dns_server_label), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                
                // DNS Presets Row 1
                 Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                     Button(
                        onClick = {
                            dnsServer = "8.8.8.8"
                            PrefsManager.setDnsServer(context, "8.8.8.8")
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = if (dnsServer == "8.8.8.8") MaterialTheme.colorScheme.primary else Color(0xFF333333))
                    ) { Text("Google", fontSize = 10.sp) }
                    
                    Button(
                        onClick = {
                            dnsServer = "1.1.1.1"
                            PrefsManager.setDnsServer(context, "1.1.1.1")
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = if (dnsServer == "1.1.1.1") MaterialTheme.colorScheme.primary else Color(0xFF333333))
                    ) { Text("Cloudflare", fontSize = 10.sp) }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // DNS Presets Row 2
                 Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                     Button(
                        onClick = {
                            dnsServer = "94.140.14.14" // AdGuard Default
                            PrefsManager.setDnsServer(context, "94.140.14.14")
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = if (dnsServer == "94.140.14.14") MaterialTheme.colorScheme.primary else Color(0xFF333333))
                    ) { Text("AdGuard", fontSize = 10.sp) }
                    
                    Button(
                        onClick = {
                            dnsServer = "9.9.9.9" // Quad9
                            PrefsManager.setDnsServer(context, "9.9.9.9")
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = if (dnsServer == "9.9.9.9") MaterialTheme.colorScheme.primary else Color(0xFF333333))
                    ) { Text("Quad9", fontSize = 10.sp) }
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = dnsServer,
                    onValueChange = { 
                        dnsServer = it 
                        PrefsManager.setDnsServer(context, it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface),
                    label = { Text(stringResource(R.string.dns_ip_label), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                )
             }
        }
    }
}

@Composable
fun CensorshipBypassSettings(context: Context) {
    var stealthModeEnabled by remember { mutableStateOf(PrefsManager.isStealthModeEnabled(context)) }
    var fragEnabled by remember { mutableStateOf(PrefsManager.isFragmentationEnabled(context)) }
    var fragMode by remember { mutableStateOf(PrefsManager.getFragmentationMode(context)) }

    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        
        // Stealth Mode
        ToggleCard(
            title = stringResource(R.string.stealth_mode_title),
            description = stringResource(R.string.stealth_mode_desc),
            checked = stealthModeEnabled,
            onCheckedChange = { 
                stealthModeEnabled = it
                PrefsManager.setStealthModeEnabled(context, it)
                VpnGlobalState.isStealthModeEnabled = it
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Tor Settings
        var useInternalTor by remember { mutableStateOf(PrefsManager.isUseInternalTor(context)) }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.tor_integration_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface 
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                // Use Internal Binary vs External Orbot
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                     Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (useInternalTor) stringResource(R.string.tor_internal_title) else stringResource(R.string.tor_external_title),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (useInternalTor) stringResource(R.string.tor_internal_desc) else stringResource(R.string.tor_external_desc),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = useInternalTor,
                        onCheckedChange = { 
                            useInternalTor = it
                            PrefsManager.setUseInternalTor(context, it)
                        }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Fragmentation
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.fragmentation_title), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.fragmentation_summary), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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
                    Spacer(modifier = Modifier.height(12.dp))
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
    }
}

@Composable
fun AutoConnectSettings(context: Context) {
    var autoConnect by remember { mutableStateOf(PrefsManager.isAutoConnectEnabled(context)) }
    var autoConnectWifi by remember { mutableStateOf(PrefsManager.isAutoConnectWifiEnabled(context)) }
    var autoConnectMobile by remember { mutableStateOf(PrefsManager.isAutoConnectMobileEnabled(context)) }

    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
         Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // On Boot
                 Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.auto_connect_title), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.auto_connect_summary), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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
                
                HorizontalDivider(color = Color(0xFF333333), thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
                
                // On Wi-Fi
                 Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.auto_connect_wifi_title), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.auto_connect_wifi_summary), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = autoConnectWifi,
                        onCheckedChange = { 
                            autoConnectWifi = it 
                            PrefsManager.setAutoConnectWifiEnabled(context, it)
                        },
                         colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
                
                HorizontalDivider(color = Color(0xFF333333), thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

                // On Mobile
                 Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.auto_connect_mobile_title), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.auto_connect_mobile_summary), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = autoConnectMobile,
                        onCheckedChange = { 
                            autoConnectMobile = it 
                            PrefsManager.setAutoConnectMobileEnabled(context, it)
                        },
                         colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun LanguageSettings(context: Context, onLanguageSelected: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Card(
             colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
             modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    "English", 
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            changeLanguage(context, "en")
                            onLanguageSelected()
                        }
                        .padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge
                )
                HorizontalDivider(color = Color(0xFF2C2C2C))
                Text(
                    "Русский", 
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            changeLanguage(context, "ru")
                            onLanguageSelected()
                        }
                        .padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
fun DonationSettings(context: Context) {
    val tonAddress = "UQAbDu4gRTfT814NWq-1E-ENhC57qGzR80WfOgaB_LXOsUN-"
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = null,
            tint = Color(0xFFFF4081),
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = stringResource(R.string.donate_page_header),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = stringResource(R.string.donate_page_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))
        
        Card(
             colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
             modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                 Row(verticalAlignment = Alignment.CenterVertically) {
                     Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = Color(0xFF0088CC))
                     Spacer(modifier = Modifier.width(8.dp))
                     Text(stringResource(R.string.donate_ton_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                 }
                 Spacer(modifier = Modifier.height(8.dp))
                 
                 Box(
                     modifier = Modifier
                         .background(Color(0xFF222222), RoundedCornerShape(8.dp))
                         .padding(12.dp)
                         .clickable {
                             val clip = android.content.ClipData.newPlainText("TON Address", tonAddress)
                             clipboardManager.setPrimaryClip(clip)
                             Toast.makeText(context, context.getString(R.string.address_copied_toast), Toast.LENGTH_SHORT).show()
                         }
                 ) {
                     Text(
                         text = tonAddress,
                         fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                         fontSize = 12.sp,
                         color = Color.LightGray,
                         textAlign = androidx.compose.ui.text.style.TextAlign.Center
                     )
                 }
                 
                 Spacer(modifier = Modifier.height(16.dp))
                 
                 Button(
                    onClick = {
                        val clip = android.content.ClipData.newPlainText("TON Address", tonAddress)
                        clipboardManager.setPrimaryClip(clip)
                         Toast.makeText(context, context.getString(R.string.address_copied_toast), Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.copy_address_btn))
                }
            }
        }
    }
}

@Composable
fun ToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                Text(text = description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

@Composable
fun LogViewerDialog(onDismiss: () -> Unit) { // Kept for reference, though moved to separate activity mostly
    // ... logic same as before if needed, but we now use LogsActivity
}
