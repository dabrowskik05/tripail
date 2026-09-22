package com.tripex.pose.domain.geo

/**
 * How much of an administrative area has been discovered (M3.4).
 *
 * ### A reversed decision (V3.3.8)
 *
 * This used to argue that showing `0%` without a geometry "invents a fact", so [Unavailable]
 * existed and the UI hid the row. In practice the message reached real countries, because the
 * denominator came back empty for a different reason: at a coarse coverage resolution a small
 * country can contain no cell *centre* at all, so the polyfill yields nothing even though the
 * geometry is perfectly good. The player saw "no data about this area" for Luxembourg.
 *
 * That cause is now fixed at the source — the resolution steps down until the denominator is
 * real — so `0%` is no longer a guess. It means what it says: nothing here has been discovered
 * yet. [Unavailable] survives only for areas that genuinely have no geometry, and the UI renders
 * it as an ordinary empty progress bar rather than an apology.
 */
sealed interface AreaCoverage {

    /**
     * No geometry for this area in the local bundle.
     *
     * Kept as a distinct case because the *diagnosis* matters to us — it is the difference
     * between a bundle gap and an untouched country — but it is no longer a distinct thing to
     * show. See [fractionOrZero].
     */
    data object Unavailable : AreaCoverage

    /**
     * @param fraction discovered share in `0f..1f`.
     * @param resolution H3 resolution both sides of the ratio were measured at.
     * @param areaCells denominator — cells covering the area's polygon.
     * @param discoveredCells numerator — those cells with at least one unlocked descendant.
     */
    data class Known(
        val fraction: Float,
        val resolution: Int,
        val areaCells: Int,
        val discoveredCells: Int,
    ) : AreaCoverage

    /** What the progress bar draws. Unknown and untouched look the same to the player. */
    val fractionOrZero: Float
        get() = (this as? Known)?.fraction ?: 0f

    companion object {
        fun of(discoveredCells: Int, areaCells: Int, resolution: Int): AreaCoverage =
            if (areaCells <= 0) {
                Unavailable
            } else {
                Known(
                    fraction = (discoveredCells.toFloat() / areaCells.toFloat()).coerceIn(0f, 1f),
                    resolution = resolution,
                    areaCells = areaCells,
                    discoveredCells = discoveredCells,
                )
            }
    }
}
