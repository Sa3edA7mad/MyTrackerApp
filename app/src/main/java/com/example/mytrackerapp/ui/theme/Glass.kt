package com.example.mytrackerapp.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Full-screen background: opaque canvas plus two soft glow blobs. That gives the "glass" depth
 * without real blur. Opaque, so it still hides the outgoing screen during navigation.
 */
@Composable
fun Modifier.glassBackdrop(): Modifier {
    val p = LocalTrackerPalette.current
    return this.drawBehind {
        drawRect(p.canvas)
        val r1 = size.width * 0.85f
        val c1 = Offset(size.width * 0.9f, size.height * 0.08f)
        drawCircle(Brush.radialGradient(listOf(p.glowA, Color.Transparent), center = c1, radius = r1), radius = r1, center = c1)
        val r2 = size.width * 0.9f
        val c2 = Offset(size.width * 0.05f, size.height * 0.72f)
        drawCircle(Brush.radialGradient(listOf(p.glowB, Color.Transparent), center = c2, radius = r2), radius = r2, center = c2)
    }
}
