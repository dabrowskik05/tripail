package com.tripex.pose.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class CartoonStyle(
    val inkPrimary: Color = Color(0xFF2B2250),
    val accentPink: Color = Color(0xFFFF5E7E),
    val skyTop: Color = Color(0xFFBFE9F7),
    val skyBottom: Color = Color(0xFF8FD4EA),
    val accentPinkShadow: Color = Color(0xFFD43F5E),
    val accentOrange: Color = Color(0xFFFF8A5B),
    val sunYellowShadow: Color = Color(0xFFD9A52F),
    val grassGreen: Color = Color(0xFF58C97A),
    val chunkyShadowOffset: Dp = 6.dp,
    val continentColors: List<Color> = listOf(
        Color(0xFF9AA5B1),
        Color(0xFF4CAF6D),
        Color(0xFF4C8FE0),
        Color(0xFFF5893C),
        Color(0xFFD64545),
        Color(0xFFB4552F),
        Color(0xFFF1F5F8),
    ),
) {
    val skyBrush: Brush
        get() = Brush.verticalGradient(listOf(skyTop, skyBottom))
}

val LocalCartoonStyle = staticCompositionLocalOf { CartoonStyle() }
