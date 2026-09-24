package com.tripex.pose.domain.geo

/**
 * Single public source for H3 resolutions and related thresholds.
 * Changing [WALKING_RESOLUTION] invalidates existing stored indices.
 */
object H3Config {
    /** What a GPS fix is stored at. */
    const val WALKING_RESOLUTION: Int = 11

    /**
     * What the walked trail is **drawn** at — one resolution for every zoom and the whole world.
     *
     * The trail used to switch between resolutions 11, 9 and 7 as the camera zoomed (`FogLod`),
     * so the same ground changed shape under the player: a jigsaw of hexagons of three sizes
     * instead of one corridor. Resolution 9 (~170 m edge) is fine enough next to a 1 km reveal
     * radius and coarse enough that years of travel stay a few tens of thousands of cells.
     */
    const val TRAIL_RESOLUTION: Int = 9

    /** Coarse parents: coverage statistics and continent estimates. */
    const val COARSE_RESOLUTION: Int = 7
    const val DEFAULT_RING: Int = 1

    /**
     * Radius wiped clear around each GPS fix, so walking or driving cuts a 2 km-wide swath.
     *
     * The game is meant to reward moving through the world, not pacing individual streets — a
     * 43 m ring (`DEFAULT_RING`) made a road trip reveal a hairline.
     */
    const val WALK_REVEAL_RADIUS_M: Double = 1_000.0

    /** ~1.3 km at res 11 — above this, the cell-path bridge returns empty. */
    const val MAX_BRIDGE_CELLS: Int = 30

    /**
     * Longest gap between two fixes that still gets filled in, in **metres** (V3.1.7).
     *
     * The old limit was counted in cells (`MAX_BRIDGE_CELLS`, ~1.3 km), which is far too tight
     * for a car: at 100 km/h consecutive fixes are ~420 m apart, so a single dropped fix put the
     * pair out of range and the bridge vanished without a sound. 20 km covers a lost signal
     * through a tunnel or a few minutes of motorway; beyond that a straight corridor would claim
     * ground nobody travelled, and an honest gap is better.
     */
    const val MAX_BRIDGE_DISTANCE_M: Double = 20_000.0

    /**
     * Spacing of the disks swept along the gap, as a fraction of [WALK_REVEAL_RADIUS_M].
     *
     * Half a radius guarantees consecutive disks overlap, so the trail is a continuous band
     * rather than a string of beads. Smaller would be smoother and cost cells for nothing.
     */
    const val BRIDGE_STEP_FRACTION: Double = 0.5

    /** Ceiling on interpolated steps, so a long gap cannot balloon into millions of cells. */
    const val MAX_BRIDGE_STEPS: Int = 64

    /**
     * Approximate center-to-center distance of neighboring cells at walking resolution
     * (~flat-to-flat). Used to convert meters → gridDisk ring for manual unlock.
     */
    const val APPROX_NEIGHBOR_DISTANCE_M: Double = 43.0

    /** Hard cap so a huge radius cannot OOM the device (~k=600 ≈ 1.08M cells). */
    const val MAX_MANUAL_RING: Int = 600
}
