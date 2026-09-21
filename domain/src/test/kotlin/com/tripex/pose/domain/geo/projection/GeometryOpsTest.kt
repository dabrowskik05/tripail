package com.tripex.pose.domain.geo.projection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryOpsTest {

    private val unitSquare = listOf(
        0.0 to 0.0,
        1.0 to 0.0,
        1.0 to 1.0,
        0.0 to 1.0,
        0.0 to 0.0,
    )

    @Test
    fun `point inside ring`() {
        assertTrue(GeometryOps.pointInRing(unitSquare, 0.5, 0.5))
    }

    @Test
    fun `point outside ring`() {
        assertFalse(GeometryOps.pointInRing(unitSquare, 1.5, 0.5))
    }

    @Test
    fun `point on edge counts as inside`() {
        assertTrue(GeometryOps.pointInRing(unitSquare, 0.5, 0.0))
    }

    @Test
    fun `point in polygon respects hole`() {
        val hole = listOf(
            0.25 to 0.25,
            0.75 to 0.25,
            0.75 to 0.75,
            0.25 to 0.75,
            0.25 to 0.25,
        )
        val polygon = listOf(unitSquare, hole)
        assertTrue(GeometryOps.pointInPolygon(polygon, 0.1, 0.1))
        assertFalse(GeometryOps.pointInPolygon(polygon, 0.5, 0.5))
    }

    @Test
    fun `chaikin preserves closed ring`() {
        val smoothed = GeometryOps.chaikin(unitSquare, iterations = 2)
        assertEquals(smoothed.first(), smoothed.last())
        assertTrue(smoothed.size > unitSquare.size)
    }

    @Test
    fun `simplify keeps endpoints of closed ring`() {
        val dense = listOf(
            0.0 to 0.0,
            0.25 to 0.01,
            0.5 to 0.0,
            0.75 to -0.01,
            1.0 to 0.0,
            1.0 to 1.0,
            0.0 to 1.0,
            0.0 to 0.0,
        )
        val simplified = GeometryOps.simplify(dense, tolerance = 0.05)
        assertEquals(dense.first(), simplified.first())
        assertEquals(dense.last(), simplified.last())
        assertTrue(simplified.size < dense.size)
    }

    @Test
    fun `boundsOf and centroidOf`() {
        val bounds = GeometryOps.boundsOf(listOf(unitSquare))
        assertEquals(1.0, bounds.north, 1e-9)
        assertEquals(0.0, bounds.south, 1e-9)
        assertEquals(1.0, bounds.east, 1e-9)
        assertEquals(0.0, bounds.west, 1e-9)
        val (lng, lat) = GeometryOps.centroidOf(bounds)
        assertEquals(0.5, lng, 1e-9)
        assertEquals(0.5, lat, 1e-9)
    }

    @Test
    fun `mainlandBounds frames the main landmass and ignores far-flung territory`() {
        // A compact mainland plus one tiny island on the other side of the planet — the shape of
        // France, Norway and every other country that used to throw the camera into the ocean.
        val mainland = listOf(
            2.0 to 51.0,
            8.0 to 51.0,
            8.0 to 43.0,
            2.0 to 43.0,
            2.0 to 51.0,
        )
        val overseasIslet = listOf(
            165.0 to -21.0,
            166.0 to -21.0,
            166.0 to -22.0,
            165.0 to -22.0,
            165.0 to -21.0,
        )

        val bounds = GeometryOps.mainlandBounds(listOf(overseasIslet, mainland))

        assertEquals(51.0, bounds.north, 1e-9)
        assertEquals(43.0, bounds.south, 1e-9)
        assertEquals(8.0, bounds.east, 1e-9)
        assertEquals(2.0, bounds.west, 1e-9)
    }

    @Test
    fun `mainlandBounds falls back to the full extent when no ring is usable`() {
        val degenerate = listOf(listOf(2.0 to 51.0, 8.0 to 43.0))

        val bounds = GeometryOps.mainlandBounds(degenerate)

        assertEquals(51.0, bounds.north, 1e-9)
        assertEquals(43.0, bounds.south, 1e-9)
    }

    @Test
    fun `areaKm2 of small bbox is positive`() {
        val bounds = GeometryOps.boundsOf(listOf(unitSquare))
        assertTrue(GeometryOps.areaKm2(bounds) > 0.0)
    }
}
