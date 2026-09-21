package com.tripex.pose.domain.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DwellDetectorTest {

    private val detector = DwellDetector()

    /** Centre of Warsaw; every offset below is measured from here. */
    private val lat = 52.2297
    private val lng = 21.0122

    @Test
    fun `first fix only anchors`() {
        val (state, dwelled) = detector.update(null, lat, lng, nowMs = 0)

        assertFalse(dwelled)
        assertEquals(lat, state.anchorLat, 1e-9)
        assertEquals(0L, state.sinceMs)
    }

    @Test
    fun `staying put long enough reports a dwell exactly once`() {
        val (anchored, _) = detector.update(null, lat, lng, nowMs = 0)

        val (afterMinute, tooEarly) = detector.update(anchored, lat, lng, 60_000)
        assertFalse(tooEarly)

        val (reported, dwelled) = detector.update(
            afterMinute,
            lat,
            lng,
            DwellDetector.DWELL_MILLIS,
        )
        assertTrue(dwelled)
        assertTrue(reported.reported)

        // The city is already claimed by now — repeating it every fix would spam the geocoder.
        val (_, again) = detector.update(reported, lat, lng, DwellDetector.DWELL_MILLIS * 2)
        assertFalse(again)
    }

    @Test
    fun `walking out of the neighbourhood restarts the timer`() {
        val (anchored, _) = detector.update(null, lat, lng, nowMs = 0)

        // ~2.2 km north: past DWELL_RADIUS_M, so this is travel, not a stay.
        val movedLat = lat + 0.02
        val (moved, dwelled) = detector.update(anchored, movedLat, lng, 120_000)

        assertFalse(dwelled)
        assertEquals(movedLat, moved.anchorLat, 1e-9)
        assertEquals(120_000L, moved.sinceMs)
    }

    @Test
    fun `drifting within the neighbourhood keeps the original anchor`() {
        val (anchored, _) = detector.update(null, lat, lng, nowMs = 0)

        // ~550 m north — a walk around the block must not reset the clock.
        val (drifted, dwelled) = detector.update(anchored, lat + 0.005, lng, 60_000)

        assertFalse(dwelled)
        assertEquals(lat, drifted.anchorLat, 1e-9)
        assertEquals(0L, drifted.sinceMs)
    }

    /**
     * V3.7.3's acceptance criterion: a stop interrupted by the process dying four minutes in
     * completes after five, not after nine. The restored state is the same object the service
     * persisted, which is the whole reason [DwellDetector.State] is plain data.
     */
    @Test
    fun `a dwell interrupted by a restart resumes instead of starting over`() {
        val startedAt = 1_700_000_000_000L

        val (beforeCrash, _) = detector.update(null, lat, lng, nowMs = startedAt)
        val (fourMinutesIn, notYet) = detector.update(
            beforeCrash,
            lat,
            lng,
            nowMs = startedAt + 4 * 60 * 1000,
        )
        assertFalse(notYet)

        // The process dies here; `fourMinutesIn` is what came back off disk.
        val restored = fourMinutesIn.copy()
        val (_, dwelled) = detector.update(
            restored,
            lat,
            lng,
            nowMs = startedAt + DwellDetector.DWELL_MILLIS,
        )

        assertTrue(dwelled)
    }

    @Test
    fun `a clock jumping backwards re-anchors instead of freezing the timer`() {
        val (anchored, _) = detector.update(null, lat, lng, nowMs = 1_000_000)

        // NTP correction, or the user editing the system clock.
        val (reanchored, dwelled) = detector.update(anchored, lat, lng, nowMs = 900_000)

        assertFalse(dwelled)
        assertEquals(900_000L, reanchored.sinceMs)
    }

    @Test
    fun `longitude distance is measured at the anchor latitude`() {
        // 0.02 deg of longitude is ~2.2 km at the equator but only ~1.1 km at 60 N; at Tromso it
        // must therefore still count as staying put.
        val north = 69.6
        val (anchored, _) = detector.update(null, north, 18.95, nowMs = 0)

        val (drifted, _) = detector.update(anchored, north, 18.97, 60_000)

        assertEquals(18.95, drifted.anchorLng, 1e-9)
    }
}
