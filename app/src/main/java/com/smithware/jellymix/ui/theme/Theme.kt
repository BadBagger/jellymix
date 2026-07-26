package com.smithware.jellymix.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Night = Color(0xFF101113)
private val Ink = Color(0xFF171A21)
private val Paper = Color(0xFFF7F3E8)
private val Cloud = Color(0xFFE8EDF2)
private val JellyAccent = Color(0xFF1DE9B6)
private val JellySelected = Color(0x1F1DE9B6)
private val JellyError = Color(0xFFFF5A64)

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
    Jelly("Jelly", JellyAccent, Color(0xFF6F7885), Color(0xFF44546A)),
    Ember("Ember", Color(0xFFFF7A1A), Color(0xFF6F7885), Color(0xFF5F5A52)),
    Ocean("Ocean", Color(0xFF1D8FE1), Color(0xFF6F7885), Color(0xFF3E5872)),
    Grape("Grape", Color(0xFF8B5CF6), Color(0xFF6F7885), Color(0xFF57427E)),
    Mono("Mono", Color(0xFF2E3440), Color(0xFF6F7885), Color(0xFF5E6777))
}

private fun lightColors(accentTheme: AccentTheme) = lightColorScheme(
    primary = JellyAccent,
    onPrimary = Night,
    secondary = JellyAccent,
    onSecondary = Night,
    tertiary = accentTheme.tertiary,
    error = JellyError,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Cloud,
    primaryContainer = JellySelected,
    onPrimaryContainer = Ink,
    onSurfaceVariant = Color(0xFF2F3742),
    outline = Color(0xFF7B8493)
)

private fun darkColors(accentTheme: AccentTheme) = darkColorScheme(
    primary = JellyAccent,
    onPrimary = Night,
    secondary = JellyAccent,
    onSecondary = Night,
    tertiary = accentTheme.tertiary,
    error = JellyError,
    background = Night,
    onBackground = Paper,
    surface = Color(0xFF181B22),
    onSurface = Paper,
    surfaceVariant = Color(0xFF252A33),
    primaryContainer = JellySelected,
    onPrimaryContainer = Paper,
    onSurfaceVariant = Color(0xFFE0E5ED),
    outline = Color(0xFF88919F)
)

private val JellyMixTypography = Typography(
    displayLarge = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    displaySmall = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelSmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
)

@Composable
fun JellyMixTheme(
    themeMode: ThemeMode = ThemeMode.Dark,
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
        typography = JellyMixTypography,
        content = content
    )
}
