package com.carnelia.vpn.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.carnelia.vpn.R
import com.carnelia.vpn.core.VpnServerConfig
import com.carnelia.vpn.utils.ConfigParser
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntryDialog(
    onDismiss: () -> Unit,
    onSave: (VpnServerConfig) -> Unit
) {
    var configString by remember { mutableStateOf("") }
    var parsedConfig by remember { mutableStateOf<VpnServerConfig?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val emptyConfigText = stringResource(R.string.manual_entry_empty_config)
    val invalidKeyText = stringResource(R.string.invalid_key_format)
    
    // Editable fields
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    stringResource(R.string.manual_server_entry_title),
                    style = MaterialTheme.typography.titleLarge, 
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (parsedConfig == null) {
                    // Phase 1: Paste & Parse
                    OutlinedTextField(
                        value = configString,
                        onValueChange = { configString = it; error = null },
                        label = { Text(stringResource(R.string.manual_entry_paste_config_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF0088CC),
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        maxLines = 5
                    )
                    
                    if (error != null) {
                        Text(
                            text = error!!, 
                            color = Color.Red, 
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (configString.isBlank()) {
                                error = emptyConfigText
                                return@Button
                            }
                            try {
                                val config = ConfigParser.parseOrThrow(configString)
                                parsedConfig = config
                                name = config.name
                                host = config.host
                                port = config.port.toString()
                            } catch (e: Exception) {
                                error = e.message ?: invalidKeyText
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088CC))
                    ) {
                        Text(stringResource(R.string.manual_entry_parse_configuration))
                    }
                } else {
                    // Phase 2: Edit & Save
                    Text("Type: ${parsedConfig!!.protocol}", color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Server Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Host / IP") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = filterPort(it) },
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { parsedConfig = null },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF444444))
                        ) {
                            Text(stringResource(R.string.manual_entry_back))
                        }
                        Button(
                            onClick = {
                                val finalConfig = parsedConfig!!.copy(
                                    name = name,
                                    host = host,
                                    port = port.toIntOrNull() ?: parsedConfig!!.port,
                                    // Generate new ID to avoid conflicts if needed, or keep same
                                    id = UUID.randomUUID().toString() 
                                )
                                onSave(finalConfig)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088CC))
                        ) {
                            Text(stringResource(R.string.add_server_title))
                        }
                    }
                }
            }
        }
    }
}

private fun filterPort(input: String): String {
    // Allow any digit sequence, validation happens later
    return input.filter { it.isDigit() }
}
