package com.tripex.pose.domain.geo

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

/**
 * How much of the world one searched place should uncover (M4.5, pulled forward).
 *
 * The game rewards arriving somewhere, not pacing its districts: finding a city is meant to clear
 * the whole city at once. The geocoder's bounding box is the honest measure of how big a place is,
 * so it is the primary source; the place kind is only a fallback for results without one.
 *
 * Match relevance deliberately plays no part here — it says how well the name matched, not how
 * large the place is, and mixing the two would make a confident match uncover more ground.
 */
object RevealRadiusPolicy {

    const val MIN_RADIUS_M: Double = 1_500.0
    const val MAX_RADIUS_M: Double = 25_000.0

    /**
     * Slack added around a measured place. The rule is "the whole city plus a kilometre", so the
     * bounding box is taken at face value and padded — trimming it (the old 0.9 factor) left the
     * outskirts of a city under parchment right after the player had supposedly discovered it.
     */
    const val CITY_PADDING_M: Double = 1_000.0

    private const val EARTH_RADIUS_M = 6_371_000.0
    private const val DEG_TO_RAD = Math.PI / 180.0

    fun radiusMeters(place: Place): Double {
        val fromBounds = place.boundingBox?.let { halfDiagonalMeters(it) + CITY_PADDING_M }
        val raw = fromBounds ?: fallbackRadius(place)
        return raw.coerceIn(MIN_RADIUS_M, MAX_RADIUS_M)
    }

    /** Half the great-circle diagonal of the box, measured at its own latitude. */
    fun halfDiagonalMeters(bounds: GeoBounds): Double {
        val midLatRad = (bounds.north + bounds.south) / 2.0 * DEG_TO_RAD
        val heightM = abs(bounds.north - bounds.south) * DEG_TO_RAD * EARTH_RADIUS_M
        val widthM = abs(bounds.east - bounds.west) * DEG_TO_RAD * EARTH_RADIUS_M * cos(midLatRad)
        return hypot(widthM, heightM) / 2.0
    }

    /**
     * Without a box there is nothing to measure, so the radius comes from what the place is.
     * Values are deliberately generous — under-revealing is the worse failure here.
     */
    private fun fallbackRadius(place: Place): Double = when (place.kind) {
        PlaceKind.City -> 8_000.0
        PlaceKind.Town -> 4_000.0
        PlaceKind.Municipality -> 3_000.0
        PlaceKind.Village -> 2_000.0
        PlaceKind.Address, PlaceKind.Poi -> 800.0
        PlaceKind.Region, PlaceKind.Country -> MAX_RADIUS_M
        PlaceKind.Unknown -> 3_000.0
    }
}
