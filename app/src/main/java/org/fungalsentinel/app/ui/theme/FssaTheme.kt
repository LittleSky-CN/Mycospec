package org.fungalsentinel.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// compatibility alias: legacy code that still references NeonXxx falls into the new palette automatically; new code must use AppColors.
val NeonBlack = AppColors.Bg
val NeonMagenta = AppColors.Accent
val NeonMagentaDark = AppColors.SurfaceRaised
val NeonGreen = AppColors.Accent
val NeonGreenDark = AppColors.AccentDim
val NeonYellow = AppColors.Accent
val NeonYellowDark = AppColors.AccentDim
val NeonCream = AppColors.TextPrimary
val NeonCyan = AppColors.AccentSoft
val NeonGray = AppColors.TextSecondary
val NeonSurfaceVariant = AppColors.Surface
val NeonError = AppColors.Error
val NeonErrorDark = AppColors.SurfaceRaised

/** single scheme: primary / secondary / tertiary are all unified into the accent green,
 *  so even a missed colorScheme reference lands in the minimal palette. */
private val MinimalScheme = darkColorScheme(
    primary = AppColors.Accent,
    onPrimary = AppColors.OnAccent,
    primaryContainer = AppColors.SurfaceRaised,
    onPrimaryContainer = AppColors.TextPrimary,
    secondary = AppColors.Accent,
    onSecondary = AppColors.OnAccent,
    secondaryContainer = AppColors.SurfaceRaised,
    onSecondaryContainer = AppColors.TextPrimary,
    tertiary = AppColors.Accent,
    onTertiary = AppColors.OnAccent,
    tertiaryContainer = AppColors.SurfaceRaised,
    onTertiaryContainer = AppColors.TextPrimary,
    background = AppColors.Bg,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.Bg,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.Surface,
    onSurfaceVariant = AppColors.TextSecondary,
    surfaceTint = AppColors.Accent,
    outline = AppColors.Outline,
    outlineVariant = AppColors.Outline,
    error = AppColors.Error,
    onError = AppColors.OnAccent,
    errorContainer = AppColors.SurfaceRaised,
    onErrorContainer = AppColors.Error,
    inverseSurface = AppColors.TextPrimary,
    inverseOnSurface = AppColors.Bg,
    inversePrimary = AppColors.AccentDim,
    scrim = AppColors.Bg
)

val Typography = Typography()

@Composable
fun FssaTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(colorScheme = MinimalScheme, typography = Typography, content = content)
}