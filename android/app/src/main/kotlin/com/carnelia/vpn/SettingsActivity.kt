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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.carnelia.vpn.ui.theme.CarheliaTheme
import com.carnelia.vpn.core.VpnServerConfig
import com.carnelia.vpn.core.VpnProtocol

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CarheliaTheme {
                SettingsScreen()
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    var servers by remember { mutableStateOf<List<VpnServerConfig>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newServerName by remember { mutableStateOf("") }
    var newServerUrl by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Параметры VPN") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Server")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                "Сохраненные серверы",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (servers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize(Alignment.Center)
                ) {
                    Text("Нет добавленных серверов")
                }
            } else {
                LazyColumn {
                    items(servers) { server ->
                        ServerItem(
                            server = server,
                            onDelete = { servers = servers.filter { it.id != server.id } }
                        )
                    }
                }
            }
        }
    }

    // Add Server Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Добавить сервер") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newServerName,
                        onValueChange = { newServerName = it },
                        label = { Text("Имя сервера") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = newServerUrl,
                        onValueChange = { newServerUrl = it },
                        label = { Text("URL или ключ доступа") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newServerName.isNotEmpty() && newServerUrl.isNotEmpty()) {
                            // Parse and add server
                            val config = VpnServerConfig(
                                id = "server-${System.currentTimeMillis()}",
                                name = newServerName,
                                protocol = VpnProtocol.OUTLINE,
                                host = "example.com",
                                port = 1234,
                                config = emptyMap()
                            )
                            servers = servers + config
                            newServerName = ""
                            newServerUrl = ""
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Добавить")
                }
            },
            dismissButton = {
                Button(onClick = { showAddDialog = false }) {
                    Text("Отмена")
                }
            }
        )
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
