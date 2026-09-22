package com.tripex.pose.ui.continent.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldPanTest {

    // A tall, narrow viewport — the world is wider than the screen, so it can pan.
    private val width = 1_080f
    private val height = 2_400f

    @Test
    fun `the western end maps to zero`() {
        val range = WorldFit.panRange(width, height)

        assertEquals(0f, WorldPan.toFraction(range.endInclusive, width, height), 1e-4f)
    }

    @Test
    fun `the eastern end maps to one`() {
        val range = WorldFit.panRange(width, height)

        assertEquals(1f, WorldPan.toFraction(range.start, width, height), 1e-4f)
    }

    /** Right on the slider must mean east, which is the opposite sign to the pixel offset. */
    @Test
    fun `moving the slider right slides the world east`() {
        val west = WorldPan.toPan(0.2f, width, height)
        val east = WorldPan.toPan(0.8f, width, height)

        assertTrue("east should sit at a smaller left-edge offset", east < west)
    }

    @Test
    fun `fraction and pan round-trip`() {
        for (fraction in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val pan = WorldPan.toPan(fraction, width, height)

            assertEquals(fraction, WorldPan.toFraction(pan, width, height), 1e-4f)
        }
    }

    @Test
    fun `out of range input is clamped rather than flinging the world away`() {
        val range = WorldFit.panRange(width, height)

        val tooFar = WorldPan.toPan(5f, width, height)

        assertTrue(tooFar in range)
    }

    /** A viewport wide enough to hold the whole world has nowhere to pan; the knob sits centred. */
    @Test
    fun `a world that cannot pan reports the centre`() {
        val wide = 4_000f
        val short = 200f

        assertEquals(
            WorldPan.CENTRE,
            WorldPan.toFraction(WorldFit.centeredPan(wide, short), wide, short),
            1e-4f,
        )
    }

    @Test
    fun `a degenerate viewport does not divide by zero`() {
        assertEquals(WorldPan.CENTRE, WorldPan.toFraction(0f, 0f, 0f), 1e-4f)
        assertEquals(0f, WorldPan.toPan(0.5f, 0f, 0f), 1e-4f)
    }
}
