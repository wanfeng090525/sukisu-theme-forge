package dev.sukisu.themeforge.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF04231E),
    secondary = Color(0xFF4C625D),
    background = Color(0xFFF6FAF9),
    onBackground = Color(0xFF0E1F1B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0E1F1B),
    surfaceVariant = Color(0xFFE3EFEC),
    onSurfaceVariant = Color(0xFF445752),
    outline = Color(0xFFB7C9C4),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF04231E),
    primaryContainer = Color(0xFF115E59),
    onPrimaryContainer = Color(0xFFCFFCF4),
    secondary = Color(0xFFADC7C1),
    background = Color(0xFF060B12),
    onBackground = Color(0xFFF1FBF8),
    surface = Color(0xFF0D141C),
    onSurface = Color(0xFFF1FBF8),
    surfaceVariant = Color(0xFF1A2530),
    onSurfaceVariant = Color(0xFFB6C8C4),
    outline = Color(0xFF3A4A50),
    error = Color(0xFFFF8A80)
)

@Composable
fun ThemeForgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
