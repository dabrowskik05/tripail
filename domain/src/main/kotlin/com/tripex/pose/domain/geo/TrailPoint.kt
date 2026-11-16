package com.tripex.pose.domain.geo

/** How the player was moving when a trail point was recorded. Decides how wide the fog opens. */
enum class TravelMode {
    /** On foot or by bike: the fog clears completely, 1 km to each side. */
    WALK,

    /** Car, train, bus: a narrower clear strip — from a vehicle you see less. */
    VEHICLE,

    /** Imported or one-off points with no movement to judge by. Drawn like [WALK]. */
    UNKNOWN,

    /** Carried over from the old hexagon trail: already area-shaped, so drawn as small dots. */
    LEGACY,
}

/**
 * One remembered position, snapped to a ~150 m grid so the exact spot is never stored.
 *
 * Points sharing a positive [segmentId] were recorded one after another and are joined into a
 * corridor. [SEGMENT_SINGLE] and [SEGMENT_LEGACY] points are never joined to anything.
 */
data class TrailPoint(
    val latitude: Double,
    val longitude: Double,
    val mode: TravelMode,
    val segmentId: Long,
    val discoveredAt: Long,
) {
    companion object {
        const val SEGMENT_LEGACY = 0L
        const val SEGMENT_SINGLE = -1L
    }
}
