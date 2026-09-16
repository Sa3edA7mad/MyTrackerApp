package com.example.mytrackerapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TrackerColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = AccentMuted,
    onPrimaryContainer = Accent,
    secondary = CatBodyweight,
    onSecondary = Canvas,
    tertiary = CatBand,
    onTertiary = Canvas,
    background = Canvas,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceHigh,
    outline = Outline,
    outlineVariant = OutlineStrong,
    error = Danger,
    onError = Canvas,
    scrim = Canvas
)

/**
 * Dark-only by design.
 *
 * There is deliberately no `darkTheme` parameter and no dynamic color: the palette's
 * contrast ratios were computed against [Canvas], and honouring a light system theme
 * or a wallpaper-derived scheme would invalidate all of them.
 */
@Composable
fun MyTrackerAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TrackerColorScheme,
        typography = Typography,
        content = content
    )
}
