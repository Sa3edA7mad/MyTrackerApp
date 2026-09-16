package com.example.mytrackerapp.ui.theme

import androidx.compose.ui.unit.dp

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val base = 16.dp

    /** Standard screen gutter. */
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val huge = 40.dp
    val jumbo = 48.dp
    val max = 64.dp
}

object Radius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val pill = 999.dp
}

/**
 * Minimum interactive target. Every clickable element in this app must be at least
 * this tall and wide, which is why the 13-row checklist scrolls rather than fitting
 * on one screen.
 */
val MinTouchTarget = 48.dp
