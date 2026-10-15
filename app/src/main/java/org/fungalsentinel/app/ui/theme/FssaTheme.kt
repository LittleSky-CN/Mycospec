package org.fungalsentinel.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// 兼容别名：旧代码若仍引用 NeonXxx，自动落入新色板；新代码一律用 AppColors。
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

/** 单一 scheme：primary/secondary/tertiary 统一为强调绿，
 *  即使有漏改的 colorScheme 引用也会自动落入极简色板。 */
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