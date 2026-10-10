package org.fungalsentinel.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────
// Acid-punk palette extracted from the design team's concept art
// (black base + magenta / neon-green / lemon-yellow / cream / cyan)
// ─────────────────────────────────────────────────────────────
val NeonBlack = Color(0xFF050505)
val NeonMagenta = Color(0xFFE4007F)
val NeonMagentaDark = Color(0xFF7A0044)
val NeonGreen = Color(0xFF3EF92A)
val NeonGreenDark = Color(0xFF124D0B)
val NeonYellow = Color(0xFFEDE928)
val NeonYellowDark = Color(0xFF5C5A00)
val NeonCream = Color(0xFFF5F5C0)
val NeonCyan = Color(0xFF2BE8E8)
val NeonGray = Color(0xFFC7CED6)
val NeonSurfaceVariant = Color(0xFF2A0018)   // dark magenta tint for cards
val NeonError = Color(0xFFFF6B6B)
val NeonErrorDark = Color(0xFF641111)

/**
 * Single fixed scheme for ALL system themes: the concept art is black-based,
 * and neon-on-white would collapse contrast. Dynamic color is disabled so the
 * brand palette survives OEM wallpapers.
 */
private val NeonScheme = darkColorScheme(
    primary = NeonMagenta,
    onPrimary = Color.Black,
    primaryContainer = NeonMagentaDark,
    onPrimaryContainer = NeonCream,
    secondary = NeonGreen,
    onSecondary = Color.Black,
    secondaryContainer = NeonGreenDark,
    onSecondaryContainer = NeonCream,
    tertiary = NeonYellow,
    onTertiary = Color.Black,
    tertiaryContainer = NeonYellowDark,
    onTertiaryContainer = NeonCream,
    background = Color.Black,
    onBackground = NeonCream,
    surface = NeonBlack,
    onSurface = NeonCream,
    surfaceVariant = NeonSurfaceVariant,
    onSurfaceVariant = NeonCream,
    surfaceTint = NeonMagenta,
    outline = NeonYellow,
    outlineVariant = NeonYellowDark,
    error = NeonError,
    onError = Color.Black,
    errorContainer = NeonErrorDark,
    onErrorContainer = NeonCream,
    inverseSurface = NeonCream,
    inverseOnSurface = Color.Black,
    inversePrimary = NeonMagentaDark,
    scrim = Color.Black
)

/**
 * Default type scale for now. The design team is still revising brand fonts;
 * keeping the platform default guarantees full glyph coverage for all four
 * languages (En / 中文 / Español / Chichewa). Swap here later when fonts land.
 */
val Typography = Typography()

@Composable
fun FssaTheme(
    // Kept for call-site compatibility; the concept defines ONE look,
    // so both parameters are intentionally ignored.
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NeonScheme,
        typography = Typography,
        content = content
    )
}