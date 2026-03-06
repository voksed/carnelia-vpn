package com.carnelia.vpn

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.carnelia.vpn.ui.theme.CarheliaTheme
import com.carnelia.vpn.utils.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfo(
    val packageName: String,
    val name: String,
    val icon: android.graphics.Bitmap?
)

class AppSelectionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CarheliaTheme {
                AppSelectionScreen()
            }
        }
    }
}

@Composable
fun AppSelectionScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var selectedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        selectedPackages = PrefsManager.getSelectedApps(context)
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val installedApps = pm.getInstalledPackages(PackageManager.GET_META_DATA)
            installedApps.mapNotNull { pkg ->
                // Ensure we don't list ourselves
                if (pkg.packageName == context.packageName) return@mapNotNull null
                
                // Show all apps including system ones if they have internet permission or just all
                // The previous filter was too strict (launch intent only).
                // Now we show everything that has a name/icon.
                try {
                     val label = pkg.applicationInfo.loadLabel(pm).toString()
                     val icon = pkg.applicationInfo.loadIcon(pm).toBitmap()
                     AppInfo(pkg.packageName, label, icon)
                } catch (e: Exception) { null }
            }.sortedBy { it.name }
        }
        isLoading = false
    }

    fun toggleApp(packageName: String) {
        val newSet = selectedPackages.toMutableSet()
        if (newSet.contains(packageName)) {
            newSet.remove(packageName)
        } else {
            newSet.add(packageName)
        }
        selectedPackages = newSet
        PrefsManager.setSelectedApps(context, newSet)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Выберите приложения", color = MaterialTheme.colorScheme.onSurface) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = {
                    IconButton(onClick = { (context as? android.app.Activity)?.finish() }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Column(modifier = Modifier.padding(padding)) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    placeholder = { Text("Поиск приложений...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                )

                val filteredApps = apps.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
                }

                LazyColumn {
                    items(filteredApps) { app ->
                        AppItem(
                            app = app,
                            isSelected = selectedPackages.contains(app.packageName),
                            onToggle = { toggleApp(app.packageName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppItem(app: AppInfo, isSelected: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (app.icon != null) {
            Image(
                bitmap = app.icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = app.name,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}
