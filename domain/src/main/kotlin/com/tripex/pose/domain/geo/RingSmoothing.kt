package com.tripex.pose.domain.geo

import com.tripex.pose.domain.geo.atlas.Ring

/**
 * Rounds the corners of the walked trail's outline (Chaikin corner cutting).
 *
 * A trail outlined straight from H3 is a saw-tooth of hexagon edges. The reveal is meant to read
 * as a corridor, so every corner is replaced by two points a quarter of the way along its edges.
 * Two passes are enough to lose the hexagon look; each pass quadruples nothing, it doubles the
 * vertex count, so the cost stays linear in the size of the outline.
 *
 * The cut stays inside the original shape by at most a quarter of an edge (~40 m at the trail
 * resolution) — invisible next to a 1 km reveal radius.
 */
object RingSmoothing {

    const val DEFAULT_ITERATIONS = 2

    private const val NEAR = 0.75
    private const val FAR = 0.25

    /** @param ring closed ring (first point repeated last); anything shorter than a triangle is returned as is. */
    fun smooth(ring: Ring, iterations: Int = DEFAULT_ITERATIONS): Ring {
        var current = ring.open() ?: return ring
        repeat(iterations) { current = cut(current) }
        return current + current.first()
    }

    private fun cut(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        val out = ArrayList<Pair<Double, Double>>(points.size * 2)
        for (i in points.indices) {
            val (x0, y0) = points[i]
            val (x1, y1) = points[(i + 1) % points.size]
            out += (NEAR * x0 + FAR * x1) to (NEAR * y0 + FAR * y1)
            out += (FAR * x0 + NEAR * x1) to (FAR * y0 + NEAR * y1)
        }
        return out
    }

    /** The ring without its closing point, or `null` when it is too short to be an area. */
    private fun Ring.open(): List<Pair<Double, Double>>? {
        val points = if (size > 1 && first() == last()) dropLast(1) else this
        return points.takeIf { it.size >= MIN_POINTS }
    }

    private const val MIN_POINTS = 3
}
