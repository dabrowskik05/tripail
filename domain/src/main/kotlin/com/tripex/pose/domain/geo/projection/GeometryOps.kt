package com.tripex.pose.domain.geo.projection

import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.atlas.Ring
import kotlin.math.abs
import kotlin.math.cos

/**
 * Pure geometry helpers for atlas hit-testing, bounds, Chaikin smoothing, and simplify.
 */
object GeometryOps {
    private const val EARTH_RADIUS_KM = 6371.0

    /**
     * Ray-casting point-in-polygon. Boundary points count as inside.
     * Closed rings (first == last) are accepted; the duplicate endpoint is ignored.
     */
    fun pointInRing(ring: Ring, x: Double, y: Double): Boolean {
        if (ring.size < 3) return false
        if (pointOnRingBoundary(ring, x, y)) return true

        val closed = ring.first() == ring.last()
        val last = if (closed) ring.lastIndex - 1 else ring.lastIndex
        if (last < 2) return false

        var inside = false
        var j = last
        for (i in 0..last) {
            val (xi, yi) = ring[i]
            val (xj, yj) = ring[j]
            val intersect = ((yi > y) != (yj > y)) &&
                (x < (xj - xi) * (y - yi) / (yj - yi) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    /**
     * Point inside exterior and outside all holes.
     * [rings] — first is exterior, rest are holes (GeoJSON polygon convention).
     */
    fun pointInPolygon(rings: List<Ring>, x: Double, y: Double): Boolean {
        if (rings.isEmpty()) return false
        if (!pointInRing(rings.first(), x, y)) return false
        for (i in 1 until rings.size) {
            if (pointInRing(rings[i], x, y)) return false
        }
        return true
    }

    fun boundsOf(rings: List<Ring>): GeoBounds {
        var north = Double.NEGATIVE_INFINITY
        var south = Double.POSITIVE_INFINITY
        var east = Double.NEGATIVE_INFINITY
        var west = Double.POSITIVE_INFINITY
        for (ring in rings) {
            for ((lng, lat) in ring) {
                if (lat > north) north = lat
                if (lat < south) south = lat
                if (lng > east) east = lng
                if (lng < west) west = lng
            }
        }
        require(north.isFinite()) { "Empty rings have no bounds" }
        return GeoBounds(north = north, south = south, east = east, west = west)
    }

    /**
     * Bounding box of a country's **main landmass** — the largest ring only, not every ring the
     * feature owns.
     *
     * Framing the full extent is a trap the project already fell into once: France reaches from
     * French Guiana to New Caledonia and Norway owns Jan Mayen, so the whole-geometry box is most
     * of the planet and the camera ends up over open ocean. Dropping the outlying rings is the
     * point — the camera has to land on the part of the country the player meant.
     */
    fun mainlandBounds(rings: List<Ring>): GeoBounds {
        val largest = rings.filter { it.size >= 3 }.maxByOrNull { approximateRingArea(it) }
        return boundsOf(listOfNotNull(largest).ifEmpty { rings })
    }

    /**
     * Shoelace area in square degrees, with longitudes narrowed by the ring's own latitude.
     *
     * Only ever compared against another ring's value, so the cosine correction is enough: it
     * stops a sprawling Arctic island from outranking a compact mainland further south.
     */
    private fun approximateRingArea(ring: Ring): Double {
        var sum = 0.0
        var north = Double.NEGATIVE_INFINITY
        var south = Double.POSITIVE_INFINITY
        var j = ring.lastIndex
        for (i in ring.indices) {
            val (xi, yi) = ring[i]
            val (xj, yj) = ring[j]
            sum += (xj + xi) * (yj - yi)
            if (yi > north) north = yi
            if (yi < south) south = yi
            j = i
        }
        val midLat = (north + south) / 2.0
        return abs(sum / 2.0) * cos(midLat * Math.PI / 180.0)
    }

    fun centroidOf(bounds: GeoBounds): Pair<Double, Double> {
        val lng = (bounds.west + bounds.east) / 2.0
        val lat = (bounds.south + bounds.north) / 2.0
        return lng to lat
    }

    /** Approximate area of an axis-aligned geographic bbox in km² (spherical). */
    fun areaKm2(bounds: GeoBounds): Double {
        val latSpan = abs(bounds.north - bounds.south) * Math.PI / 180.0
        val lngSpan = abs(bounds.east - bounds.west) * Math.PI / 180.0
        val midLat = (bounds.north + bounds.south) / 2.0 * Math.PI / 180.0
        val height = EARTH_RADIUS_KM * latSpan
        val width = EARTH_RADIUS_KM * lngSpan * cos(midLat)
        return abs(width * height)
    }

    /**
     * Chaikin corner-cutting. Preserves ring closure when the input is closed.
     * [minSegmentEps] skips cutting when a segment is shorter than the epsilon (lng/lat degrees).
     */
    fun chaikin(ring: Ring, iterations: Int, minSegmentEps: Double = 1e-9): Ring {
        if (ring.size < 3 || iterations <= 0) return ring
        var current = ring
        repeat(iterations) {
            current = chaikinOnce(current, minSegmentEps)
        }
        return current
    }

    /**
     * Douglas–Peucker simplification. Keeps endpoints; works on open or closed rings.
     */
    fun simplify(ring: Ring, tolerance: Double): Ring {
        if (ring.size <= 2 || tolerance <= 0.0) return ring
        val closed = ring.size >= 2 && ring.first() == ring.last()
        val open = if (closed) ring.dropLast(1) else ring
        if (open.size <= 2) return ring
        val kept = douglasPeucker(open, tolerance)
        return if (closed) kept + kept.first() else kept
    }

    private fun chaikinOnce(ring: Ring, minSegmentEps: Double): Ring {
        val closed = ring.size >= 2 && ring.first() == ring.last()
        val pts = if (closed) ring.dropLast(1) else ring
        if (pts.size < 2) return ring
        val out = ArrayList<Pair<Double, Double>>(pts.size * 2)
        for (i in pts.indices) {
            val (x0, y0) = pts[i]
            val (x1, y1) = pts[(i + 1) % pts.size]
            val dx = x1 - x0
            val dy = y1 - y0
            if (abs(dx) < minSegmentEps && abs(dy) < minSegmentEps) {
                out += x0 to y0
                continue
            }
            out += (0.75 * x0 + 0.25 * x1) to (0.75 * y0 + 0.25 * y1)
            out += (0.25 * x0 + 0.75 * x1) to (0.25 * y0 + 0.75 * y1)
        }
        if (closed && out.isNotEmpty()) {
            out += out.first()
        }
        return out
    }

    private fun douglasPeucker(points: List<Pair<Double, Double>>, tolerance: Double): List<Pair<Double, Double>> {
        if (points.size <= 2) return points
        var maxDist = 0.0
        var index = 0
        val start = points.first()
        val end = points.last()
        for (i in 1 until points.lastIndex) {
            val d = perpendicularDistance(points[i], start, end)
            if (d > maxDist) {
                maxDist = d
                index = i
            }
        }
        if (maxDist <= tolerance) {
            return listOf(start, end)
        }
        val left = douglasPeucker(points.subList(0, index + 1), tolerance)
        val right = douglasPeucker(points.subList(index, points.size), tolerance)
        return left.dropLast(1) + right
    }

    private fun perpendicularDistance(
        point: Pair<Double, Double>,
        lineStart: Pair<Double, Double>,
        lineEnd: Pair<Double, Double>,
    ): Double {
        val (x, y) = point
        val (x1, y1) = lineStart
        val (x2, y2) = lineEnd
        val dx = x2 - x1
        val dy = y2 - y1
        if (dx == 0.0 && dy == 0.0) {
            val ex = x - x1
            val ey = y - y1
            return kotlin.math.sqrt(ex * ex + ey * ey)
        }
        val t = ((x - x1) * dx + (y - y1) * dy) / (dx * dx + dy * dy)
        val projX = x1 + t * dx
        val projY = y1 + t * dy
        val ex = x - projX
        val ey = y - projY
        return kotlin.math.sqrt(ex * ex + ey * ey)
    }

    private fun pointOnRingBoundary(ring: Ring, x: Double, y: Double, eps: Double = 1e-9): Boolean {
        var j = ring.size - 1
        for (i in ring.indices) {
            if (pointOnSegment(x, y, ring[j].first, ring[j].second, ring[i].first, ring[i].second, eps)) {
                return true
            }
            j = i
        }
        return false
    }

    private fun pointOnSegment(
        px: Double,
        py: Double,
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        eps: Double,
    ): Boolean {
        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        if (lenSq <= eps * eps) return false // degenerate / closing duplicate
        val cross = (py - y1) * dx - (px - x1) * dy
        if (abs(cross) > eps) return false
        val dot = (px - x1) * dx + (py - y1) * dy
        if (dot < 0) return false
        return dot <= lenSq + eps
    }
}
