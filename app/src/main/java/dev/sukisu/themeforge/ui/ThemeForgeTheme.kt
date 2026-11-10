package dev.sukisu.themeforge.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF0B1B3F),
    secondary = Color(0xFF5A6473),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4E8F0),
    onSecondaryContainer = Color(0xFF161C26),
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF0F1319),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F1319),
    surfaceVariant = Color(0xFFEDF0F5),
    onSurfaceVariant = Color(0xFF5B6472),
    surfaceContainer = Color(0xFFF1F3F7),
    surfaceContainerHigh = Color(0xFFE9ECF2),
    surfaceContainerLow = Color(0xFFFAFBFC),
    outline = Color(0xFFD5DBE4),
    outlineVariant = Color(0xFFE5E9F0),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8BAAFF),
    onPrimary = Color(0xFF0A1B3D),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFFA7B1C2),
    onSecondary = Color(0xFF101720),
    secondaryContainer = Color(0xFF232A36),
    onSecondaryContainer = Color(0xFFDCE3EE),
    background = Color(0xFF080A0E),
    onBackground = Color(0xFFECEFF4),
    surface = Color(0xFF101319),
    onSurface = Color(0xFFECEFF4),
    surfaceVariant = Color(0xFF1A1F28),
    onSurfaceVariant = Color(0xFFA6AFBD),
    surfaceContainer = Color(0xFF151922),
    surfaceContainerHigh = Color(0xFF1B2029),
    surfaceContainerLow = Color(0xFF0E1116),
    outline = Color(0xFF2C3340),
    outlineVariant = Color(0xFF232933),
    error = Color(0xFFFF8A80)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ThemeForgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = AppShapes,
        content = content
    )
}
