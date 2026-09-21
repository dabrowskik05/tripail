package com.tripex.pose.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Parchment wash over the vivid basemap; unlocked H3 rings punch holes so streets stay vivid.
 */
@Immutable
data class RevealStyle(
    val washColorArgb: Int = Color(0xFFD4C4A8).toArgb(),
    /** High enough that streets vanish; the basemap only bleeds through as soft tone. */
    val washOpacity: Float = 0.94f,
    val edgeColorArgb: Int = Color(0x662B2250).toArgb(),
    val edgeWidthDp: Float = 1.2f,
)

val LocalRevealStyle = staticCompositionLocalOf { RevealStyle() }
