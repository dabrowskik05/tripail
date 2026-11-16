package com.tripex.pose.domain.geo

import com.tripex.pose.domain.geo.atlas.Ring
import com.tripex.pose.domain.geo.projection.GeometryOps
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sqrt
import org.locationtech.jts.algorithm.Area
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.CoordinateFilter
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LinearRing
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.util.GeometryFixer
import org.locationtech.jts.operation.buffer.BufferOp
import org.locationtech.jts.operation.buffer.BufferParameters
import org.locationtech.jts.operation.overlayng.OverlayNG
import org.locationtech.jts.operation.overlayng.OverlayNGRobust
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier

/**
 * The three GeoJSON documents the map draws the fog from.
 *
 * - [dense]: the whole world minus everywhere the player has been near. Features carry
 *   `lod` = `near` (true shape) or `far` (a much wider, softer shape shown when zoomed out, so
 *   a road trip reads as a cleared patch of country rather than a hairline).
 * - [light]: the "been around here" band between dense fog and clear ground.
 * - [clear]: exactly where the player has been — corridors, cities, regions.
 */
data class FogLayers(val dense: String, val light: String, val clear: String)

/**
 * Builds the fog from the trail, the discovered cities and the discovered regions.
 *
 * Everything is merged before it is drawn, so overlapping discoveries can never cancel each
 * other out. Corridors are buffered in metres (a local flat projection per stretch of trail) and
 * come out round at any latitude.
 *
 * The slightly torn edge is not random: it is a fixed noise field over the map, so a border
 * looks the same every time it is drawn and two shapes sharing a border tear identically.
 */
@Singleton
class FogComposer
    @Inject
    constructor() {

        data class Circle(val latitude: Double, val longitude: Double, val radiusMeters: Double)

        /** A discovered region: [key] identifies it for caching, [rings] are its outlines. */
        data class Region(val key: String, val rings: List<Ring>)

        private class Shapes(val clear: Geometry?, val light: Geometry?, val far: Geometry?)

        private class CachedSegment(val size: Int, val lastLat: Double, val lastLng: Double, val shapes: Shapes)

        private val factory = GeometryFactory()
        private val segmentCache = HashMap<Long, CachedSegment>()
        private val regionCache = HashMap<String, Shapes>()

        fun empty(): FogLayers = FogLayers(
            dense = collection(listOf(worldFeature(emptyList(), LOD_NEAR), worldFeature(emptyList(), LOD_FAR))),
            light = collection(emptyList()),
            clear = collection(emptyList()),
        )

        @Synchronized
        fun compose(
            points: List<TrailPoint>,
            circles: List<Circle>,
            regions: List<Region>,
        ): FogLayers {
            val clearParts = ArrayList<Geometry>()
            val lightParts = ArrayList<Geometry>()
            val farParts = ArrayList<Geometry>()
            fun add(shapes: Shapes) {
                shapes.clear?.let(clearParts::add)
                shapes.light?.let(lightParts::add)
                shapes.far?.let(farParts::add)
            }

            // 1. The trail, one cached shape set per segment.
            val groups = LinkedHashMap<Long, MutableList<TrailPoint>>()
            for (point in points) {
                groups.getOrPut(point.segmentId * MODE_SLOTS + point.mode.ordinal) { ArrayList() } += point
            }
            segmentCache.keys.retainAll(groups.keys)
            for ((key, group) in groups) {
                val last = group.last()
                val cached = segmentCache[key]?.takeIf {
                    it.size == group.size && it.lastLat == last.latitude && it.lastLng == last.longitude
                }
                val shapes = cached?.shapes ?: segmentShapes(group).also {
                    segmentCache[key] = CachedSegment(group.size, last.latitude, last.longitude, it)
                }
                add(shapes)
            }

            // 2. Cities: the whole circle is clear, with a ring of light fog around it.
            for (circle in circles) {
                clearParts += circlePolygon(circle, 0.0)
                lightParts += circlePolygon(circle, CITY_LIGHT_MARGIN_M)
                farParts += circlePolygon(circle, CITY_FAR_MARGIN_M)
            }

            // 3. Whole regions.
            regionCache.keys.retainAll(regions.mapTo(HashSet()) { it.key })
            for (region in regions) {
                add(regionCache.getOrPut(region.key) { regionShapes(region.rings) })
            }

            val clear = union(clearParts)?.let { patchHoles(it, HOLE_KM2) } ?: return empty()
            val outer = union(lightParts + clear)?.let { patchHoles(it, HOLE_KM2) } ?: clear
            val far = union(farParts)?.let { patchHoles(it, FAR_HOLE_KM2) } ?: outer
            val lightOnly = runCatching { OverlayNGRobust.overlay(outer, clear, OverlayNG.DIFFERENCE) }
                .getOrNull()

            val total = clear.numPoints + outer.numPoints
            val detail = when {
                total < FULL_DETAIL_POINTS -> 1.0
                total < HALF_DETAIL_POINTS -> 2.5
                else -> 0.0
            }
            val fineKeys = HashSet<Long>(clear.numPoints * 2)
            for (c in clear.coordinates) fineKeys += key(c)
            val rough = Roughener(fineKeys, detail)

            val dense = ArrayList<String>()
            dense += denseFeatures(outer, LOD_NEAR, rough)
            dense += denseFeatures(far, LOD_FAR, null)
            return FogLayers(
                dense = collection(dense),
                light = collection(lightOnly?.let { polygonFeatures(it, rough) }.orEmpty()),
                clear = collection(polygonFeatures(clear, rough)),
            )
        }

        // ---------------------------------------------------------------- shapes

        private fun segmentShapes(group: List<TrailPoint>): Shapes {
            val mode = group.first().mode
            val joined = group.first().segmentId > 0 && group.size > 1
            val clearRadius = when (mode) {
                TravelMode.WALK, TravelMode.UNKNOWN -> WALK_CLEAR_M
                TravelMode.VEHICLE -> VEHICLE_CLEAR_M
                TravelMode.LEGACY -> LEGACY_CLEAR_M
            }
            val coords = group.map { Coordinate(it.longitude, it.latitude) }
            return if (joined) {
                Shapes(
                    clear = bufferLine(coords, clearRadius, simplifyM = 25.0, quad = 6),
                    light = bufferLine(coords, LIGHT_M, simplifyM = 300.0, quad = 4),
                    far = bufferLine(coords, FAR_M, simplifyM = 2_000.0, quad = 3),
                )
            } else {
                Shapes(
                    clear = bufferCloud(coords, clearRadius, gridDeg = 0.0, quad = 4),
                    light = bufferCloud(coords, LIGHT_M, gridDeg = 0.01, quad = 4),
                    far = bufferCloud(coords, FAR_M, gridDeg = 0.05, quad = 3),
                )
            }
        }

        /** A corridor around a line, cut into stretches short enough to project flat. */
        private fun bufferLine(coords: List<Coordinate>, radiusM: Double, simplifyM: Double, quad: Int): Geometry? {
            val parts = ArrayList<Geometry>()
            var start = 0
            while (start < coords.size - 1) {
                var end = start + 1
                val origin = coords[start]
                while (end < coords.size - 1 && end - start < CHUNK_POINTS &&
                    abs(coords[end].y - origin.y) < CHUNK_LAT_DEG && abs(coords[end].x - origin.x) < CHUNK_LNG_DEG
                ) {
                    end++
                }
                parts += bufferLocal(coords.subList(start, end + 1), radiusM, simplifyM, quad, asLine = true)
                start = end
            }
            return union(parts)
        }

        /** Dots around unconnected points, grouped by 1° square so each group projects flat. */
        private fun bufferCloud(coords: List<Coordinate>, radiusM: Double, gridDeg: Double, quad: Int): Geometry? {
            val buckets = HashMap<Long, MutableList<Coordinate>>()
            val seen = HashSet<Long>()
            for (c in coords) {
                if (gridDeg > 0.0) {
                    val cell = (floor(c.x / gridDeg).toLong() shl 32) xor (floor(c.y / gridDeg).toLong() and 0xffffffffL)
                    if (!seen.add(cell)) continue
                }
                val bucket = (floor(c.x).toLong() shl 32) xor (floor(c.y).toLong() and 0xffffffffL)
                buckets.getOrPut(bucket) { ArrayList() } += c
            }
            return union(buckets.values.map { bufferLocal(it, radiusM, 0.0, quad, asLine = false) })
        }

        private fun bufferLocal(
            coords: List<Coordinate>,
            radiusM: Double,
            simplifyM: Double,
            quad: Int,
            asLine: Boolean,
        ): Geometry {
            val lat0 = coords.sumOf { it.y } / coords.size
            val lng0 = coords.first().x
            val kx = METRES_PER_DEGREE * max(cos(Math.toRadians(lat0)), MIN_COS)
            val local = Array(coords.size) { i ->
                Coordinate((coords[i].x - lng0) * kx, (coords[i].y - lat0) * METRES_PER_DEGREE)
            }
            var shape: Geometry = if (asLine && local.size >= 2) {
                factory.createLineString(local)
            } else {
                factory.createMultiPointFromCoords(local)
            }
            if (asLine && simplifyM > 0.0 && local.size > 2) {
                shape = DouglasPeuckerSimplifier.simplify(shape, simplifyM)
            }
            val params = BufferParameters(quad, BufferParameters.CAP_ROUND, BufferParameters.JOIN_ROUND, 5.0)
            val buffered = BufferOp.bufferOp(shape, radiusM, params)
            buffered.apply(
                CoordinateFilter { c ->
                    c.x = lng0 + c.x / kx
                    c.y = lat0 + c.y / METRES_PER_DEGREE
                },
            )
            buffered.geometryChanged()
            return buffered
        }

        private fun circlePolygon(circle: Circle, marginM: Double): Geometry {
            val ring = GeoCircle.ring(circle.latitude, circle.longitude, circle.radiusMeters + marginM, CIRCLE_SEGMENTS)
            return factory.createPolygon(ring.toCoordinates())
        }

        private fun regionShapes(rings: List<Ring>): Shapes {
            val polygons = ArrayList<Geometry>()
            for (polygon in GeometryOps.polygonsOf(rings)) {
                val shell = polygon.firstOrNull()?.toLinearRing() ?: continue
                val holes = polygon.drop(1).mapNotNull { it.toLinearRing() }.toTypedArray()
                val built = factory.createPolygon(shell, holes)
                polygons += if (built.isValid) built else runCatching { GeometryFixer.fix(built) }.getOrNull() ?: continue
            }
            val clear = union(polygons) ?: return Shapes(null, null, null)
            val light = runCatching { DouglasPeuckerSimplifier.simplify(clear, 0.01).buffer(REGION_LIGHT_DEG, 3) }.getOrNull()
            val far = runCatching { DouglasPeuckerSimplifier.simplify(clear, 0.03).buffer(REGION_FAR_DEG, 2) }.getOrNull()
            return Shapes(clear, light ?: clear, far ?: clear)
        }

        private fun union(parts: List<Geometry>): Geometry? {
            val solid = parts.filter { !it.isEmpty }
            if (solid.isEmpty()) return null
            if (solid.size == 1) return solid.first()
            return runCatching { OverlayNGRobust.union(solid) }
                .recoverCatching { OverlayNGRobust.union(solid.map { GeometryFixer.fix(it) }) }
                .getOrNull()
        }

        /**
         * Drops small pockets of fog fully surrounded by discovered ground — the "sieve" look.
         * The limit is a fixed area: driving a ring around a big piece of country never opens it.
         */
        private fun patchHoles(geometry: Geometry, maxKm2: Double): Geometry {
            val out = ArrayList<Polygon>(geometry.numGeometries)
            for (i in 0 until geometry.numGeometries) {
                val polygon = geometry.getGeometryN(i) as? Polygon ?: continue
                if (polygon.isEmpty) continue
                if (polygon.numInteriorRing == 0) {
                    out += polygon
                    continue
                }
                val kept = ArrayList<LinearRing>()
                for (h in 0 until polygon.numInteriorRing) {
                    val hole = polygon.getInteriorRingN(h)
                    if (areaKm2(hole) > maxKm2) kept += hole
                }
                out += factory.createPolygon(polygon.exteriorRing, kept.toTypedArray())
            }
            return factory.createMultiPolygon(out.toTypedArray())
        }

        private fun areaKm2(ring: LinearRing): Double {
            val lat = ring.envelopeInternal.centre()?.y ?: 0.0
            val degrees2 = Area.ofRing(ring.coordinates)
            return degrees2 * KM_PER_DEGREE * KM_PER_DEGREE * max(cos(Math.toRadians(lat)), MIN_COS)
        }

        // ---------------------------------------------------------------- GeoJSON

        private fun denseFeatures(revealed: Geometry, lod: String, rough: Roughener?): List<String> {
            val holes = ArrayList<String>()
            val islands = ArrayList<String>()
            for (i in 0 until revealed.numGeometries) {
                val polygon = revealed.getGeometryN(i) as? Polygon ?: continue
                if (polygon.isEmpty) continue
                holes += ringJson(polygon.exteriorRing.coordinates, rough)
                for (h in 0 until polygon.numInteriorRing) {
                    islands += feature("[${ringJson(polygon.getInteriorRingN(h).coordinates, rough)}]", lod)
                }
            }
            return listOf(worldFeature(holes, lod)) + islands
        }

        private fun polygonFeatures(geometry: Geometry, rough: Roughener): List<String> {
            val features = ArrayList<String>(geometry.numGeometries)
            for (i in 0 until geometry.numGeometries) {
                val polygon = geometry.getGeometryN(i) as? Polygon ?: continue
                if (polygon.isEmpty) continue
                val rings = StringBuilder("[")
                rings.append(ringJson(polygon.exteriorRing.coordinates, rough))
                for (h in 0 until polygon.numInteriorRing) {
                    rings.append(',').append(ringJson(polygon.getInteriorRingN(h).coordinates, rough))
                }
                rings.append(']')
                features += feature(rings.toString(), LOD_NEAR)
            }
            return features
        }

        private fun worldFeature(holes: List<String>, lod: String): String {
            val rings = StringBuilder("[").append(WORLD_RING_JSON)
            for (hole in holes) rings.append(',').append(hole)
            rings.append(']')
            return feature(rings.toString(), lod)
        }

        private fun feature(coordinates: String, lod: String): String =
            """{"type":"Feature","properties":{"lod":"$lod"},"geometry":{"type":"Polygon","coordinates":$coordinates}}"""

        private fun collection(features: List<String>): String =
            features.joinToString(",", """{"type":"FeatureCollection","features":[""", "]}")

        private fun ringJson(coords: Array<Coordinate>, rough: Roughener?): String {
            val out = StringBuilder(coords.size * 24)
            out.append('[')
            var first = true
            var firstX = 0.0
            var firstY = 0.0
            fun emit(x: Double, y: Double) {
                if (first) {
                    firstX = x
                    firstY = y
                } else {
                    out.append(',')
                }
                first = false
                out.append('[')
                appendFixed(out, x)
                out.append(',')
                appendFixed(out, y)
                out.append(']')
            }
            if (rough == null) {
                for (i in 0 until coords.size - 1) emit(coords[i].x, coords[i].y)
            } else {
                rough.walk(coords, ::emit)
            }
            if (!first) {
                out.append(",[")
                appendFixed(out, firstX)
                out.append(',')
                appendFixed(out, firstY)
                out.append(']')
            }
            out.append(']')
            return out.toString()
        }

        /** Six decimals (~10 cm), never scientific notation — `1.0E-4` is not valid GeoJSON to every parser. */
        private fun appendFixed(out: StringBuilder, value: Double) {
            val clamped = value.coerceIn(-180.0, 180.0)
            val scaled = (abs(clamped) * FIXED_SCALE).roundToLong()
            if (clamped < 0 && scaled != 0L) out.append('-')
            out.append(scaled / FIXED_SCALE_LONG).append('.')
            val fraction = (scaled % FIXED_SCALE_LONG).toString()
            repeat(FIXED_DIGITS - fraction.length) { out.append('0') }
            out.append(fraction)
        }

        // ---------------------------------------------------------------- torn edge

        /**
         * Walks a ring, adding points along long edges and nudging every point by the noise field.
         *
         * Borders of clear ground get a fine tear (±[FINE_AMP_M]); the outer border of the light
         * fog also gets a broad, lazy one (±[COARSE_AMP_M]), so it reads as mist rather than as a
         * tube drawn around a road.
         */
        private class Roughener(private val fineKeys: Set<Long>, private val detail: Double) {
            fun walk(coords: Array<Coordinate>, emit: (Double, Double) -> Unit) {
                for (i in 0 until coords.size - 1) {
                    val a = coords[i]
                    val b = coords[i + 1]
                    val fine = key(a) in fineKeys && key(b) in fineKeys
                    val steps = if (detail <= 0.0) {
                        1
                    } else {
                        val step = (if (fine) FINE_STEP_M else COARSE_STEP_M) * detail
                        min(ceil(metres(a, b) / step).toInt(), MAX_EDGE_STEPS).coerceAtLeast(1)
                    }
                    for (k in 0 until steps) {
                        val t = k.toDouble() / steps
                        displace(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, fine, emit)
                    }
                }
            }

            private fun displace(lng: Double, lat: Double, fine: Boolean, emit: (Double, Double) -> Unit) {
                val u = lng * NOISE_LNG_M
                val v = lat * METRES_PER_DEGREE
                var dx = FINE_AMP_M * noise(u / FINE_WAVE_M, v / FINE_WAVE_M, 11)
                var dy = FINE_AMP_M * noise(u / FINE_WAVE_M, v / FINE_WAVE_M, 23)
                if (!fine) {
                    dx += COARSE_AMP_M * noise(u / COARSE_WAVE_M, v / COARSE_WAVE_M, 37)
                    dy += COARSE_AMP_M * noise(u / COARSE_WAVE_M, v / COARSE_WAVE_M, 53)
                }
                val kx = METRES_PER_DEGREE * max(cos(Math.toRadians(lat)), MIN_COS)
                emit(lng + dx / kx, (lat + dy / METRES_PER_DEGREE).coerceIn(-MAX_LAT, MAX_LAT))
            }

            private fun metres(a: Coordinate, b: Coordinate): Double {
                val kx = METRES_PER_DEGREE * max(cos(Math.toRadians((a.y + b.y) / 2)), MIN_COS)
                val dx = (b.x - a.x) * kx
                val dy = (b.y - a.y) * METRES_PER_DEGREE
                return sqrt(dx * dx + dy * dy)
            }

            /** Smooth value noise in [-1, 1]. */
            private fun noise(x: Double, y: Double, seed: Int): Double {
                val x0 = floor(x)
                val y0 = floor(y)
                val fx = smooth(x - x0)
                val fy = smooth(y - y0)
                val ix = x0.toLong()
                val iy = y0.toLong()
                val top = lerp(hash(ix, iy, seed), hash(ix + 1, iy, seed), fx)
                val bottom = lerp(hash(ix, iy + 1, seed), hash(ix + 1, iy + 1, seed), fx)
                return lerp(top, bottom, fy)
            }

            private fun smooth(t: Double) = t * t * (3 - 2 * t)

            private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

            private fun hash(x: Long, y: Long, seed: Int): Double {
                var h = x * 374761393L + y * 668265263L + seed * 2147483647L
                h = (h xor (h ushr 13)) * 1274126177L
                h = h xor (h ushr 16)
                return ((h and 0xFFFFL).toDouble() / 0xFFFF) * 2.0 - 1.0
            }
        }

        private fun Ring.toCoordinates(): Array<Coordinate> {
            val closed = if (first() == last()) this else this + first()
            return Array(closed.size) { Coordinate(closed[it].first, closed[it].second) }
        }

        private fun Ring.toLinearRing(): LinearRing? {
            if (size < 3) return null
            val coords = toCoordinates()
            if (coords.size < 4) return null
            return runCatching { factory.createLinearRing(coords) }.getOrNull()
        }

        companion object {
            /** Clear radius on foot or by bike, metres. */
            const val WALK_CLEAR_M = 1_000.0

            /** Clear radius from a vehicle. */
            const val VEHICLE_CLEAR_M = 500.0

            /** Old hexagon-trail points were already ~170 m cells covering the whole swath. */
            const val LEGACY_CLEAR_M = 230.0

            /** "Been around here": how far the light fog reaches from the trail. */
            const val LIGHT_M = 6_000.0

            /** The zoomed-out shape: wide enough to stay visible on a whole-country view. */
            const val FAR_M = 25_000.0

            const val CITY_LIGHT_MARGIN_M = 3_000.0
            const val CITY_FAR_MARGIN_M = 15_000.0
            const val REGION_LIGHT_DEG = 0.03
            const val REGION_FAR_DEG = 0.12

            /** Pockets of fog smaller than this, enclosed by discovered ground, are cleared. */
            const val HOLE_KM2 = 8.0
            const val FAR_HOLE_KM2 = 400.0

            const val LOD_NEAR = "near"
            const val LOD_FAR = "far"

            private const val FINE_AMP_M = 120.0
            private const val FINE_WAVE_M = 450.0
            private const val FINE_STEP_M = 150.0
            private const val COARSE_AMP_M = 600.0
            private const val COARSE_WAVE_M = 2_800.0
            private const val COARSE_STEP_M = 400.0
            private const val MAX_EDGE_STEPS = 400

            /** Above these vertex counts the edge is torn more coarsely, then not subdivided at all. */
            private const val FULL_DETAIL_POINTS = 40_000
            private const val HALF_DETAIL_POINTS = 120_000

            private const val MODE_SLOTS = 8L
            private const val CHUNK_POINTS = 400
            private const val CHUNK_LAT_DEG = 0.6
            private const val CHUNK_LNG_DEG = 1.5
            private const val CIRCLE_SEGMENTS = 48

            private const val METRES_PER_DEGREE = 111_320.0
            private const val KM_PER_DEGREE = 111.32

            /** One fixed scale for the noise lattice, so the field does not shear with latitude. */
            private const val NOISE_LNG_M = 70_000.0
            private const val MIN_COS = 0.05
            private const val MAX_LAT = 85.0
            private const val FIXED_SCALE = 1_000_000.0
            private const val FIXED_SCALE_LONG = 1_000_000L
            private const val FIXED_DIGITS = 6

            private const val WORLD_RING_JSON =
                "[[-180,-85.051128],[180,-85.051128],[180,85.051128],[-180,85.051128],[-180,-85.051128]]"

            private fun key(c: Coordinate): Long =
                ((c.x * FIXED_SCALE).roundToLong() shl 32) xor ((c.y * FIXED_SCALE).roundToLong() and 0xffffffffL)
        }
    }
