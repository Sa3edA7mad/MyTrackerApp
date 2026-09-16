package com.example.mytrackerapp.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * "Dark Athletic" palette.
 *
 * Contrast ratios were computed with the WCAG 2.x relative-luminance formula.
 * Lime [Accent] is reserved exclusively for completion and primary action so the
 * category hues never compete with it, and every category color is always paired
 * with a text label — color alone never carries meaning.
 */

val Canvas = Color(0xFF0B0D0C)        // app background
val Surface = Color(0xFF16191A)       // cards, sheets
val SurfaceHigh = Color(0xFF212526)   // selected rows, badges, progress track
val Outline = Color(0xFF2E3435)       // hairlines
val OutlineStrong = Color(0xFF414A4B) // unchecked checkbox border

val Accent = Color(0xFFC8FF3D)        // done / primary CTA    16.6:1 on Canvas
val AccentPressed = Color(0xFFA8DC28)
val AccentMuted = Color(0xFF3A4A14)   // faint bar fills — NOT for text
val OnAccent = Color(0xFF0B0D0C)      // 16.6:1 on Accent

val TextPrimary = Color(0xFFF2F5F3)   // 17.8:1 on Canvas
val TextSecondary = Color(0xFFA2ABA8) //  8.3:1
val TextTertiary = Color(0xFF7C8784)  //  5.3:1 — smallest allowed text color

/**
 * 2.5:1 — fails AA body (4.5:1) AND AA large (3:1).
 *
 * MUST NEVER RENDER TEXT. Permitted only for non-informational fills and borders,
 * e.g. a disabled checkbox outline. If you need faint text, use [TextTertiary].
 */
val TextDisabled = Color(0xFF4A5452)

// Category hues, measured on Surface (#16191A) where chips and badges actually sit.
val CatBodyweight = Color(0xFF5BE1C4) // 11.0:1
val CatBand = Color(0xFFFF8A4C)       //  7.6:1
val CatWarmUp = Color(0xFF6EA8FF)     //  7.3:1
val CatStretch = Color(0xFFA88BFF)    //  6.6:1

val Danger = Color(0xFFFF5C5C)        //  6.4:1 on Canvas
val HeatPartial = Color(0xFF5C7A1C)   // heatmap fill only, never text
