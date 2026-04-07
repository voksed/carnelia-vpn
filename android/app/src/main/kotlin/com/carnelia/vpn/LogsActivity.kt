package com.carnelia.vpn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carnelia.vpn.ui.theme.CarheliaTheme
import com.carnelia.vpn.utils.AppLogger
import com.carnelia.vpn.utils.LogLevel
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share

class LogsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CarheliaTheme {
                LogsScreen(
                    onBack = { finish() },
                    onCopy = { copyLogs() },
                    onClear = { AppLogger.clear() }
                )
            }
        }
    }

    private fun copyLogs() {
        val logs = AppLogger.logs.joinToString("\n") { 
            "[${AppLogger.getFormattedTime(it.timestamp)}] ${it.level}: ${it.message}" 
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("CarneliaVPN Logs", logs)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, getString(R.string.logs_copied_toast), Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(onBack: () -> Unit, onCopy: () -> Unit, onClear: () -> Unit) {
    val logs = AppLogger.logs // Already a SnapshotStateList from mutableStateListOf

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.log_viewer_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.manual_entry_back))
                    }
                },
                actions = {
                    IconButton(onClick = onCopy) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.copy_to_clipboard))
                    }
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.clear_history))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp),
            reverseLayout = true
        ) {
            items(logs) { log ->
                 Text(
                     text = "[${AppLogger.getFormattedTime(log.timestamp)}] ${log.level}: ${log.message}",
                     color = when(log.level) {
                         LogLevel.ERROR -> Color.Red
                         LogLevel.DEBUG -> Color.Gray
                         LogLevel.INFO -> Color.Green
                     },
                     fontFamily = FontFamily.Monospace,
                     fontSize = 12.sp,
                     modifier = Modifier.padding(vertical = 2.dp)
                 )
                 Divider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
            }
        }
    }
}
