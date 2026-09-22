package com.tripex.pose.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailCorridorTest {

    private val lat = 52.0
    private val lng = 21.0

    @Test
    fun `fixes closer than one step need no filling`() {
        val steps = TrailCorridor.stepsBetween(lat, lng, lat + 0.001, lng)

        assertTrue(steps.isEmpty())
    }

    /**
     * The photographed failure: a drive came out as separate dots. Consecutive stamps must be no
     * further apart than half a radius, so their disks overlap into a band.
     */
    @Test
    fun `a three kilometre gap is filled with overlapping stamps`() {
        val toLat = lat + 3_000.0 / 111_320.0

        val steps = TrailCorridor.stepsBetween(lat, lng, toLat, lng)

        assertTrue("expected the gap to be filled", steps.isNotEmpty())
        val points = listOf(lat to lng) + steps + (toLat to lng)
        for (i in 0 until points.size - 1) {
            val gap = TrailCorridor.distanceMeters(
                points[i].first, points[i].second,
                points[i + 1].first, points[i + 1].second,
            )
            assertTrue(
                "stamps $i and ${i + 1} are $gap m apart — disks would not overlap",
                gap <= H3Config.WALK_REVEAL_RADIUS_M,
            )
        }
    }

    @Test
    fun `a gap beyond the honest limit is left as a gap`() {
        val toLat = lat + (H3Config.MAX_BRIDGE_DISTANCE_M + 1_000.0) / 111_320.0

        val steps = TrailCorridor.stepsBetween(lat, lng, toLat, lng)

        assertTrue("a corridor here would claim unvisited ground", steps.isEmpty())
    }

    @Test
    fun `step count is capped so a long gap cannot balloon`() {
        val toLat = lat + (H3Config.MAX_BRIDGE_DISTANCE_M - 100.0) / 111_320.0

        val steps = TrailCorridor.stepsBetween(lat, lng, toLat, lng)

        assertTrue(steps.size <= H3Config.MAX_BRIDGE_STEPS)
    }

    @Test
    fun `stamps lie on the segment between the fixes`() {
        val toLat = lat + 0.05
        val toLng = lng + 0.05

        val steps = TrailCorridor.stepsBetween(lat, lng, toLat, toLng)

        for ((sLat, sLng) in steps) {
            assertTrue(sLat in lat..toLat)
            assertTrue(sLng in lng..toLng)
            // Straight line: the two offsets advance together.
            assertEquals((sLat - lat), (sLng - lng), 1e-9)
        }
    }

    @Test
    fun `longitude distance shrinks towards the poles`() {
        val atEquator = TrailCorridor.distanceMeters(0.0, 0.0, 0.0, 1.0)
        val atSixty = TrailCorridor.distanceMeters(60.0, 0.0, 60.0, 1.0)

        assertEquals(atEquator / 2.0, atSixty, atEquator * 0.01)
    }
}
