package com.tripex.pose.domain.geo

/**
 * Single public source for H3 walking resolution, LOD levels, and related thresholds.
 * Changing [WALKING_RESOLUTION] invalidates existing stored indices.
 */
object H3Config {
    const val WALKING_RESOLUTION: Int = 11
    const val LOD_MID_RESOLUTION: Int = 9
    const val LOD_FAR_RESOLUTION: Int = 7
    const val DEFAULT_RING: Int = 1

    /** ~1.3 km at res 11 — above this, bridge returns empty. */
    const val MAX_BRIDGE_CELLS: Int = 30

    /**
     * Approximate center-to-center distance of neighboring cells at walking resolution
     * (~flat-to-flat). Used to convert meters → gridDisk ring for manual unlock.
     */
    const val APPROX_NEIGHBOR_DISTANCE_M: Double = 43.0

    /** Hard cap so a huge radius cannot OOM the device (~k=150 ≈ 45k cells). */
    const val MAX_MANUAL_RING: Int = 150

    /** Default city unlock radius for Nominatim search. */
    const val MANUAL_UNLOCK_RADIUS_M: Double = 5_000.0
}
