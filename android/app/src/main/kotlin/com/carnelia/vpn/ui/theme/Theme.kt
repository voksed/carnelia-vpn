package com.carnelia.vpn.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity
import com.carnelia.vpn.utils.PrefsManager
import com.carnelia.vpn.R
import androidx.compose.runtime.remember

enum class AppTheme(val displayNameResId: Int, val colorScheme: androidx.compose.material3.ColorScheme, val isDark: Boolean = true) {
    CARNELIA(R.string.theme_carnelia, darkColorScheme(
        primary = Color(0xFFE53935), // Красный (фирменный)
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFB71C1C),
        onPrimaryContainer = Color(0xFFFFCDD2),
        secondary = Color(0xFFD32F2F), // Темно-Красный
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFF8C0000),
        onSecondaryContainer = Color(0xFFFFDAD6),
        tertiary = Color(0xFFFFFFFF), // Белый
        onTertiary = Color(0xFF000000),
        tertiaryContainer = Color(0xFFCCCCCC),
        onTertiaryContainer = Color(0xFF000000),
        error = Color(0xFFFFB4AB),
        errorContainer = Color(0xFF93000A),
        onError = Color(0xFF690005),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF000000), // Черный фон
        onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFF101010), // Почти черный для карточек
        onSurface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFF1F1F1F),
        onSurfaceVariant = Color(0xFFE0E0E0),
        outline = Color(0xFF424242)
    )),
    CYBERPUNK(R.string.theme_cyberpunk, darkColorScheme(
        primary = Color(0xFF00FF88), // Яркий неоново-зеленый
        onPrimary = Color(0xFF000000),
        primaryContainer = Color(0xFF004422),
        onPrimaryContainer = Color(0xFF00FF88),
        secondary = Color(0xFFFF1493), // Глубокий розовый
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFF4D0019),
        onSecondaryContainer = Color(0xFFFF1493),
        tertiary = Color(0xFF00FFFF), // Электрик
        onTertiary = Color(0xFF000000),
        tertiaryContainer = Color(0xFF003D3D),
        onTertiaryContainer = Color(0xFF00FFFF),
        error = Color(0xFFFF0040),
        errorContainer = Color(0xFF990026),
        onError = Color(0xFFFFFFFF),
        onErrorContainer = Color(0xFFFF0040),
        background = Color(0xFF0D0D0D),
        onBackground = Color(0xFFE0E0E0),
        surface = Color(0xFF1A1A1A),
        onSurface = Color(0xFFF0F0F0),
        surfaceVariant = Color(0xFF2A2A2A),
        onSurfaceVariant = Color(0xFFB8B8B8),
        outline = Color(0xFF404040)
    )),
    MATRIX(R.string.theme_matrix, darkColorScheme(
        primary = Color(0xFF00FF41), // Яркий матричный зеленый
        onPrimary = Color(0xFF000000),
        primaryContainer = Color(0xFF003D1A),
        onPrimaryContainer = Color(0xFF00FF41),
        secondary = Color(0xFF39FF14), // Неоново-зеленый
        onSecondary = Color(0xFF000000),
        secondaryContainer = Color(0xFF0A4D0A),
        onSecondaryContainer = Color(0xFF39FF14),
        tertiary = Color(0xFF7FFF00), // Chartreuse
        onTertiary = Color(0xFF000000),
        tertiaryContainer = Color(0xFF2D4D00),
        onTertiaryContainer = Color(0xFF7FFF00),
        error = Color(0xFFFF4500),
        errorContainer = Color(0xFF990000),
        onError = Color(0xFFFFFFFF),
        onErrorContainer = Color(0xFFFF4500),
        background = Color(0xFF000000),
        onBackground = Color(0xFF00FF41),
        surface = Color(0xFF0A0A0A),
        onSurface = Color(0xFF00FF41),
        surfaceVariant = Color(0xFF1A1A1A),
        onSurfaceVariant = Color(0xFF66FF66),
        outline = Color(0xFF004400)
    )),
    PURPLE(R.string.theme_purple, darkColorScheme(
        primary = Color(0xFFBB86FC), // Фиолетовый
        onPrimary = Color(0xFF000000),
        primaryContainer = Color(0xFF4A148C),
        onPrimaryContainer = Color(0xFFBB86FC),
        secondary = Color(0xFF03DAC6), // Teal
        onSecondary = Color(0xFF000000),
        secondaryContainer = Color(0xFF004D40),
        onSecondaryContainer = Color(0xFF03DAC6),
        tertiary = Color(0xFFFF9800), // Оранжевый
        onTertiary = Color(0xFF000000),
        tertiaryContainer = Color(0xFF4D2C00),
        onTertiaryContainer = Color(0xFFFF9800),
        error = Color(0xFFCF6679),
        errorContainer = Color(0xFFB00020),
        onError = Color(0xFFFFFFFF),
        onErrorContainer = Color(0xFFCF6679),
        background = Color(0xFF121212),
        onBackground = Color(0xFFE0E0E0),
        surface = Color(0xFF1E1E1E),
        onSurface = Color(0xFFF0F0F0),
        surfaceVariant = Color(0xFF2C2C2C),
        onSurfaceVariant = Color(0xFFB8B8B8),
        outline = Color(0xFF424242)
    )),
    LIGHT_BLUE(R.string.theme_light_blue, lightColorScheme(
        primary = Color(0xFF1976D2),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD3E4FD),
        onPrimaryContainer = Color(0xFF001B3E),
        secondary = Color(0xFF42A5F5),
        onSecondary = Color(0xFF000000),
        secondaryContainer = Color(0xFFE3F2FD),
        onSecondaryContainer = Color(0xFF0D47A1),
        tertiary = Color(0xFF26C6DA),
        onTertiary = Color(0xFF000000),
        tertiaryContainer = Color(0xFFB2EBF2),
        onTertiaryContainer = Color(0xFF006064),
        error = Color(0xFFD32F2F),
        errorContainer = Color(0xFFFFCDD2),
        onError = Color(0xFFFFFFFF),
        onErrorContainer = Color(0xFFB71C1C),
        background = Color(0xFFFFFFFF),
        onBackground = Color(0xFF1A1A1A),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1A1A1A),
        surfaceVariant = Color(0xFFF5F5F5),
        onSurfaceVariant = Color(0xFF424242),
        outline = Color(0xFFBDBDBD)
    ), isDark = false),
    LIGHT_GREEN(R.string.theme_light_green, lightColorScheme(
        primary = Color(0xFF388E3C),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFC8E6C9),
        onPrimaryContainer = Color(0xFF1B5E20),
        secondary = Color(0xFF4CAF50),
        onSecondary = Color(0xFF000000),
        secondaryContainer = Color(0xFFE8F5E8),
        onSecondaryContainer = Color(0xFF2E7D32),
        tertiary = Color(0xFF8BC34A),
        onTertiary = Color(0xFF000000),
        tertiaryContainer = Color(0xFFF1F8E9),
        onTertiaryContainer = Color(0xFF558B2F),
        error = Color(0xFFD32F2F),
        errorContainer = Color(0xFFFFCDD2),
        onError = Color(0xFFFFFFFF),
        onErrorContainer = Color(0xFFB71C1C),
        background = Color(0xFFFFFFFF),
        onBackground = Color(0xFF1A1A1A),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1A1A1A),
        surfaceVariant = Color(0xFFF5F5F5),
        onSurfaceVariant = Color(0xFF424242),
        outline = Color(0xFFBDBDBD)
    ), isDark = false),
    LIGHT_PINK(R.string.theme_light_pink, lightColorScheme(
        primary = Color(0xFFC2185B),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFCE4EC),
        onPrimaryContainer = Color(0xFF880E4F),
        secondary = Color(0xFFE91E63),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFCE4EC),
        onSecondaryContainer = Color(0xFFAD1457),
        tertiary = Color(0xFF9C27B0),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFF3E5F5),
        onTertiaryContainer = Color(0xFF6A1B9A),
        error = Color(0xFFD32F2F),
        errorContainer = Color(0xFFFFCDD2),
        onError = Color(0xFFFFFFFF),
        onErrorContainer = Color(0xFFB71C1C),
        background = Color(0xFFFFFFFF),
        onBackground = Color(0xFF1A1A1A),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1A1A1A),
        surfaceVariant = Color(0xFFF5F5F5),
        onSurfaceVariant = Color(0xFF424242),
        outline = Color(0xFFBDBDBD)
    ), isDark = false),
    LIGHT_PURPLE(R.string.theme_light_purple, lightColorScheme(
        primary = Color(0xFF7B1FA2),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE1BEE7),
        onPrimaryContainer = Color(0xFF4A148C),
        secondary = Color(0xFF9C27B0),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFF3E5F5),
        onSecondaryContainer = Color(0xFF6A1B9A),
        tertiary = Color(0xFFBA68C8),
        onTertiary = Color(0xFF000000),
        tertiaryContainer = Color(0xFFF8E8F8),
        onTertiaryContainer = Color(0xFF7B1FA2),
        error = Color(0xFFD32F2F),
        errorContainer = Color(0xFFFFCDD2),
        onError = Color(0xFFFFFFFF),
        onErrorContainer = Color(0xFFB71C1C),
        background = Color(0xFFFFFFFF),
        onBackground = Color(0xFF1A1A1A),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1A1A1A),
        surfaceVariant = Color(0xFFF5F5F5),
        onSurfaceVariant = Color(0xFF424242),
        outline = Color(0xFFBDBDBD)
    ), isDark = false),
    LIGHT(R.string.theme_light, lightColorScheme(
        primary = Color(0xFF1976D2),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD3E4FD),
        onPrimaryContainer = Color(0xFF001B3E),
        secondary = Color(0xFF03DAC6),
        onSecondary = Color(0xFF000000),
        secondaryContainer = Color(0xFFB2DFDB),
        onSecondaryContainer = Color(0xFF001B3E),
        tertiary = Color(0xFFFF9800),
        onTertiary = Color(0xFF000000),
        tertiaryContainer = Color(0xFFFFE0B2),
        onTertiaryContainer = Color(0xFF4D2C00),
        error = Color(0xFFD32F2F),
        errorContainer = Color(0xFFFFCDD2),
        onError = Color(0xFFFFFFFF),
        onErrorContainer = Color(0xFFB71C1C),
        background = Color(0xFFFFFFFF),
        onBackground = Color(0xFF1A1A1A),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1A1A1A),
        surfaceVariant = Color(0xFFF5F5F5),
        onSurfaceVariant = Color(0xFF424242),
        outline = Color(0xFFBDBDBD)
    ), isDark = false),
    DARK(R.string.theme_dark, darkColorScheme(
        primary = Color(0xFF90CAF9),
        onPrimary = Color(0xFF0D47A1),
        primaryContainer = Color(0xFF1976D2),
        onPrimaryContainer = Color(0xFFE3F2FD),
        secondary = Color(0xFF81C784),
        onSecondary = Color(0xFF1B5E20),
        secondaryContainer = Color(0xFF4CAF50),
        onSecondaryContainer = Color(0xFFE8F5E8),
        tertiary = Color(0xFFFFB74D),
        onTertiary = Color(0xFFE65100),
        tertiaryContainer = Color(0xFFFF9800),
        onTertiaryContainer = Color(0xFFFFF3E0),
        error = Color(0xFFEF5350),
        errorContainer = Color(0xFFD32F2F),
        onError = Color(0xFFFFFFFF),
        onErrorContainer = Color(0xFFFFCDD2),
        background = Color(0xFF121212),
        onBackground = Color(0xFFE0E0E0),
        surface = Color(0xFF1E1E1E),
        onSurface = Color(0xFFF0F0F0),
        surfaceVariant = Color(0xFF2C2C2C),
        onSurfaceVariant = Color(0xFFB8B8B8),
        outline = Color(0xFF424242)
    )),
    TON(R.string.theme_ton, darkColorScheme(
        primary = Color(0xFF0088CC), // Toncoin Blue
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF003D5C),
        onPrimaryContainer = Color(0xFFD1E4FF),
        secondary = Color(0xFF0098EA), // Lighter Blue
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFF004F7A),
        onSecondaryContainer = Color(0xFFCFE5FF),
        tertiary = Color(0xFF2AABEE),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFF005682),
        onTertiaryContainer = Color(0xFFCDE6FF),
        error = Color(0xFFFFB4AB),
        errorContainer = Color(0xFF93000A),
        onError = Color(0xFF690005),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF1B2329), // Dark Navy Background
        onBackground = Color(0xFFE1E2E4),
        surface = Color(0xFF242F36), // Slightly lighter
        onSurface = Color(0xFFE1E2E4),
        surfaceVariant = Color(0xFF323F4B),
        onSurfaceVariant = Color(0xFFC0C7CD),
        outline = Color(0xFF4B5A66)
    )),
    SYSTEM(R.string.theme_system, darkColorScheme( // Default to dark, will be overridden
        primary = Color(0xFF9ECAFF),
        onPrimary = Color(0xFF003257),
        primaryContainer = Color(0xFF00497D),
        onPrimaryContainer = Color(0xFFD1E4FF),
        secondary = Color(0xFFBBC7DB),
        onSecondary = Color(0xFF253140),
        secondaryContainer = Color(0xFF3B4858),
        onSecondaryContainer = Color(0xFFD7E3F7),
        tertiary = Color(0xFFD7BEE4),
        onTertiary = Color(0xFF3B2946),
        tertiaryContainer = Color(0xFF523F5E),
        onTertiaryContainer = Color(0xFFF2DAFF),
        error = Color(0xFFFFB4AB),
        errorContainer = Color(0xFF93000A),
        onError = Color(0xFF690005),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF0F1419),
        onBackground = Color(0xFFE0E2E8),
        surface = Color(0xFF0F1419),
        onSurface = Color(0xFFE0E2E8),
        surfaceVariant = Color(0xFF41484D),
        onSurfaceVariant = Color(0xFFC1C7CE),
        outline = Color(0xFF424242)
    )),
    SECRET(R.string.theme_secret, darkColorScheme(
        primary = Color(0xFFFFFFFF),
        onPrimary = Color(0xFF000000),
        primaryContainer = Color(0xFF333333),
        onPrimaryContainer = Color(0xFFFFFFFF),
        secondary = Color(0xFFCCCCCC),
        onSecondary = Color(0xFF000000),
        secondaryContainer = Color(0xFF666666),
        onSecondaryContainer = Color(0xFFFFFFFF),
        tertiary = Color(0xFFAAAAAA),
        onTertiary = Color(0xFF000000),
        tertiaryContainer = Color(0xFF999999),
        onTertiaryContainer = Color(0xFF000000),
        error = Color(0xFFFF0000),
        errorContainer = Color(0xFF330000),
        onError = Color(0xFFFFFFFF),
        onErrorContainer = Color(0xFFFF0000),
        background = Color(0xFF000000),
        onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFF111111),
        onSurface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFF222222),
        onSurfaceVariant = Color(0xFFDDDDDD),
        outline = Color(0xFF444444)
    ))
}

@Composable
fun CarheliaTheme(
    themeIndex: Int? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isSecretUnlocked = remember { PrefsManager.isSecretThemeUnlocked(context) }
    val isSystemInDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    
    // If themeIndex is provided, use it. Otherwise read from Prefs.
    // However, if themeIndex is passed as a parameter, it might be outdated if we don't observe Prefs?
    // Actually, the caller (MainActivity/SettingsActivity) is responsible for observing and passing the correct index.
    
    val currentThemeIndex = themeIndex ?: PrefsManager.getThemeIndex(context)

    // Ensure we handle valid indices
    val theme = remember(currentThemeIndex, isSystemInDarkTheme) {
        when {
            isSecretUnlocked && currentThemeIndex == 12 -> AppTheme.SECRET.colorScheme
            currentThemeIndex == 11 -> if (isSystemInDarkTheme) AppTheme.DARK.colorScheme else AppTheme.LIGHT.colorScheme
            currentThemeIndex >= 0 && currentThemeIndex < AppTheme.values().size -> AppTheme.values()[currentThemeIndex].colorScheme
            else -> AppTheme.CARNELIA.colorScheme
        }
    }
    
    val isDark = remember(currentThemeIndex, isSystemInDarkTheme) {
        when {
            isSecretUnlocked && currentThemeIndex == 12 -> AppTheme.SECRET.isDark
            currentThemeIndex == 11 -> isSystemInDarkTheme
            currentThemeIndex >= 0 && currentThemeIndex < AppTheme.values().size -> AppTheme.values()[currentThemeIndex].isDark
            else -> AppTheme.CARNELIA.isDark
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = theme.background.toArgb()
            window.navigationBarColor = theme.background.toArgb()
            
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = theme,
        content = content
    )
}


