package com.carnelia.vpn.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Ультра мрачная палитра: Черный, Красный, Белый
private val DarkMrackColors = darkColorScheme(
    // Основные цвета - Красный/Черный
    primary = Color(0xFFE53935),           // Ярко-красный
    onPrimary = Color(0xFF000000),         // Черный текст на красном
    primaryContainer = Color(0xFF8B0000),  // Темно-красный контейнер
    onPrimaryContainer = Color(0xFFFFFFFF), // Белый текст в контейнере
    
    // Вторичный - Немного мягче
    secondary = Color(0xFFCC0000),         // Темно-красный
    onSecondary = Color(0xFFFFFFFF),       // Белый текст
    secondaryContainer = Color(0xFF330000), // Очень темно-красный
    onSecondaryContainer = Color(0xFFFF6B6B), // Светло-красный текст
    
    // Третичный - Белый для контраста
    tertiary = Color(0xFFFFFFFF),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFFE0E0E0),
    onTertiaryContainer = Color(0xFF000000),
    
    // Ошибки - Пульсирующий красный
    error = Color(0xFFFF1744),
    errorContainer = Color(0xFF5F0000),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFFFFFFFF),
    
    // Фоны - Пропасть черноты
    background = Color(0xFF0A0A0A),        // Почти черный
    onBackground = Color(0xFFFFFFFF),      // Белый текст
    surface = Color(0xFF1A1A1A),           // Немного светлее черного
    onSurface = Color(0xFFFFFFFF),         // Белый текст
    surfaceVariant = Color(0xFF2D2D2D),    // Серый-черный для разделения
    onSurfaceVariant = Color(0xFFFF6B6B),  // Светло-красный текст
)

@Composable
fun CarheliaTheme(
    useDarkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colors = DarkMrackColors

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
