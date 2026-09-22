package com.tripex.pose.domain.geo

import com.tripex.pose.domain.geo.atlas.Ring
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RevealUnionTest {

    private val union = RevealUnion()

    @Test
    fun `nothing revealed is nothing to draw`() {
        assertEquals(FogGeometry.EMPTY, union.unionRings(emptyList()))
    }

    @Test
    fun `two separate shapes stay two polygons`() {
        val result = union.unionRings(listOf(square(0.0, 0.0, 1.0), square(10.0, 10.0, 1.0)))

        assertEquals(2, result.polygons.size)
    }

    @Test
    fun `a shape inside another collapses to one`() {
        val result = union.unionRings(listOf(square(0.0, 0.0, 10.0), square(2.0, 2.0, 1.0)))

        assertEquals(1, result.polygons.size)
        assertEquals(100.0, result.polygons.first().first().area(), 1e-6)
    }

    /**
     * The photographed bug: a city circle overlapping the walked trail turned the overlap back
     * into fog. After the union there is one shape, so there is no overlap left to mis-render.
     */
    @Test
    fun `overlapping shapes merge instead of cancelling out`() {
        val result = union.unionRings(listOf(square(0.0, 0.0, 2.0), square(1.0, 1.0, 2.0)))

        assertEquals(1, result.polygons.size)
        val area = result.polygons.first().first().area()
        // Two 2x2 squares overlapping on a 1x1 corner: 4 + 4 - 1.
        assertEquals(7.0, area, 1e-6)
    }

    @Test
    fun `a circle overlapping an irregular polygon yields a single ring`() {
        val circle = GeoCircle.ring(lat = 51.95, lng = 20.15, radiusMeters = 5_000.0)
        val blob = listOf(
            20.10 to 51.96,
            20.22 to 51.99,
            20.26 to 51.94,
            20.16 to 51.91,
            20.10 to 51.96,
        )

        val result = union.unionRings(listOf(circle, blob))

        assertEquals(1, result.polygons.size)
        assertTrue(result.polygons.first().isNotEmpty())
    }

    /** Order must not change the picture — otherwise the map depends on Room's row order. */
    @Test
    fun `union is order independent`() {
        val a = square(0.0, 0.0, 2.0)
        val b = square(1.0, 1.0, 2.0)
        val c = square(5.0, 5.0, 1.0)

        val one = union.unionRings(listOf(a, b, c)).totalArea()
        val two = union.unionRings(listOf(c, b, a)).totalArea()

        assertEquals(one, two, 1e-9)
    }

    @Test
    fun `a ring around a gap keeps the gap as a hole`() {
        // Four bars forming a closed frame: the middle is never revealed.
        val result = union.unionRings(
            listOf(
                rect(0.0, 0.0, 10.0, 1.0),
                rect(0.0, 9.0, 10.0, 1.0),
                rect(0.0, 0.0, 1.0, 10.0),
                rect(9.0, 0.0, 1.0, 10.0),
            ),
        )

        assertEquals(1, result.polygons.size)
        assertEquals("exterior + one hole", 2, result.polygons.first().size)
    }

    @Test
    fun `degenerate rings are dropped rather than breaking the union`() {
        val result = union.unionRings(
            listOf(
                square(0.0, 0.0, 1.0),
                listOf(5.0 to 5.0, 5.0 to 5.0),
                emptyList(),
            ),
        )

        assertEquals(1, result.polygons.size)
    }

    @Test
    fun `a ring wrapping the antimeridian is dropped, not smeared across the globe`() {
        val wrapping = listOf(
            179.0 to 10.0,
            -179.0 to 10.0,
            -179.0 to 11.0,
            179.0 to 11.0,
            179.0 to 10.0,
        )

        val result = union.unionRings(listOf(square(0.0, 0.0, 1.0), wrapping))

        assertEquals(1, result.polygons.size)
        assertEquals(1.0, result.polygons.first().first().area(), 1e-6)
    }

    @Test
    fun `self-intersecting input is repaired instead of thrown away`() {
        // Bow-tie: invalid as a polygon, fixable into two triangles.
        val bowTie = listOf(
            0.0 to 0.0,
            2.0 to 2.0,
            2.0 to 0.0,
            0.0 to 2.0,
            0.0 to 0.0,
        )

        val result = union.unionRings(listOf(bowTie, square(10.0, 10.0, 1.0)))

        assertTrue("expected the bow-tie to survive as geometry", result.totalArea() > 1.0)
    }

    /**
     * A walked loop leaves an unvisited middle. Flattening the shape to its exterior would
     * reveal ground nobody went to, so the hole has to survive the union.
     */
    @Test
    fun `a hole in an input polygon survives the union`() {
        val donut = listOf(square(0.0, 0.0, 10.0), square(4.0, 4.0, 2.0))

        val result = union.union(listOf(donut))

        assertEquals(1, result.polygons.size)
        assertEquals(2, result.polygons.first().size)
        assertEquals(96.0, result.totalArea(), 1e-6)
    }

    @Test
    fun `a shape covering the hole fills it in`() {
        val donut = listOf(square(0.0, 0.0, 10.0), square(4.0, 4.0, 2.0))

        val result = union.union(listOf(donut, listOf(square(3.0, 3.0, 4.0))))

        assertEquals(1, result.polygons.size)
        assertEquals("the hole is covered, so it is no longer a hole", 1, result.polygons.first().size)
        assertEquals(100.0, result.totalArea(), 1e-6)
    }

    // --- helpers ---

    private fun square(x: Double, y: Double, size: Double): Ring = rect(x, y, size, size)

    private fun rect(x: Double, y: Double, w: Double, h: Double): Ring = listOf(
        x to y,
        x + w to y,
        x + w to y + h,
        x to y + h,
        x to y,
    )

    /** Shoelace, absolute — rings may come back in either winding order. */
    private fun Ring.area(): Double {
        var sum = 0.0
        for (i in 0 until size - 1) {
            val (x1, y1) = this[i]
            val (x2, y2) = this[i + 1]
            sum += x1 * y2 - x2 * y1
        }
        return abs(sum) / 2.0
    }

    private fun FogGeometry.totalArea(): Double =
        polygons.sumOf { rings ->
            rings.first().area() - rings.drop(1).sumOf { it.area() }
        }
}
