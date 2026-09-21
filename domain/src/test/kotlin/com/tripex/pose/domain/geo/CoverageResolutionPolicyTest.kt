package com.tripex.pose.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverageResolutionPolicyTest {

    @Test
    fun `small countries use the finest coverage resolution`() {
        // Poland ~312 000 km² sits just above the threshold; Portugal ~92 000 km² below it.
        assertEquals(CoverageResolutionPolicy.RESOLUTION_SMALL, CoverageResolutionPolicy.resolutionFor(92_000.0))
        assertEquals(CoverageResolutionPolicy.RESOLUTION_SMALL, CoverageResolutionPolicy.resolutionFor(249_999.0))
    }

    @Test
    fun `mid-sized countries drop one level`() {
        assertEquals(CoverageResolutionPolicy.RESOLUTION_LARGE, CoverageResolutionPolicy.resolutionFor(250_000.0))
        assertEquals(CoverageResolutionPolicy.RESOLUTION_LARGE, CoverageResolutionPolicy.resolutionFor(1_999_999.0))
    }

    @Test
    fun `the largest countries drop two levels`() {
        // Canada ~9 985 000 km² is still measured as a country.
        assertEquals(CoverageResolutionPolicy.RESOLUTION_HUGE, CoverageResolutionPolicy.resolutionFor(2_000_000.0))
        assertEquals(CoverageResolutionPolicy.RESOLUTION_HUGE, CoverageResolutionPolicy.resolutionFor(9_985_000.0))
    }

    @Test
    fun `continents drop one level further`() {
        // Russia ~17 100 000 km² and Asia ~44 000 000 km² both land in the continent tier.
        assertEquals(
            CoverageResolutionPolicy.RESOLUTION_CONTINENT,
            CoverageResolutionPolicy.resolutionFor(17_100_000.0),
        )
        assertEquals(
            CoverageResolutionPolicy.RESOLUTION_CONTINENT,
            CoverageResolutionPolicy.resolutionFor(44_000_000.0),
        )
    }

    @Test
    fun `resolution never rises as the area grows`() {
        val areas = generateSequence(1_000.0) { it * 1.7 }.takeWhile { it < 50_000_000.0 }.toList()

        val resolutions = areas.map { CoverageResolutionPolicy.resolutionFor(it) }

        assertTrue(
            "resolution must be monotonically non-increasing: $resolutions",
            resolutions.zipWithNext().all { (a, b) -> b <= a },
        )
    }

    @Test
    fun `degenerate areas still yield a usable resolution`() {
        assertEquals(CoverageResolutionPolicy.RESOLUTION_SMALL, CoverageResolutionPolicy.resolutionFor(0.0))
    }
}
