package com.tripex.pose.ui.continent.components

/**
 * Converts between the slider's position and the world's horizontal offset (V3.6.4).
 *
 * ### Why a slider at all
 *
 * On the continent menu the zoom is fixed, so dragging has exactly one degree of freedom while
 * offering four. It is easy to push the world off screen and have no idea which way to drag back,
 * because a gesture shows no position. A slider does: left is west, right is east, and the knob
 * says where you are in the whole range.
 *
 * The offset itself is in pixels and depends on the viewport, so it cannot be stored or restored
 * meaningfully. The fraction can, which is why this mapping exists rather than the slider driving
 * pixels directly.
 */
internal object WorldPan {

    /**
     * @return `0` at the western end of the range, `1` at the eastern end, `0.5` when the world
     *   cannot pan at all (a viewport wide enough to hold it whole).
     */
    fun toFraction(pan: Float, width: Float, height: Float): Float {
        val range = WorldFit.panRange(width, height)
        val span = range.endInclusive - range.start
        if (span <= 0f) return CENTRE
        // `pan` is the world's left edge, so it grows as the world slides *east* — which moves
        // the view west. The fraction is inverted so that right on the slider means east.
        return ((range.endInclusive - pan) / span).coerceIn(0f, 1f)
    }

    /** Inverse of [toFraction]; clamped, so an out-of-range value cannot fling the world away. */
    fun toPan(fraction: Float, width: Float, height: Float): Float {
        val range = WorldFit.panRange(width, height)
        val span = range.endInclusive - range.start
        if (span <= 0f) return range.start
        return (range.endInclusive - fraction.coerceIn(0f, 1f) * span)
            .coerceIn(range.start, range.endInclusive)
    }

    const val CENTRE = 0.5f
}
