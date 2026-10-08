package com.dockeep.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.dockeep.app.domain.ThemeMode

private val LightColors = lightColorScheme(
    primary = TealDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4EFEA),
    onPrimaryContainer = Color(0xFF062E2A),
    secondary = Gold,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E6C8),
    onSecondaryContainer = Color(0xFF3D2E08),
    tertiary = Color(0xFF3D5A80),
    background = Cream,
    onBackground = Ink,
    surface = CreamSurface,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE7E2DA),
    onSurfaceVariant = Color(0xFF4F534F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF3F0EA),
    surfaceContainer = Color(0xFFEEEAE3),
    surfaceContainerHigh = Color(0xFFE8E4DD),
    error = Danger,
    onError = Color.White,
    outline = Color(0xFFC9C3B8)
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF003732),
    primaryContainer = Color(0xFF0E6B62),
    onPrimaryContainer = Color(0xFFD4EFEA),
    secondary = Color(0xFFE2C57A),
    onSecondary = Color(0xFF3D2E08),
    secondaryContainer = Color(0xFF5C4710),
    onSecondaryContainer = Color(0xFFF8E7C2),
    tertiary = Color(0xFF9BB4D4),
    background = DarkBackground,
    onBackground = DarkOn,
    surface = DarkSurface,
    onSurface = DarkOn,
    surfaceVariant = Color(0xFF2A3331),
    onSurfaceVariant = Color(0xFFC5CBC8),
    surfaceContainerLowest = Color(0xFF0A0D0C),
    surfaceContainerLow = Color(0xFF141A19),
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = Color(0xFF27302E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    outline = Color(0xFF3E4745)
)

@Composable
fun DocKeepTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = DocKeepTypography,
        content = content
    )
}
