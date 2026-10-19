package org.fungalsentinel.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Single source of truth for colours.
 * Design constraint: any workflow screen uses at most 2 hues at a time --
 *   1) accent green (the Accent family, including Dim / Soft variants, covering interaction / success / processing)
 *   2) semantic red (Error, only for FAILED / BLOCKED / warnings)
 * Everything else is neutral grey. The theme and every component may only reference this object.
 */
object AppColors {
    // ── neutral greys ──
    val Bg            = Color(0xFF121212)   // page background
    val Surface       = Color(0xFF1E1E1E)   // cards / panels / dropdowns
    val SurfaceRaised = Color(0xFF2A2A2A)   // raised surface / shadow substitute / disabled background
    val Outline       = Color(0xFF3A3A3A)   // outline / divider / secondary border
    val TextPrimary   = Color(0xFFE0E0E0)   // primary text
    val TextSecondary = Color(0xFF9E9E9E)   // secondary text / idle state
    val OnAccent      = Color(0xFF14241A)   // dark text on accent surfaces

    // ── the single accent hue (green) ──
    val Accent     = Color(0xFF81C784)
    val AccentDim  = Color(0xFF5F8F61)                 // pressed / secondary
    val AccentSoft = Color(0xFF81C784).copy(alpha = 0.55f) // processing / secondary highlight

    // ── the single semantic hue (red, exceptions only) ──
    val Error = Color(0xFFE57373)

    // ── data visualisation only (channel semantic encoding, not part of the UI palette) ──
    object Chart {
        val R   = Color(0xFFE57373)
        val G   = Color(0xFF81C784)
        val B   = Color(0xFF90CAF9)
        val Fit = Color(0xFFB0BEC5)
    }
}

/** App-wide status token -> text colour mapping, removing the duplicated when blocks and hardcoded colours in every screen. */
fun statusTextColor(token: String): Color = when (token) {
    "FAILED", "BLOCKED"   -> AppColors.Error
    "COMPUTED", "PRESET"  -> AppColors.Accent
    "PROCESSING"          -> AppColors.AccentSoft
    else                  -> AppColors.TextSecondary
}