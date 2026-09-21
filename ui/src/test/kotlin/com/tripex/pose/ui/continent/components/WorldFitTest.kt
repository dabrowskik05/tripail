package com.tripex.pose.ui.continent.components

import com.tripex.pose.domain.geo.projection.EquirectangularProjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards Stage A against the failure mode that is invisible from the outside: a canvas that
 * renders its content off-screen, or at zero size, looks exactly like a canvas with no data.
 *
 * The menu **covers** the viewport rather than fitting inside it, so the tests here are about
 * two things: no empty band of ocean anywhere, and a pan that cannot be dragged off the world.
 */
class WorldFitTest {

    private val screenWidth = 1080f
    private val screenHeight = 2200f

    /** Projects a lng/lat into the on-screen pixel the canvas would draw it at. */
    private fun WorldFit.toScreen(lng: Double, lat: Double): Pair<Float, Float> {
        val (x, y) = EquirectangularProjection.lngLatToXy(lng, lat, PATH_WORLD_WIDTH)
        return (offsetX + x.toFloat() * scale) to (offsetY + y.toFloat() * scale)
    }

    @Test
    fun `the world covers the viewport instead of leaving bands of ocean`() {
        val fit = WorldFit.of(screenWidth, screenHeight)

        val (_, top) = fit.toScreen(0.0, EquirectangularProjection.MAX_LAT)
        val (_, bottom) = fit.toScreen(0.0, EquirectangularProjection.MIN_LAT)

        assertTrue("empty band above the world: $top", top <= 0.5f)
        assertTrue("empty band below the world: $bottom", bottom >= screenHeight - 0.5f)
    }

    @Test
    fun `every latitude stays on screen, so Antarctica remains tappable`() {
        val fit = WorldFit.of(screenWidth, screenHeight)

        val (_, top) = fit.toScreen(0.0, EquirectangularProjection.MAX_LAT)
        val (_, bottom) = fit.toScreen(0.0, EquirectangularProjection.MIN_LAT)

        // Covering a portrait screen is driven by height, so the band fits exactly.
        assertEquals(0f, top, 0.5f)
        assertEquals(screenHeight, bottom, 0.5f)
    }

    @Test
    fun `the default position centres the prime meridian`() {
        val fit = WorldFit.of(screenWidth, screenHeight)

        val (x, _) = fit.toScreen(0.0, 0.0)

        assertEquals(screenWidth / 2f, x, 0.5f)
    }

    @Test
    fun `panning is horizontal only and clamps flush against both edges`() {
        val range = WorldFit.panRange(screenWidth, screenHeight)

        val draggedFarLeft = WorldFit.of(screenWidth, screenHeight, pan = -100_000f)
        val draggedFarRight = WorldFit.of(screenWidth, screenHeight, pan = 100_000f)

        assertEquals(range.start, draggedFarLeft.offsetX, 0.5f)
        assertEquals(range.endInclusive, draggedFarRight.offsetX, 0.5f)
        // Flush right means the world's left edge is at x = 0: no ocean gap on either side.
        assertEquals(0f, draggedFarRight.offsetX, 0.5f)
        val (dateline, _) = draggedFarLeft.toScreen(180.0, 0.0)
        assertEquals(screenWidth, dateline, 0.5f)
        assertEquals(
            "panning must not change the vertical placement",
            draggedFarLeft.offsetY,
            draggedFarRight.offsetY,
            0.001f,
        )
    }

    @Test
    fun `a portrait phone has room to pan, a very wide one does not`() {
        val portrait = WorldFit.panRange(screenWidth, screenHeight)
        assertTrue("expected pan room on a phone", portrait.endInclusive > portrait.start)

        // Wider than the world's own 2.07:1 aspect: there is nothing left to slide.
        val ultraWide = WorldFit.panRange(3000f, 1080f)
        assertEquals(ultraWide.start, ultraWide.endInclusive, 0.5f)
    }

    @Test
    fun `screen point maps back to the coordinate it was drawn from, panned or not`() {
        val warsaw = 21.0 to 52.0

        for (pan in listOf(null, -2_000f, 0f)) {
            val fit = WorldFit.of(screenWidth, screenHeight, pan)
            val (x, y) = fit.toScreen(warsaw.first, warsaw.second)
            val (lng, lat) = fit.toLngLat(x, y)

            assertEquals(warsaw.first, lng, 0.1)
            assertEquals(warsaw.second, lat, 0.1)
        }
    }

    @Test
    fun `a viewport with no size yields no scale instead of a division by zero`() {
        val fit = WorldFit.of(0f, 0f)

        assertEquals(0f, fit.scale, 0.0001f)
        assertEquals(0.0 to 0.0, fit.toLngLat(10f, 10f))
        assertEquals(0f, WorldFit.centeredPan(0f, 0f), 0.0001f)
    }
}
