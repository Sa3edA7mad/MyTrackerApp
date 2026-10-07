package com.example.mytrackerapp.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Three "glass gray" palettes. Contrast is enforced by PaletteContrastTest:
 * every text token >= 4.5:1 on canvas, surface and canvas+glow; category hues >= 4.5:1 on
 * surface (where chips and badges sit); onAccent >= 4.5:1 on accent.
 *
 * Accent is reserved for completion and primary action; category hues are always paired
 * with a text label — color alone never carries meaning.
 */
@Immutable
data class TrackerPalette(
    /** True for palettes with dark text — drives status-bar icon color and the M3 scheme. */
    val isLight: Boolean,
    val canvas: Color,        // app background
    val surface: Color,       // cards, sheets, bottom bars
    val surfaceHigh: Color,   // selected rows, badges, progress track
    val outline: Color,       // hairlines (the "glass edge")
    val outlineStrong: Color, // unchecked checkbox / switch border
    val accent: Color,        // done / primary CTA
    val accentPressed: Color,
    val accentMuted: Color,   // faint bar fills — NOT for text
    val onAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,  // smallest allowed text color
    /** MUST NEVER RENDER TEXT — borders/fills only. Use textTertiary for faint text. */
    val textDisabled: Color,
    val catBodyweight: Color,
    val catBand: Color,
    val catWarmUp: Color,
    val catStretch: Color,
    val danger: Color,
    val heatPartial: Color,   // heatmap fill only, never text
    val glowA: Color,         // ambient blob, top-right (translucent)
    val glowB: Color          // ambient blob, bottom-left (translucent)
)

val CharcoalPalette = TrackerPalette(
    isLight = false,
    canvas = Color(0xFF4A5052),
    surface = Color(0xFF545B5E),
    surfaceHigh = Color(0xFF5F676A),
    outline = Color(0xFF7A8386),
    outlineStrong = Color(0xFFA9B2B5),
    accent = Color(0xFFC8FF3D),
    accentPressed = Color(0xFFA8DC28),
    accentMuted = Color(0xFF627834),
    onAccent = Color(0xFF1A1E1F),
    textPrimary = Color(0xFFF7F9F8),
    textSecondary = Color(0xFFE3E8E6),
    textTertiary = Color(0xFFD2D9D6),
    textDisabled = Color(0xFF838C8F),
    catBodyweight = Color(0xFF8FF5DE),
    catBand = Color(0xFFFFCDB2),
    catWarmUp = Color(0xFFBED7FF),
    catStretch = Color(0xFFD9CCFF),
    danger = Color(0xFFFFCACA),
    heatPartial = Color(0xFF7D9A3A),
    glowA = Color(0x247E9A55),
    glowB = Color(0x245F7F92)
)

val SteelPalette = TrackerPalette(
    isLight = true,
    canvas = Color(0xFF8A9294),
    surface = Color(0xFFA3AAAC),
    surfaceHigh = Color(0xFFB6BCBE),
    outline = Color(0xFFC9CED0),
    outlineStrong = Color(0xFF3A4143),
    accent = Color(0xFF16190F),
    accentPressed = Color(0xFF2A2F1E),
    accentMuted = Color(0xFF6F7A5E),
    onAccent = Color(0xFFC8FF3D),
    textPrimary = Color(0xFF0B0D0E),
    textSecondary = Color(0xFF191D1E),
    textTertiary = Color(0xFF23282A),
    textDisabled = Color(0xFF6C7476),
    catBodyweight = Color(0xFF063F33),
    catBand = Color(0xFF5A2205),
    catWarmUp = Color(0xFF0F2F66),
    catStretch = Color(0xFF371F7A),
    danger = Color(0xFF4A0505),
    heatPartial = Color(0xFF5A6644),
    glowA = Color(0x73D5E0E4),
    glowB = Color(0x4DB7C79A)
)

val FrostPalette = TrackerPalette(
    isLight = true,
    canvas = Color(0xFFD6DADB),
    surface = Color(0xFFEBEDEE),
    surfaceHigh = Color(0xFFD0D6D8),
    outline = Color(0xFFC3C9CB),
    outlineStrong = Color(0xFF6B7477),
    accent = Color(0xFF356000),
    accentPressed = Color(0xFF2A4C00),
    accentMuted = Color(0xFFB9CC94),
    onAccent = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF101314),
    textSecondary = Color(0xFF3A4143),
    textTertiary = Color(0xFF4E5659),
    textDisabled = Color(0xFFA9B0B2),
    catBodyweight = Color(0xFF0A6452),
    catBand = Color(0xFF943F0D),
    catWarmUp = Color(0xFF1F57B3),
    catStretch = Color(0xFF5B3FB8),
    danger = Color(0xFFA8231B),
    heatPartial = Color(0xFF9DBB6A),
    glowA = Color(0x73C8E29A),
    glowB = Color(0x80C9DDEA)
)

val LocalTrackerPalette = staticCompositionLocalOf { CharcoalPalette }

// Legacy names, kept so no screen import changes. Each reads the active palette.
val Canvas: Color @Composable @ReadOnlyComposable get() = LocalTrackerPalette.current.canvas
val Surface: Color @Composable @ReadOnlyComposable get() = LocalTrackerPalette.current.surface
val SurfaceHigh: Color @Composable @ReadOnlyComposable get() = LocalTrackerPalette.current.surfaceHigh
val Outline: Color @Composable @ReadOnlyComposable get() = LocalTrackerPalette.current.outline
val OutlineStrong: Color @Composable @ReadOnlyComposable get() = LocalTrackerPalette.current.outlineStrong
val Accent: Color @Composable @ReadOnlyComposable get() = LocalTrackerPalette.current.accent
val AccentPressed: Color @Composable @ReadOnlyComposable get() = LocalTrackerPalette.current.accentPressed
val AccentMuted: Color @Composable @ReadOnlyComposable get() = LocalTrackerPalette.current.accentMuted
val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalTrackerPalette.current.onAccent
val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalTrackerPalette.current.textPrimary
val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalTrackerPalette.current.textSecondary
val TextTertiary: Color @Composable @ReadOnlyComposable get() = LocalTrackerPalette.current.textTertiary
val TextDisabled: Color @Composable @ReadOnlyComposable get() = LocalTrackerPalette.current.textDisabled
val CatBodyweight: Color @Composable @ReadOnlyComposable get() = LocalTrackerPalette.current.catBodyweight
val CatBand: Color @Composable @ReadOnlyComposable get() = LocalTrackerPalette.current.catBand
val CatWarmUp: Color @Composable @ReadOnlyComposable get() = LocalTrackerPalette.current.catWarmUp
val CatStretch: Color @Composable @ReadOnlyComposable get() = LocalTrackerPalette.current.catStretch
val Danger: Color @Composable @ReadOnlyComposable get() = LocalTrackerPalette.current.danger
val HeatPartial: Color @Composable @ReadOnlyComposable get() = LocalTrackerPalette.current.heatPartial
