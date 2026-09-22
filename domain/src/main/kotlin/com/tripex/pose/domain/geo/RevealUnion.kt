package com.tripex.pose.domain.geo

import com.tripex.pose.domain.geo.atlas.Ring
import javax.inject.Inject
import javax.inject.Singleton
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LinearRing
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.PrecisionModel
import org.locationtech.jts.geom.util.GeometryFixer
import org.locationtech.jts.operation.union.UnaryUnionOp

/**
 * Merges every revealed shape into one set of **disjoint** polygons (V3.1.1, V3.1.5).
 *
 * ### Why this exists
 *
 * The renderer draws fog as a single world polygon with revealed areas as holes. GeoJSON — and
 * the earcut tessellator MapLibre uses — assume holes do not intersect. They did: a city circle
 * overlapping a walked H3 trail came back as *fog* in the overlap, photographed on the road near
 * Skierniewice. There is no `fill-rule` in MapLibre GL to switch; the fix has to be geometric.
 *
 * So: union everything first, hand the renderer shapes that cannot overlap because they are one
 * shape. All three sources — H3 outlines, city circles, region outlines — go in together, in one
 * pass. Unioning them in stages would leave exactly the seams this class exists to remove.
 *
 * ### Coordinates
 *
 * Input and output are `[lng, lat]` degrees, GeoJSON order. The union runs in that flat degree
 * space rather than a projection: the shapes are small relative to the globe, the result is only
 * ever drawn (never measured), and reprojecting to metres and back would cost more than it buys.
 * Areas spanning the antimeridian are not handled — see [MAX_SPAN_DEGREES].
 */
@Singleton
class RevealUnion
    @Inject
    constructor() {

        /**
         * @param shapes every revealed shape, from all sources. Each entry is one polygon in
         *   GeoJSON nesting: `[exterior, hole, hole, …]`. Holes matter — walking a loop around a
         *   block leaves an unvisited middle, and flattening that to its exterior would quietly
         *   reveal ground nobody went to.
         * @return disjoint polygons with their holes, in [FogGeometry]'s nesting.
         */
        fun union(shapes: List<List<Ring>>): FogGeometry {
            if (shapes.isEmpty()) return FogGeometry.EMPTY

            val polygons = ArrayList<Polygon>(shapes.size)
            for (rings in shapes) {
                polygons += toPolygons(rings)
            }
            if (polygons.isEmpty()) return FogGeometry.EMPTY
            // One polygon cannot overlap itself; skipping the union here is the common case
            // during a walk and saves the whole node-splitting pass.
            if (polygons.size == 1) return FogGeometry(listOf(polygons.first().toRings()))

            val merged = runCatching { UnaryUnionOp.union(polygons) }
                .recoverCatching {
                    // Self-intersections and near-duplicate vertices are normal in generated
                    // rings. GeometryFixer is slower, so it is the fallback, not the default.
                    UnaryUnionOp.union(polygons.map { GeometryFixer.fix(it) })
                }
                .getOrNull()
                ?: return FogGeometry(polygons.map { it.toRings() })

            return FogGeometry(merged.polygons().map { it.toRings() })
        }

        /** Convenience for sources that are plain outlines with no holes: circles, region rings. */
        fun unionRings(rings: List<Ring>): FogGeometry = union(rings.map(::listOf))

        /**
         * One input polygon becomes one JTS polygon — or several, when repairing it splits it up.
         *
         * A self-intersecting ring (a figure of eight traced by a noisy GPS, say) is not garbage:
         * [GeometryFixer] turns it into a valid **MultiPolygon**. Casting that to `Polygon` would
         * silently drop the shape, which is how a revealed area disappears from the map.
         */
        private fun toPolygons(rings: List<Ring>): List<Polygon> {
            val exterior = rings.firstOrNull()?.let(::toLinearRing) ?: return emptyList()
            val holes = rings.drop(1).mapNotNull(::toLinearRing).toTypedArray()

            val polygon = factory.createPolygon(exterior, holes)
            if (polygon.isValid) return listOf(polygon)
            return runCatching { GeometryFixer.fix(polygon).polygons() }.getOrDefault(emptyList())
        }

        private fun toLinearRing(ring: Ring): LinearRing? {
            if (ring.size < MIN_RING_POINTS) return null
            val closed = if (ring.first() == ring.last()) ring else ring + ring.first()
            if (closed.size < MIN_CLOSED_POINTS) return null

            val coordinates = Array(closed.size) { i ->
                val (lng, lat) = closed[i]
                Coordinate(lng, lat)
            }
            if (!coordinates.spansSanely()) return null
            return runCatching { factory.createLinearRing(coordinates) }.getOrNull()
        }

        /**
         * Rejects rings that wrap the antimeridian.
         *
         * Such a ring is not wrong, it is ambiguous: as flat degrees it reads as a band stretching
         * the long way round the planet, and unioning it would swallow everything in between.
         * Dropping it loses one shape; keeping it would lose the map.
         */
        private fun Array<Coordinate>.spansSanely(): Boolean {
            var min = Double.MAX_VALUE
            var max = -Double.MAX_VALUE
            for (c in this) {
                if (c.x < min) min = c.x
                if (c.x > max) max = c.x
            }
            return max - min <= MAX_SPAN_DEGREES
        }

        private fun Geometry.polygons(): List<Polygon> = when (this) {
            is Polygon -> if (isEmpty) emptyList() else listOf(this)
            else -> (0 until numGeometries).mapNotNull { getGeometryN(it) as? Polygon }
                .filterNot { it.isEmpty }
        }

        /** JTS polygon → `[exterior, hole, hole, …]`, each as `[lng, lat]` pairs. */
        private fun Polygon.toRings(): List<Ring> {
            val rings = ArrayList<Ring>(numInteriorRing + 1)
            rings += exteriorRing.toRing()
            for (i in 0 until numInteriorRing) {
                rings += getInteriorRingN(i).toRing()
            }
            return rings
        }

        private fun LinearRing.toRing(): Ring = coordinates.map { it.x to it.y }

        private companion object {
            /**
             * Degrees of precision kept when snapping coordinates.
             *
             * A fixed grid is what makes the union robust: floating-point rings that *almost*
             * touch produce slivers and self-intersections, and a sliver is a hairline of fog
             * through a revealed area. 1e-9 degrees is about 0.1 mm — far below anything the map
             * can show, and coarse enough to make near-identical vertices identical.
             */
            const val PRECISION_SCALE = 1e9

            /** Below this a ring is a point or a line — nothing to reveal. */
            const val MIN_RING_POINTS = 3
            const val MIN_CLOSED_POINTS = 4

            /** No legitimate reveal — not even Russia as one ring — is wider than this. */
            const val MAX_SPAN_DEGREES = 180.0
        }

        private val factory = GeometryFactory(PrecisionModel(PRECISION_SCALE))
    }
