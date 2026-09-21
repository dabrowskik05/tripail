package com.tripex.pose.domain.geo

/**
 * Picks the H3 resolution used to measure how much of an area has been discovered (M3.4).
 *
 * The denominator of a coverage ratio is every cell covering the area's polygon, so resolution
 * has to fall as the area grows: Russia or Canada at resolution 7 would be millions of cells and
 * would hang the device. The thresholds below keep the denominator near or below
 * [MAX_DENOMINATOR_CELLS].
 *
 * The numerator must use the **same** resolution — compare `parentOf(cell, resolution)` against
 * the set produced here, never a mix of levels.
 */
object CoverageResolutionPolicy {

    /** Soft ceiling on the number of cells a single area may be measured with. */
    const val MAX_DENOMINATOR_CELLS: Int = 50_000

    const val SMALL_AREA_KM2: Double = 250_000.0
    const val LARGE_AREA_KM2: Double = 2_000_000.0

    /**
     * Continents, not countries. Asia is ~44M km², which even at resolution 5 would be about
     * 175 000 cells — well past [MAX_DENOMINATOR_CELLS]. One coarser tier keeps them in budget.
     */
    const val CONTINENT_AREA_KM2: Double = 10_000_000.0

    const val RESOLUTION_SMALL: Int = 7
    const val RESOLUTION_LARGE: Int = 6
    const val RESOLUTION_HUGE: Int = 5
    const val RESOLUTION_CONTINENT: Int = 4

    /**
     * @param areaKm2 approximate area of the region, e.g. from
     *   [com.tripex.pose.domain.geo.projection.GeometryOps.areaKm2].
     */
    fun resolutionFor(areaKm2: Double): Int = when {
        areaKm2 < SMALL_AREA_KM2 -> RESOLUTION_SMALL
        areaKm2 < LARGE_AREA_KM2 -> RESOLUTION_LARGE
        areaKm2 < CONTINENT_AREA_KM2 -> RESOLUTION_HUGE
        else -> RESOLUTION_CONTINENT
    }
}
