package org.fungalsentinel.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Single source of truth for colors.
 * Design constraint: at most 2 hues per workflow screen at the same time --
 *   1) Accent green (Accent family: includes Dim/Soft variants, covers interaction/success/processing)
 *   2) Semantic red (Error, only for FAILED/BLOCKED/warning)
 * Everything else is a neutral gray scale. The theme and all components may only reference this object.
 */
object AppColors {
    // ── Neutral gray scale ──
    val Bg            = Color(0xFF121212)   // Page background
    val Surface       = Color(0xFF1E1E1E)   // Card / panel / dropdown
    val SurfaceRaised = Color(0xFF2A2A2A)   // Raised surface / shadow replacement / disabled base
    val Outline       = Color(0xFF3A3A3A)   // Stroke / divider / secondary border
    val TextPrimary   = Color(0xFFE0E0E0)   // Primary text
    val TextSecondary = Color(0xFF9E9E9E)   // Secondary text / idle state
    val OnAccent      = Color(0xFF14241A)   // Dark text on accent surfaces

    // ── Single accent hue (green) ──
    val Accent     = Color(0xFF81C784)
    val AccentDim  = Color(0xFF5F8F61)                 // Pressed / secondary
    val AccentSoft = Color(0xFF81C784).copy(alpha = 0.55f) // Processing / secondary highlight

    // ── Single semantic hue (red, exceptions only) ──
    val Error = Color(0xFFE57373)

    // ── Data visualization only (channel semantic encoding, not part of the UI palette) ──
    object Chart {
        val R   = Color(0xFFE57373)
        val G   = Color(0xFF81C784)
        val B   = Color(0xFF90CAF9)
        val Fit = Color(0xFFB0BEC5)
    }
}

/** App-wide unified status token → text color mapping, removing duplicated when blocks and hardcoded colors across screens. */
fun statusTextColor(token: String): Color = when (token) {
    "FAILED", "BLOCKED"   -> AppColors.Error
    "COMPUTED", "PRESET"  -> AppColors.Accent
    "PROCESSING"          -> AppColors.AccentSoft
    else                  -> AppColors.TextSecondary
}