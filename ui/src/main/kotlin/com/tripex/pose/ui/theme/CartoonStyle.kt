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
    /** Flat ocean fill shared by the start screen and the world overview. */
    val oceanBlue: Color = Color(0xFFBCE3F7),
    /** Slightly deeper water toward the bottom — a very soft sense of depth, not a banded gradient. */
    val oceanDeep: Color = Color(0xFFA3D6F1),
    /** Wave dashes: a darker tint of the water, since white is invisible on a light ocean. */
    val oceanWave: Color = Color(0xFF6FAECF),
    val accentPinkShadow: Color = Color(0xFFD43F5E),
    val accentOrange: Color = Color(0xFFFF8A5B),
    val sunYellowShadow: Color = Color(0xFFD9A52F),
    val grassGreen: Color = Color(0xFF58C97A),
    val paperBg: Color = Color(0xFFF6F4EE),
    /** Primary action colour — calm teal, replaces the red/green chunky button. */
    val buttonPrimary: Color = Color(0xFF4A8B80),
    val buttonOnPrimary: Color = Color(0xFFFFFFFF),
    /** Fill of the selected country/region on the boundary map (M3.1). */
    val mapLand: Color = Color(0xFFE6C88A),
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
