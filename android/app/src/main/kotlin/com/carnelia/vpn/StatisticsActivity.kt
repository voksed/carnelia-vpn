package com.carnelia.vpn

import android.content.Context
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carnelia.vpn.core.TrafficStatsManager
import com.carnelia.vpn.core.TrafficSession
import com.carnelia.vpn.ui.theme.CarheliaTheme
import com.carnelia.vpn.utils.PrefsManager
import java.text.SimpleDateFormat
import java.util.*

class StatisticsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            CarheliaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background 
                ) {
                    StatisticsScreen(
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var history by remember { mutableStateOf(TrafficStatsManager.getHistory(context)) }
    var selectedUnit by remember { mutableStateOf("auto") } // auto, mb, gb

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_screen_title), color = MaterialTheme.colorScheme.onSurface) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        TrafficStatsManager.clearHistory(context)
                        history = emptyList()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.clear_history), tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Unit Selector
            Row(
                modifier = Modifier
                   .fillMaxWidth()
                   .padding(16.dp)
                   .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                   .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                UnitTab(stringResource(R.string.unit_auto), selectedUnit == "auto") { selectedUnit = "auto" }
                UnitTab(stringResource(R.string.unit_mb), selectedUnit == "mb") { selectedUnit = "mb" }
                UnitTab(stringResource(R.string.unit_gb), selectedUnit == "gb") { selectedUnit = "gb" }
            }
            
            // Traffic Chart
            if (history.isNotEmpty()) {
                TrafficChart(history = history, modifier = Modifier.fillMaxWidth().height(140.dp).padding(horizontal = 16.dp, vertical = 8.dp))
            }

            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.date_header), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(1.2f))
                Text(stringResource(R.string.downloaded_header), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.uploaded_header), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(1f))
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            if (history.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(history) { session ->
                        SessionItem(session, selectedUnit)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun UnitTab(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, 
                RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text, 
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 12.sp
        )
    }
}

@Composable
fun SessionItem(session: TrafficSession, unitMode: String) {
    val date = Date(session.timestamp)
    val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Date/Time
        Column(modifier = Modifier.weight(1.2f)) {
            Text(dateFormat.format(date), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(timeFormat.format(date), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        
        // Down
        Text(
            formatData(session.bytesReceived, unitMode), 
            color = Color(0xFF00E676), 
            fontSize = 14.sp, 
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Medium
        )
        
        // Up
        Text(
            formatData(session.bytesSent, unitMode), 
            color = Color(0xFF2979FF), 
            fontSize = 14.sp, 
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Medium
        )
    }
}

fun formatData(bytes: Long, mode: String): String {
    return when (mode) {
        "mb" -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        "gb" -> String.format("%.3f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        else -> {
            // Auto
            if (bytes < 1024 * 1024) {
                 String.format("%.0f KB", bytes / 1024.0)
            } else if (bytes < 1024 * 1024 * 1024) {
                 String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            } else {
                 String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            }
        }
    }
}

@Composable
fun TrafficChart(history: List<com.carnelia.vpn.core.TrafficSession>, modifier: Modifier = Modifier) {
    // Group by day (last 7 days)
    val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val cal = Calendar.getInstance()
    val days = (6 downTo 0).map { offset ->
        cal.timeInMillis = System.currentTimeMillis()
        cal.add(Calendar.DAY_OF_YEAR, -offset)
        dayFormat.format(cal.time)
    }
    val dailyDown = days.map { day ->
        history.filter { dayFormat.format(Date(it.timestamp)) == day }.sumOf { it.bytesReceived }
    }
    val dailyUp = days.map { day ->
        history.filter { dayFormat.format(Date(it.timestamp)) == day }.sumOf { it.bytesSent }
    }
    val maxVal = (dailyDown + dailyUp).maxOrNull()?.toFloat() ?: 1f
    val downColor = Color(0xFF00E676)
    val upColor = Color(0xFF2979FF)
    val gridColor = Color(0xFF333333)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val count = days.size
        val step = w / (count - 1).coerceAtLeast(1)

        // Draw grid lines
        for (i in 0..3) {
            val y = h * i / 3f
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }

        // Draw download line
        val downPath = Path()
        dailyDown.forEachIndexed { idx, bytes ->
            val x = idx * step
            val y = h - (bytes.toFloat() / maxVal) * h * 0.9f
            if (idx == 0) downPath.moveTo(x, y) else downPath.lineTo(x, y)
        }
        drawPath(downPath, downColor, style = Stroke(width = 3f))

        // Draw upload line
        val upPath = Path()
        dailyUp.forEachIndexed { idx, bytes ->
            val x = idx * step
            val y = h - (bytes.toFloat() / maxVal) * h * 0.9f
            if (idx == 0) upPath.moveTo(x, y) else upPath.lineTo(x, y)
        }
        drawPath(upPath, upColor, style = Stroke(width = 3f))

        // Dots
        dailyDown.forEachIndexed { idx, bytes ->
            val x = idx * step
            val y = h - (bytes.toFloat() / maxVal) * h * 0.9f
            drawCircle(downColor, radius = 4f, center = Offset(x, y))
        }
        dailyUp.forEachIndexed { idx, bytes ->
            val x = idx * step
            val y = h - (bytes.toFloat() / maxVal) * h * 0.9f
            drawCircle(upColor, radius = 4f, center = Offset(x, y))
        }
    }
}
