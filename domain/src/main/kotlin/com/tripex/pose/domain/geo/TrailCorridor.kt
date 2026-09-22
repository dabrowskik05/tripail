package com.tripex.pose.domain.geo

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Points at which to stamp reveal disks between two GPS fixes (V3.1.7).
 *
 * ### Why the old bridge was not enough
 *
 * `H3Utils.bridge` returns `gridPathCells`, a chain of cells **one cell wide** — about 25 m at
 * walking resolution — strung between disks of 1 km radius. On the map that is a hairline: a
 * photographed drive came out as a row of separate dots, because the thread joining them was
 * far too thin to see. Merging the geometry does not help; the thread is genuinely that thin.
 *
 * So instead of a path *between* the disks, the disk is swept *along* the gap: stamp one every
 * half radius and the reveals overlap into a continuous band two kilometres wide.
 */
object TrailCorridor {

    private const val METRES_PER_DEGREE_LAT = 111_320.0
    private const val DEG_TO_RAD = Math.PI / 180.0
    private const val MIN_COS = 0.01

    /**
     * @return intermediate points strictly between the two fixes, empty when the gap needs no
     *   filling or is too long to fill honestly.
     */
    fun stepsBetween(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double,
        radiusMeters: Double = H3Config.WALK_REVEAL_RADIUS_M,
    ): List<Pair<Double, Double>> {
        val distance = distanceMeters(fromLat, fromLng, toLat, toLng)
        // Closer than one step: the two disks already overlap, nothing to fill.
        val step = max(radiusMeters * H3Config.BRIDGE_STEP_FRACTION, 1.0)
        if (distance <= step) return emptyList()
        // Too far to be a journey we can vouch for. A gap in the trail is honest; a corridor
        // through unvisited ground is not.
        if (distance > H3Config.MAX_BRIDGE_DISTANCE_M) return emptyList()

        val count = min((distance / step).roundToInt() - 1, H3Config.MAX_BRIDGE_STEPS)
        if (count <= 0) return emptyList()

        val points = ArrayList<Pair<Double, Double>>(count)
        for (i in 1..count) {
            val t = i.toDouble() / (count + 1).toDouble()
            points += (fromLat + (toLat - fromLat) * t) to (fromLng + (toLng - fromLng) * t)
        }
        return points
    }

    /**
     * Equirectangular approximation — good to a fraction of a percent at the distances involved
     * here, and far cheaper than haversine on every fix.
     */
    fun distanceMeters(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double,
    ): Double {
        val dLat = (toLat - fromLat) * METRES_PER_DEGREE_LAT
        val meanLat = (fromLat + toLat) / 2.0
        val dLng = (toLng - fromLng) * METRES_PER_DEGREE_LAT *
            max(cos(meanLat * DEG_TO_RAD), MIN_COS)
        return sqrt(dLat * dLat + dLng * dLng)
    }
}
