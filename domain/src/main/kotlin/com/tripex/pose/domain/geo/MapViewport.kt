package com.tripex.pose.domain.geo

/**
 * Visible map window used to pick LOD and query unlocked cells.
 */
data class MapViewport(
    val bounds: GeoBounds,
    val zoom: Double,
) {
    companion object {
        /** Warsaw-ish default so first fog query is local, not planetary. */
        val DEFAULT: MapViewport = MapViewport(
            bounds = GeoBounds(
                north = 52.30,
                south = 52.15,
                east = 21.10,
                west = 20.90,
            ),
            zoom = 12.0,
        )
    }
}
