package com.tripex.pose.domain.geo

import com.tripex.pose.domain.location.TrackingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinentFocusPolicyTest {

    private val europe = ContinentBounds.region(ContinentId.Europe).bounds
    private val now = 1_700_000_000_000L

    private fun fix(lat: Double, lng: Double, atMs: Long = now) =
        TrackingSession.Fix(latitude = lat, longitude = lng, atMs = atMs)

    @Test
    fun `no fix means the continent framing stands`() {
        assertNull(ContinentFocusPolicy.focus(europe, fix = null, nowMs = now))
    }

    @Test
    fun `a player on the continent is flown to`() {
        val focus = ContinentFocusPolicy.focus(europe, fix(52.0, 21.0), now)

        assertNotNull(focus)
        assertTrue(focus!!.north > 52.0 && focus.south < 52.0)
        assertTrue(focus.east > 21.0 && focus.west < 21.0)
    }

    @Test
    fun `a player on another continent is left alone`() {
        // Buenos Aires, while looking at Europe.
        assertNull(ContinentFocusPolicy.focus(europe, fix(-34.6, -58.4), now))
    }

    /** Yesterday's city is a confident wrong answer — worse than no answer. */
    @Test
    fun `a stale fix is ignored`() {
        val old = now - ContinentFocusPolicy.MAX_FIX_AGE_MS - 1

        assertNull(ContinentFocusPolicy.focus(europe, fix(52.0, 21.0, atMs = old), now))
    }

    @Test
    fun `a fix right at the age limit still counts`() {
        val borderline = now - ContinentFocusPolicy.MAX_FIX_AGE_MS

        assertNotNull(ContinentFocusPolicy.focus(europe, fix(52.0, 21.0, atMs = borderline), now))
    }

    /** A square on screen needs a wider box in degrees the further from the equator it is. */
    @Test
    fun `the focus box widens towards the poles`() {
        val warsaw = ContinentFocusPolicy.focus(europe, fix(52.0, 21.0), now)!!
        val tromso = ContinentFocusPolicy.focus(europe, fix(69.6, 21.0), now)!!

        assertTrue(
            (tromso.east - tromso.west) > (warsaw.east - warsaw.west),
        )
    }

    @Test
    fun `the camera limit is wider than the continent it guards`() {
        val limit = ContinentFocusPolicy.cameraLimit(europe)

        assertTrue(limit.north > europe.north)
        assertTrue(limit.south < europe.south)
        assertTrue(limit.east > europe.east)
        assertTrue(limit.west < europe.west)
    }

    @Test
    fun `the limit margin scales with the continent, not in fixed degrees`() {
        val small = GeoBounds(north = 10.0, south = 0.0, east = 10.0, west = 0.0)
        val large = GeoBounds(north = 80.0, south = 0.0, east = 100.0, west = 0.0)

        val smallMargin = ContinentFocusPolicy.cameraLimit(small).north - small.north
        val largeMargin = ContinentFocusPolicy.cameraLimit(large).north - large.north

        assertTrue(largeMargin > smallMargin)
    }

    @Test
    fun `limits never escape the projection`() {
        val antarctica = ContinentBounds.region(ContinentId.Antarctica).bounds

        val limit = ContinentFocusPolicy.cameraLimit(antarctica)

        assertEquals(-85.0, limit.south, 1e-9)
        assertTrue(limit.east <= 180.0)
        assertTrue(limit.west >= -180.0)
    }
}
