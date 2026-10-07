package com.example.mytrackerapp.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.example.mytrackerapp.data.prefs.ThemeMode
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteContrastTest {

    private fun ratio(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05; val lb = b.luminance() + 0.05
        return (maxOf(la, lb) / minOf(la, lb)).toDouble()
    }

    private val palettes = mapOf("charcoal" to CharcoalPalette, "steel" to SteelPalette, "frost" to FrostPalette)

    @Test
    fun textMeetsAaOnEveryBackground() = palettes.forEach { (name, p) ->
        val backgrounds = listOf(p.canvas, p.surface, p.glowA.compositeOver(p.canvas), p.glowB.compositeOver(p.canvas))
        listOf(p.textPrimary, p.textSecondary, p.textTertiary, p.accent, p.danger).forEach { fg ->
            backgrounds.forEach { bg ->
                assertTrue("$name $fg on $bg = ${ratio(fg, bg)}", ratio(fg, bg) >= 4.5)
            }
        }
    }

    @Test
    fun categoryHuesMeetAaOnSurface() = palettes.forEach { (name, p) ->
        listOf(p.catBodyweight, p.catBand, p.catWarmUp, p.catStretch).forEach { c ->
            assertTrue("$name $c = ${ratio(c, p.surface)}", ratio(c, p.surface) >= 4.5)
        }
    }

    @Test
    fun onAccentMeetsAa() = palettes.forEach { (name, p) ->
        assertTrue(name, ratio(p.onAccent, p.accent) >= 4.5)
    }

    @Test
    fun matchDeviceMapsDarkToCharcoalAndLightToFrost() {
        assertSame(CharcoalPalette, paletteFor(ThemeMode.SYSTEM, systemDark = true))
        assertSame(FrostPalette, paletteFor(ThemeMode.SYSTEM, systemDark = false))
        assertSame(SteelPalette, paletteFor(ThemeMode.STEEL, systemDark = true))
        assertSame(SteelPalette, paletteFor(ThemeMode.STEEL, systemDark = false))
    }
}
