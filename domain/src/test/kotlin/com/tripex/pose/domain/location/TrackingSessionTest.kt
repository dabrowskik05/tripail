package com.tripex.pose.domain.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackingSessionTest {

    private val fix = TrackingSession.Fix(latitude = 52.23, longitude = 21.01, atMs = 0L)

    @Test
    fun `a fresh fix is bridgeable`() {
        val session = TrackingSession(lastFix = fix)

        assertEquals(fix, session.bridgeableFix(nowMs = 5 * 60 * 1000L))
    }

    @Test
    fun `a fix exactly at the age limit is still bridgeable`() {
        val session = TrackingSession(lastFix = fix)

        assertEquals(fix, session.bridgeableFix(nowMs = LocationConfig.BRIDGE_MAX_AGE_MS))
    }

    /**
     * The invariant this type exists for: a gap in the trail is acceptable, a straight corridor
     * of unlocked cells through places nobody visited is not.
     */
    @Test
    fun `a stale fix is not bridgeable`() {
        val session = TrackingSession(lastFix = fix)

        assertNull(session.bridgeableFix(nowMs = LocationConfig.BRIDGE_MAX_AGE_MS + 1))
    }

    @Test
    fun `an empty session has nothing to bridge to`() {
        assertNull(TrackingSession.EMPTY.bridgeableFix(nowMs = 0L))
    }
}
