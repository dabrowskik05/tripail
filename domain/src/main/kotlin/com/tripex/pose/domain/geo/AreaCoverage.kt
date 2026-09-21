package com.tripex.pose.domain.geo

/**
 * How much of an administrative area has been discovered (M3.4).
 *
 * Deliberately not a bare `Float`: when the boundary bundle cannot supply a geometry there is no
 * coverage to report, and showing `0%` would invent a fact. Callers hide the row instead.
 */
sealed interface AreaCoverage {

    /** No geometry for this area in the local bundle — say nothing rather than guess. */
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
