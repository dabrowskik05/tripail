package com.tripex.pose.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Test

class ContinentBoundsTest {

    @Test
    fun `a real atlas outline is preferred over the hand-written constant`() {
        val outline = GeoBounds(north = 71.0, south = 35.0, east = 39.0, west = -24.0)

        assertEquals(outline, ContinentBounds.framing(ContinentId.Europe, outline))
    }

    @Test
    fun `an antimeridian-wide outline falls back to the curated box`() {
        // What the atlas reports for Asia: land on both sides of 180° flattens into a world-wide
        // bbox, and framing it would put the camera on the whole planet.
        val artefact = GeoBounds(north = 78.0, south = -11.0, east = 180.0, west = -180.0)

        assertEquals(
            ContinentBounds.region(ContinentId.Asia).bounds,
            ContinentBounds.framing(ContinentId.Asia, artefact),
        )
    }

    @Test
    fun `a missing outline falls back to the curated box`() {
        assertEquals(
            ContinentBounds.region(ContinentId.Oceania).bounds,
            ContinentBounds.framing(ContinentId.Oceania, atlasBounds = null),
        )
    }
}
