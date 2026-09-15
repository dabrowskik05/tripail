package com.tripex.pose.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

@Immutable
data class FogStyle(
    val fillColorArgb: Int = Color.Black.toArgb(),
    val fillOpacity: Float = 0.82f,
)

val LocalFogStyle = staticCompositionLocalOf { FogStyle() }
