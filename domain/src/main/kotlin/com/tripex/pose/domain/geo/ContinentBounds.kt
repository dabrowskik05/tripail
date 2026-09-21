package com.tripex.pose.domain.geo

/**
 * Approximate land bounding boxes and estimated land-cell counts at [H3Config.LOD_FAR_RESOLUTION].
 *
 * Estimates are order-of-magnitude constants for coverage ratios — not exact coastlines.
 * Changing [H3Config.LOD_FAR_RESOLUTION] requires recalibrating [estimatedLandCells].
 */
object ContinentBounds {
    data class ContinentRegion(
        val id: ContinentId,
        val bounds: GeoBounds,
        /**
         * Rough land-cell estimate at LOD_FAR, kept only as a fallback for when the overview
         * atlas fails to load. Since M3.4 the displayed percentage is measured from real atlas
         * geometry by `ObserveAreaCoverageUseCase` — this number is no longer a source of truth.
         */
        val estimatedLandCells: Int,
    )

    val ALL: List<ContinentRegion> =
        listOf(
            ContinentRegion(
                id = ContinentId.NorthAmerica,
                bounds = GeoBounds(north = 83.0, south = 7.0, east = -52.0, west = -168.0),
                estimatedLandCells = 48_000,
            ),
            ContinentRegion(
                id = ContinentId.SouthAmerica,
                bounds = GeoBounds(north = 13.0, south = -56.0, east = -34.0, west = -82.0),
                estimatedLandCells = 28_000,
            ),
            ContinentRegion(
                id = ContinentId.Europe,
                bounds = GeoBounds(north = 72.0, south = 34.0, east = 40.0, west = -25.0),
                estimatedLandCells = 18_000,
            ),
            ContinentRegion(
                id = ContinentId.Africa,
                bounds = GeoBounds(north = 38.0, south = -35.0, east = 52.0, west = -18.0),
                estimatedLandCells = 42_000,
            ),
            ContinentRegion(
                id = ContinentId.Asia,
                bounds = GeoBounds(north = 78.0, south = -11.0, east = 180.0, west = 25.0),
                estimatedLandCells = 95_000,
            ),
            ContinentRegion(
                id = ContinentId.Oceania,
                bounds = GeoBounds(north = 0.0, south = -50.0, east = 180.0, west = 110.0),
                estimatedLandCells = 14_000,
            ),
            ContinentRegion(
                id = ContinentId.Antarctica,
                bounds = GeoBounds(north = -60.0, south = -85.0, east = 180.0, west = -180.0),
                estimatedLandCells = 22_000,
            ),
        )

    fun region(id: ContinentId): ContinentRegion = ALL.first { it.id == id }

    /**
     * Box to place the camera on when entering a continent.
     *
     * The atlas outline is more faithful than the constants above — except across the
     * antimeridian. Asia and Oceania own land on both sides of 180°, so the bbox of their outline
     * comes out as almost the whole planet, and framing it would show the entire world instead of
     * the continent the player just picked. There the hand-curated box wins.
     */
    fun framing(
        id: ContinentId,
        atlasBounds: GeoBounds?,
    ): GeoBounds {
        val fallback = region(id).bounds
        if (atlasBounds == null) return fallback
        val span = atlasBounds.east - atlasBounds.west
        return if (span >= NEAR_GLOBAL_SPAN_DEG) fallback else atlasBounds
    }

    /** A longitude span this wide is an antimeridian artefact, not a real extent. */
    const val NEAR_GLOBAL_SPAN_DEG: Double = 350.0

    fun contains(
        bounds: GeoBounds,
        lat: Double,
        lng: Double,
    ): Boolean {
        if (lat > bounds.north || lat < bounds.south) return false
        return if (bounds.west <= bounds.east) {
            lng >= bounds.west && lng <= bounds.east
        } else {
            // Antimeridian-spanning box
            lng >= bounds.west || lng <= bounds.east
        }
    }

    fun center(bounds: GeoBounds): Pair<Double, Double> {
        val lat = (bounds.north + bounds.south) / 2.0
        val lng =
            if (bounds.west <= bounds.east) {
                (bounds.west + bounds.east) / 2.0
            } else {
                val mid = (bounds.west + bounds.east + 360.0) / 2.0
                if (mid > 180.0) mid - 360.0 else mid
            }
        return lat to lng
    }

    /** Suggested MapLibre zoom when opening a continent overview. */
    const val MAP_OVERVIEW_ZOOM: Double = 3.5
}
