package com.example.mytrackerapp.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.example.mytrackerapp.data.prefs.ThemeMode

/** Match device: dark → Light charcoal, light → Pale frost. Steel glass is manual-only. */
fun paletteFor(mode: ThemeMode, systemDark: Boolean): TrackerPalette = when (mode) {
    ThemeMode.SYSTEM -> if (systemDark) CharcoalPalette else FrostPalette
    ThemeMode.CHARCOAL -> CharcoalPalette
    ThemeMode.STEEL -> SteelPalette
    ThemeMode.FROST -> FrostPalette
}

private fun TrackerPalette.toColorScheme(): ColorScheme {
    val base = if (isLight) lightColorScheme() else darkColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentMuted,
        onPrimaryContainer = if (isLight) textPrimary else accent,
        secondary = catBodyweight,
        onSecondary = canvas,
        tertiary = catBand,
        onTertiary = canvas,
        background = canvas,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceHigh,
        onSurfaceVariant = textSecondary,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceHigh,
        outline = outline,
        outlineVariant = outlineStrong,
        error = danger,
        onError = canvas,
        scrim = Color(0x99000000)
    )
}

/**
 * No dynamic (wallpaper) color: the palettes' contrast ratios are hand-checked and a
 * wallpaper-derived scheme would invalidate them. Previews get Light charcoal by default.
 */
@Composable
fun MyTrackerAppTheme(
    palette: TrackerPalette = CharcoalPalette,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalTrackerPalette provides palette) {
        MaterialTheme(
            colorScheme = palette.toColorScheme(),
            typography = Typography,
            content = content
        )
    }
}
