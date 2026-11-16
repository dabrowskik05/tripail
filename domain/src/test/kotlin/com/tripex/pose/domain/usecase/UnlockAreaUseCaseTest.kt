package com.tripex.pose.domain.usecase

import com.tripex.pose.domain.geo.TrailPoint
import com.tripex.pose.domain.geo.TravelMode
import com.tripex.pose.domain.location.DomainLocation
import com.tripex.pose.domain.location.TrackingSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockAreaUseCaseTest {

    private val h3 = FakeGridH3()

    private fun location(lat: Double, lng: Double) =
        DomainLocation(lat, lng, accuracyMeters = 5f, elapsedRealtimeNanos = 0L, isMock = false)

    private fun fix(lat: Double, lng: Double) = TrackingSession.Fix(lat, lng, 0L)

    @Test
    fun `the first fix stores one point and opens a segment`() = runTest {
        val repo = FakeUnlockedAreaRepository()
        val useCase = UnlockAreaUseCase(h3, repo)

        assertEquals(1, useCase(location(52.0, 21.0), bridgeFrom = null, atMs = 1_000L))

        val point = repo.points.single()
        assertEquals(TravelMode.WALK, point.mode)
        assertTrue(point.segmentId > 0)
    }

    @Test
    fun `consecutive fixes share one segment`() = runTest {
        val repo = FakeUnlockedAreaRepository()
        val useCase = UnlockAreaUseCase(h3, repo)

        useCase(location(52.000, 21.0), bridgeFrom = null, atMs = 1_000L)
        useCase(location(52.001, 21.0), bridgeFrom = fix(52.000, 21.0), atMs = 2_000L)
        useCase(location(52.002, 21.0), bridgeFrom = fix(52.001, 21.0), atMs = 3_000L)

        assertEquals(3, repo.points.size)
        assertEquals(1, repo.points.map { it.segmentId }.distinct().size)
    }

    @Test
    fun `standing still stores nothing`() = runTest {
        val repo = FakeUnlockedAreaRepository()
        val useCase = UnlockAreaUseCase(h3, repo)

        useCase(location(52.0, 21.0), bridgeFrom = null, atMs = 1_000L)
        val second = useCase(location(52.0001, 21.0), bridgeFrom = fix(52.0, 21.0), atMs = 2_000L)

        assertEquals(0, second)
        assertEquals(1, repo.points.size)
    }

    @Test
    fun `a jump too long to vouch for starts a new segment`() = runTest {
        val repo = FakeUnlockedAreaRepository()
        val useCase = UnlockAreaUseCase(h3, repo)

        useCase(location(52.0, 21.0), bridgeFrom = null, atMs = 1_000L)
        // ~110 km north, far past the bridge limit.
        useCase(location(53.0, 21.0), bridgeFrom = fix(52.0, 21.0), atMs = 2_000L)

        assertEquals(2, repo.points.size)
        assertNotEquals(repo.points[0].segmentId, repo.points[1].segmentId)
    }

    @Test
    fun `a mode change opens a new segment that starts where the old one ended`() = runTest {
        val repo = FakeUnlockedAreaRepository()
        val useCase = UnlockAreaUseCase(h3, repo)

        useCase(location(52.000, 21.0), bridgeFrom = null, mode = TravelMode.WALK, atMs = 1_000L)
        useCase(location(52.004, 21.0), bridgeFrom = fix(52.000, 21.0), mode = TravelMode.VEHICLE, atMs = 2_000L)

        val vehicle = repo.points.filter { it.mode == TravelMode.VEHICLE }
        assertEquals(2, vehicle.size)
        assertEquals(1, vehicle.map { it.segmentId }.distinct().size)
        assertEquals(52.000, vehicle.first().latitude, 1e-9)
        assertNotEquals(repo.points.first().segmentId, vehicle.first().segmentId)
    }

    @Test
    fun `a restarted process continues from the remembered fix`() = runTest {
        val repo = FakeUnlockedAreaRepository()
        UnlockAreaUseCase(h3, repo)(location(52.000, 21.0), bridgeFrom = null, atMs = 1_000L)

        // New instance = process was killed; the session still remembers the last fix.
        UnlockAreaUseCase(h3, repo)(location(52.003, 21.0), bridgeFrom = fix(52.000, 21.0), atMs = 2_000L)

        val second = repo.points.filter { it.segmentId == 2_000L }
        assertEquals("the new segment starts at the old fix", listOf(52.000, 52.003), second.map { it.latitude })
    }

    @Test
    fun `known ground is not stored again but the join points are`() = runTest {
        val known = h3.cellAt(52.002, 21.0)
        val repo = FakeUnlockedAreaRepository(
            listOf(known to TrailPoint(52.002, 21.0, TravelMode.WALK, 5L, 0L)),
        )
        val useCase = UnlockAreaUseCase(h3, repo)

        assertEquals(1, useCase(location(52.001, 21.0), bridgeFrom = null, atMs = 1_000L))
        assertEquals(0, useCase(location(52.002, 21.0), bridgeFrom = fix(52.001, 21.0), atMs = 2_000L))
        assertEquals(1, useCase(location(52.003, 21.0), bridgeFrom = fix(52.002, 21.0), atMs = 3_000L))

        // Before: one old point. The entry closes the first line, the exit opens the second one.
        val incoming = repo.points.filter { it.segmentId == 1_000L }.map { it.latitude }
        val outgoing = repo.points.filter { it.segmentId == 3_000L }.map { it.latitude }
        assertEquals(listOf(52.001, 52.002), incoming)
        assertEquals(listOf(52.002, 52.003), outgoing)
    }

    @Test
    fun `driven ground is not known to a walker`() = runTest {
        val driven = h3.cellAt(52.0, 21.0)
        val repo = FakeUnlockedAreaRepository(
            listOf(driven to TrailPoint(52.0, 21.0, TravelMode.VEHICLE, 5L, 0L)),
        )

        assertEquals(1, UnlockAreaUseCase(h3, repo)(location(52.0, 21.0), bridgeFrom = null, atMs = 1_000L))
    }

    @Test
    fun `single points are stand-alone and deduplicated`() = runTest {
        val repo = FakeUnlockedAreaRepository()
        val useCase = UnlockAreaUseCase(h3, repo)

        assertEquals(1, useCase.single(52.0, 21.0, atMs = 1L))
        assertEquals(0, useCase.single(52.0001, 21.0, atMs = 2L))
        assertEquals(TrailPoint.SEGMENT_SINGLE, repo.points.single().segmentId)
    }

    @Test
    fun `import drops duplicates of each other and of the trail`() = runTest {
        val repo = FakeUnlockedAreaRepository()
        val useCase = UnlockAreaUseCase(h3, repo)
        useCase.single(52.0, 21.0, atMs = 1L)

        val added = useCase.import(
            listOf(Triple(52.0, 21.0, 2L), Triple(48.0, 2.0, 3L), Triple(48.0001, 2.0, 4L)),
        )

        assertEquals(1, added)
        assertEquals(2, repo.points.size)
    }
}
