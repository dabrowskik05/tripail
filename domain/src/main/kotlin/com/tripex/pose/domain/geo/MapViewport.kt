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
        val DEFAULT: MapViewport =
            MapViewport(
                bounds =
                    GeoBounds(
                        north = 52.30,
                        south = 52.15,
                        east = 21.10,
                        west = 20.90,
                    ),
                zoom = 12.0,
            )

        /**
         * Everything, seen from far away.
         *
         * The starting value before the camera has reported anything. It resolves to the coarse
         * fog LOD, which is a cheap global query — the opposite of guessing a local viewport and
         * asking for walking-resolution cells somewhere the player is not.
         */
        val WORLD: MapViewport =
            MapViewport(
                bounds = GeoBounds(north = 85.0, south = -85.0, east = 180.0, west = -180.0),
                zoom = 2.0,
            )
    }
}
