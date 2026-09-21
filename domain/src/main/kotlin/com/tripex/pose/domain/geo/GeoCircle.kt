package com.tripex.pose.domain.geo

import com.tripex.pose.domain.geo.atlas.Ring
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * A circular area as a polygon ring, used to cut a whole settlement out of the fog in one shape.
 *
 * Cities are unlocked as circles rather than as H3 cells: Warsaw at walking resolution is roughly
 * 770 000 indices, which is not a database row count, it is a denial of service on your own app.
 * A circle is three numbers and a ring generated on demand.
 */
object GeoCircle {

    const val DEFAULT_SEGMENTS = 64

    private const val METRES_PER_DEGREE_LAT = 111_320.0
    private const val DEG_TO_RAD = PI / 180.0

    /** @return closed ring of `[lng, lat]` pairs, GeoJSON order. */
    fun ring(
        lat: Double,
        lng: Double,
        radiusMeters: Double,
        segments: Int = DEFAULT_SEGMENTS,
    ): Ring {
        val steps = max(segments, MIN_SEGMENTS)
        val dLat = radiusMeters / METRES_PER_DEGREE_LAT
        // Longitude degrees shrink towards the poles, so the circle must not become an ellipse.
        val dLng = radiusMeters / (METRES_PER_DEGREE_LAT * max(cos(lat * DEG_TO_RAD), MIN_COS))
        val points = ArrayList<Pair<Double, Double>>(steps + 1)
        for (i in 0 until steps) {
            val angle = 2.0 * PI * i / steps
            points += (lng + dLng * cos(angle)) to (lat + dLat * sin(angle))
        }
        points += points.first()
        return points
    }

    private const val MIN_SEGMENTS = 8

    /** Keeps the longitude scaling finite near the poles. */
    private const val MIN_COS = 0.01
}
