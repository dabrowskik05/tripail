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
     * Finest resolution worth stepping down to when a polygon turns out to be too small to
     * contain a single cell centre.
     *
     * Capped at the trail resolution because the numerator has to be expressed at the same
     * level, and discovered ground is only stored lifted to resolutions 9 and 7. Refining past
     * that asked H3 for a parent *finer* than the cell ("res (8) must be between 0 and 7") and
     * crashed the app. Resolution 9 cells are ~0.1 km², smaller than any country or region.
     */
    const val FINEST_RESOLUTION: Int = H3Config.TRAIL_RESOLUTION

    /**
     * Next finer resolution, or `null` at the floor (V3.3.8).
     *
     * A polyfill only keeps cells whose **centre** falls inside the polygon, so a country smaller
     * than one cell — Monaco at resolution 7 — yields an empty denominator and the coverage row
     * used to read "no data about this area". The geometry was never the problem; the ruler was
     * too coarse. Refining is safe precisely because it only happens when the area proved tiny.
     */
    fun refined(resolution: Int): Int? =
        if (resolution >= FINEST_RESOLUTION) null else resolution + 1

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
