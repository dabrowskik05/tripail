package com.tripex.pose.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * How the fog looks. Values come from the approved browser prototype (`prototype/fog.html`).
 *
 * The one rule behind all of them: fog is darker and more faded than discovered ground, by day
 * and by night — what you have seen must always look brighter and more alive than what you have
 * not.
 */
@Immutable
data class RevealStyle(
    val paperDay: Int = Color(0xFFCBBE9F).toArgb(),
    val paperNight: Int = Color(0xFF1C2038).toArgb(),
    val denseOpacity: Float = 0.95f,
    /** Deliberately faint: the step from clear ground to "been around here" should barely show. */
    val lightOpacity: Float = 0.2f,
    val inkDay: Int = Color(0xFF4E3B26).toArgb(),
    val inkNight: Int = Color(0xFFFFD38A).toArgb(),
    val inkWashDay: Int = Color(0xFF8A6A44).toArgb(),
    val inkWashNight: Int = Color(0xFFFFB84D).toArgb(),
    /** Laid over the whole map at night; discovered ground is dimmed only this much. */
    val nightShade: Int = Color(0xFF0E1430).toArgb(),
    val nightShadeOpacity: Float = 0.25f,
    /** How much of the continent's colour is mixed into the paper — a signature, not a paint job. */
    val continentTint: Float = 0.1f,
) {
    /** Kept for the boundary layers, which borrow the paper colour. */
    val washColorArgb: Int get() = paperDay

    fun paper(night: Boolean): Int = if (night) paperNight else paperDay
    fun ink(night: Boolean): Int = if (night) inkNight else inkDay
    fun inkWash(night: Boolean): Int = if (night) inkWashNight else inkWashDay
}

val LocalRevealStyle = staticCompositionLocalOf { RevealStyle() }
