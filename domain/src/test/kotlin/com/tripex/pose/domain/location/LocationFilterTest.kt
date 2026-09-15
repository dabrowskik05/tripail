package com.tripex.pose.domain.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationFilterTest {

    private val filter = LocationFilter()
    private val now = 60_000_000_000L // 60s in nanos

    @Test
    fun `accepts accurate fresh fix`() {
        assertTrue(filter.shouldAccept(fix(accuracy = 10f), lastAccepted = null, now))
    }

    @Test
    fun `accepts accuracy just under threshold`() {
        assertTrue(filter.shouldAccept(fix(accuracy = 49f), lastAccepted = null, now))
    }

    @Test
    fun `rejects accuracy above threshold`() {
        assertFalse(filter.shouldAccept(fix(accuracy = 51f), lastAccepted = null, now))
        assertFalse(filter.shouldAccept(fix(accuracy = 200f), lastAccepted = null, now))
    }

    @Test
    fun `rejects mock when not allowed`() {
        assertFalse(
            filter.shouldAccept(
                fix(isMock = true),
                lastAccepted = null,
                nowElapsedRealtimeNanos = now,
                allowMock = false,
            ),
        )
    }

    @Test
    fun `accepts mock when allowed`() {
        assertTrue(
            filter.shouldAccept(
                fix(isMock = true),
                lastAccepted = null,
                nowElapsedRealtimeNanos = now,
                allowMock = true,
            ),
        )
    }

    @Test
    fun `rejects stale fix older than 30s`() {
        val stale = fix(elapsed = now - 31_000_000_000L)
        assertFalse(filter.shouldAccept(stale, lastAccepted = null, now))
    }

    @Test
    fun `rejects jitter under 20m`() {
        val previous = fix(lat = 52.2297, lng = 21.0122, elapsed = now - 15_000_000_000L)
        val near = fix(lat = 52.22975, lng = 21.0122, elapsed = now - 1_000_000_000L)
        assertFalse(filter.shouldAccept(near, lastAccepted = previous, now))
    }

    @Test
    fun `accepts move beyond jitter distance`() {
        val previous = fix(lat = 52.2297, lng = 21.0122, elapsed = now - 15_000_000_000L)
        // ~55 m north
        val next = fix(lat = 52.2302, lng = 21.0122, elapsed = now - 1_000_000_000L)
        assertTrue(filter.shouldAccept(next, lastAccepted = previous, now))
    }

    private fun fix(
        lat: Double = 52.2297,
        lng: Double = 21.0122,
        accuracy: Float = 10f,
        elapsed: Long = now - 1_000_000_000L,
        isMock: Boolean = false,
    ): DomainLocation = DomainLocation(
        latitude = lat,
        longitude = lng,
        accuracyMeters = accuracy,
        elapsedRealtimeNanos = elapsed,
        isMock = isMock,
    )
}
