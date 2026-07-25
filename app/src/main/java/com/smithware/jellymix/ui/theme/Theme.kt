package com.smithware.jellymix.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Night = Color(0xFF101113)
private val Ink = Color(0xFF171A21)
private val Paper = Color(0xFFF7F3E8)
private val Cloud = Color(0xFFE8EDF2)

enum class ThemeMode(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark")
}

enum class AccentTheme(
    val label: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color
) {
    Jelly("Jelly", Color(0xFF00D9B5), Color(0xFFFF6B6B), Color(0xFF44546A)),
    Ember("Ember", Color(0xFFFF7A1A), Color(0xFF0AA39A), Color(0xFF5F5A52)),
    Ocean("Ocean", Color(0xFF1D8FE1), Color(0xFF24C6A5), Color(0xFF3E5872)),
    Grape("Grape", Color(0xFF8B5CF6), Color(0xFFFFB703), Color(0xFF57427E)),
    Mono("Mono", Color(0xFF2E3440), Color(0xFF88C0D0), Color(0xFF5E6777))
}

private fun lightColors(accentTheme: AccentTheme) = lightColorScheme(
    primary = accentTheme.primary,
    onPrimary = Night,
    secondary = accentTheme.secondary,
    onSecondary = Night,
    tertiary = accentTheme.tertiary,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Cloud,
    onSurfaceVariant = Color(0xFF3D4552),
    outline = Color(0xFF7B8493)
)

private fun darkColors(accentTheme: AccentTheme) = darkColorScheme(
    primary = accentTheme.primary,
    onPrimary = Night,
    secondary = accentTheme.secondary,
    onSecondary = Night,
    tertiary = accentTheme.tertiary,
    background = Night,
    onBackground = Paper,
    surface = Color(0xFF181B22),
    onSurface = Paper,
    surfaceVariant = Color(0xFF252A33),
    onSurfaceVariant = Color(0xFFD2D8E2),
    outline = Color(0xFF88919F)
)

@Composable
fun JellyMixTheme(
    themeMode: ThemeMode = ThemeMode.System,
    accentTheme: AccentTheme = AccentTheme.Jelly,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    MaterialTheme(
        colorScheme = if (darkTheme) darkColors(accentTheme) else lightColors(accentTheme),
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
