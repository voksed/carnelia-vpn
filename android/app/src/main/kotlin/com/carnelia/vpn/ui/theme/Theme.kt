package com.carnelia.vpn.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.carnelia.vpn.utils.PrefsManager

// Function to generate color scheme based on primary color
fun createDarkScheme(primary: Color): androidx.compose.material3.ColorScheme {
    return darkColorScheme(
        primary = primary,
        onPrimary = Color(0xFF000000),
        primaryContainer = primary.copy(alpha = 0.5f), // Darker version
        onPrimaryContainer = Color(0xFFFFFFFF),
        
        secondary = primary.copy(alpha = 0.8f),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = primary.copy(alpha = 0.3f), 
        onSecondaryContainer = primary.copy(alpha = 1f), // Lighter text
        
        tertiary = Color(0xFFFFFFFF),
        onTertiary = Color(0xFF000000),
        tertiaryContainer = Color(0xFFE0E0E0),
        onTertiaryContainer = Color(0xFF000000),
        
        error = Color(0xFFCF6679), // Standard Material Error Red
        errorContainer = Color(0xFFB3261E),
        onError = Color(0xFF000000),
        onErrorContainer = Color(0xFFFFFFFF),
        
        background = Color(0xFF0A0A0A),
        onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFF1A1A1A),
        onSurface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFF2D2D2D),
        onSurfaceVariant = Color(0xFFCAC4D0) // Standard text color, not primary
    )
}

@Composable
fun CarheliaTheme(
    accentColor: Color? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    // Default Red: 0xFFE53935
    val storedColor = PrefsManager.getThemeColor(context)
    
    // Treat the Long as ARGB Int
    val primaryColor = accentColor ?: Color(storedColor.toInt())

    val colorScheme = createDarkScheme(primaryColor)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
