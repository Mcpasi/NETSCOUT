package de.netscout.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF64E6B3), onPrimary = Color(0xFF063B29),
    primaryContainer = Color(0xFF173D31), onPrimaryContainer = Color(0xFFB7F9D9),
    background = Color(0xFF0C1218), onBackground = Color(0xFFE9F0F3),
    surface = Color(0xFF121C25), onSurface = Color(0xFFE9F0F3),
    surfaceVariant = Color(0xFF1B2935), onSurfaceVariant = Color(0xFFA7BAC6),
    outline = Color(0xFF384A56), error = Color(0xFFFFB4AB),
)
private val LightColors = lightColorScheme(
    primary = Color(0xFF146A4B), onPrimary = Color.White,
    primaryContainer = Color(0xFFD0F5E2), onPrimaryContainer = Color(0xFF073B29),
    background = Color(0xFFF3F7F6), surface = Color.White,
    surfaceVariant = Color(0xFFE7EFEB), onSurfaceVariant = Color(0xFF455D54),
)

@Composable
fun NetScoutTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
