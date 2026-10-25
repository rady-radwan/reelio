package com.rady.reelio.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class ThemeSpec(
    val key: String,
    val name: String,
    val dark: Boolean,
    val primary: Color,
    val onPrimary: Color,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val onSurface: Color,
    val muted: Color,
    val accent: Color,
)

val Themes = listOf(
    ThemeSpec("dark", "Dark", true, Color(0xFFA78BFA), Color(0xFF1A0B3D), Color(0xFF000000), Color(0xFF101014), Color(0xFF1C1C22), Color(0xFFF2F1F6), Color(0xFF9C9AA8), Color(0xFFFF6B8A)),
    ThemeSpec("light", "Light", false, Color(0xFF6D28D9), Color(0xFFFFFFFF), Color(0xFFF7F6FB), Color(0xFFFFFFFF), Color(0xFFECEAF4), Color(0xFF1B1A22), Color(0xFF6B6878), Color(0xFFE11D63)),
    ThemeSpec("blue", "Blue", true, Color(0xFF5AB0FF), Color(0xFF002544), Color(0xFF06111F), Color(0xFF0C1B30), Color(0xFF142A47), Color(0xFFE6F0FB), Color(0xFF8DA6C2), Color(0xFF7DE3FF)),
    ThemeSpec("green", "Green", true, Color(0xFF3DDC97), Color(0xFF002A1A), Color(0xFF051510), Color(0xFF0B2219), Color(0xFF133326), Color(0xFFE5F6EE), Color(0xFF8BB3A1), Color(0xFFB8F35A)),
    ThemeSpec("grey", "Grey", true, Color(0xFFD0D3D9), Color(0xFF1E2024), Color(0xFF16171A), Color(0xFF212327), Color(0xFF2D3035), Color(0xFFECEDEF), Color(0xFF9EA2A9), Color(0xFF9EC5FF)),
    ThemeSpec("yellow", "Yellow", true, Color(0xFFFFC83D), Color(0xFF2B1F00), Color(0xFF14110A), Color(0xFF221D10), Color(0xFF332B17), Color(0xFFFBF4E2), Color(0xFFB8AB8A), Color(0xFFFF8A3D)),
    ThemeSpec("red", "Red", true, Color(0xFFFF5A67), Color(0xFF3A0008), Color(0xFF16080A), Color(0xFF261013), Color(0xFF3A1A1E), Color(0xFFFBEAEC), Color(0xFFC09A9E), Color(0xFFFFB35A)),
)

fun themeFor(key: String, systemDark: Boolean): ThemeSpec = when (key) {
    "system" -> if (systemDark) Themes[0] else Themes[1]
    else -> Themes.firstOrNull { it.key == key } ?: Themes[0]
}

val LocalThemeSpec = staticCompositionLocalOf { Themes[0] }

private fun ThemeSpec.scheme(): ColorScheme =
    if (dark) darkColorScheme(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = primary.copy(alpha = 0.22f).compositeOver(surface), onPrimaryContainer = onSurface,
        secondary = accent, onSecondary = Color.Black,
        secondaryContainer = surfaceHigh, onSecondaryContainer = onSurface,
        tertiary = accent, onTertiary = Color.Black,
        background = background, onBackground = onSurface,
        surface = background, onSurface = onSurface,
        surfaceVariant = surfaceHigh, onSurfaceVariant = muted,
        surfaceContainerLowest = background, surfaceContainerLow = surface,
        surfaceContainer = surface, surfaceContainerHigh = surfaceHigh,
        surfaceContainerHighest = surfaceHigh.lighten(0.04f),
        surfaceBright = surfaceHigh, surfaceDim = background,
        outline = muted.copy(alpha = 0.6f), outlineVariant = surfaceHigh.lighten(0.06f),
        inverseSurface = onSurface, inverseOnSurface = background,
    ) else lightColorScheme(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = primary.copy(alpha = 0.14f).compositeOver(surface), onPrimaryContainer = onSurface,
        secondary = accent, onSecondary = Color.White,
        secondaryContainer = surfaceHigh, onSecondaryContainer = onSurface,
        tertiary = accent,
        background = background, onBackground = onSurface,
        surface = background, onSurface = onSurface,
        surfaceVariant = surfaceHigh, onSurfaceVariant = muted,
        surfaceContainerLowest = Color.White, surfaceContainerLow = surface,
        surfaceContainer = surface, surfaceContainerHigh = surfaceHigh,
        surfaceContainerHighest = surfaceHigh.darken(0.04f),
        outline = muted.copy(alpha = 0.6f), outlineVariant = surfaceHigh.darken(0.06f),
    )

private fun Color.compositeOver(bg: Color): Color {
    val a = alpha
    return Color(red * a + bg.red * (1 - a), green * a + bg.green * (1 - a), blue * a + bg.blue * (1 - a), 1f)
}

private fun Color.lighten(f: Float) = Color((red + f).coerceAtMost(1f), (green + f).coerceAtMost(1f), (blue + f).coerceAtMost(1f), alpha)
private fun Color.darken(f: Float) = Color((red - f).coerceAtLeast(0f), (green - f).coerceAtLeast(0f), (blue - f).coerceAtLeast(0f), alpha)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun ReelioTheme(themeKey: String, content: @Composable () -> Unit) {
    val spec = themeFor(themeKey, isSystemInDarkTheme())
    androidx.compose.runtime.CompositionLocalProvider(LocalThemeSpec provides spec) {
        MaterialTheme(colorScheme = spec.scheme(), typography = AppTypography, content = content)
    }
}

val subtleText: TextStyle
    @Composable get() = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
