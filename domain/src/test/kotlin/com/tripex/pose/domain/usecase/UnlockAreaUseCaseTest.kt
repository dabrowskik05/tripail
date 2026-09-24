package com.tripex.pose.domain.usecase

import com.tripex.pose.domain.geo.FogGeometry
import com.tripex.pose.domain.geo.H3Config
import com.tripex.pose.domain.geo.H3Converter
import com.tripex.pose.domain.location.DomainLocation
import com.tripex.pose.domain.location.TrackingSession
import com.tripex.pose.domain.repository.UnlockedAreaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockAreaUseCaseTest {
    @Test
    fun `first fix unlocks reveal disk of 7 cells`() =
        runTest {
            val h3 = FakeH3Converter(diskSize = 7)
            val repo = FakeUnlockedAreaRepository()
            val useCase = UnlockAreaUseCase(h3, repo)

            val unlocked = useCase(location(1.0, 1.0), bridgeFrom = null)

            assertEquals(7, unlocked)
            assertEquals(7, repo.unlocked.size)
            assertTrue(h3.bridgeCalls.isEmpty())
        }

    /**
     * The gap is filled by sweeping the reveal disk along it, not by threading single cells
     * between the endpoints — a 25 m thread between 2 km disks photographed as separate dots.
     */
    @Test
    fun `gap filling stamps overlapping disks along the segment`() =
        runTest {
            val h3 = FakeH3Converter(diskSize = 7)
            val repo = FakeUnlockedAreaRepository()
            val useCase = UnlockAreaUseCase(h3, repo)

            useCase(location(1.0, 1.0), bridgeFrom = null)
            h3.revealCalls.clear()
            useCase(location(1.1, 1.1), bridgeFrom = fix(1.0, 1.0))

            // The fix itself plus one stamp per interpolated step.
            assertTrue("expected corridor stamps, got ${h3.revealCalls.size}", h3.revealCalls.size > 1)
            assertTrue(h3.bridgeCalls.isEmpty())
        }

    @Test
    fun `a gap too long to vouch for is left unbridged`() =
        runTest {
            val h3 = FakeH3Converter(diskSize = 7)
            val repo = FakeUnlockedAreaRepository()
            val useCase = UnlockAreaUseCase(h3, repo)

            // ~110 km north: far past MAX_BRIDGE_DISTANCE_M.
            useCase(location(2.0, 1.0), bridgeFrom = fix(1.0, 1.0))

            assertEquals("only the fix itself", 1, h3.revealCalls.size)
        }

    @Test
    fun `a stale restored fix is not bridged to`() =
        runTest {
            val h3 = FakeH3Converter(diskSize = 7)
            val repo = FakeUnlockedAreaRepository()
            val useCase = UnlockAreaUseCase(h3, repo)

            // Eleven minutes old: past BRIDGE_MAX_AGE_MS, so the caller hands over null rather
            // than letting the trail claim a corridor nobody travelled.
            val now = 11 * 60 * 1000L
            val session = TrackingSession(lastFix = TrackingSession.Fix(1.0, 1.0, atMs = 0L))
            useCase(location(1.1, 1.1), bridgeFrom = session.bridgeableFix(now))

            assertEquals("only the fix itself", 1, h3.revealCalls.size)
        }

    @Test
    fun `a fresh restored fix bridges across the restart`() =
        runTest {
            val h3 = FakeH3Converter(diskSize = 7)
            val repo = FakeUnlockedAreaRepository()
            val useCase = UnlockAreaUseCase(h3, repo)

            val now = 60_000L
            val session = TrackingSession(lastFix = TrackingSession.Fix(1.0, 1.0, atMs = 0L))
            useCase(location(1.1, 1.1), bridgeFrom = session.bridgeableFix(now))

            assertTrue("the trail must continue across a restart", h3.revealCalls.size > 1)
        }

    @Test
    fun `duplicate unlocks do not inflate newly unlocked count`() =
        runTest {
            val h3 = FakeH3Converter(diskSize = 7)
            val repo = FakeUnlockedAreaRepository()
            val useCase = UnlockAreaUseCase(h3, repo)
            val fix = location(1.0, 1.0)

            val first = useCase(fix, bridgeFrom = null)
            val second = useCase(fix, bridgeFrom = null)

            assertEquals(7, first)
            assertEquals(0, second)
            assertEquals(7, repo.unlocked.size)
        }

    private fun fix(
        lat: Double,
        lng: Double,
    ) = TrackingSession.Fix(latitude = lat, longitude = lng, atMs = 0L)

    private fun location(
        lat: Double,
        lng: Double,
    ) = DomainLocation(
        latitude = lat,
        longitude = lng,
        accuracyMeters = 10f,
        elapsedRealtimeNanos = 1_000_000_000L,
        isMock = false,
    )

    private class FakeUnlockedAreaRepository : UnlockedAreaRepository {
        val unlocked = linkedSetOf<Long>()

        override suspend fun unlock(hexes: Set<Long>): Int {
            val before = unlocked.size
            unlocked += hexes
            return unlocked.size - before
        }

        override fun observeTrail(): Flow<List<Long>> = flowOf(emptyList())

        override fun observeFar(): Flow<List<Long>> = flowOf(emptyList())

        override fun observeCount(): Flow<Int> = flowOf(unlocked.size)
    }

    private class FakeH3Converter(
        private val diskSize: Int,
        private val bridgeCells: Set<Long> = emptySet(),
    ) : H3Converter {
        val bridgeCalls = mutableListOf<Pair<Long, Long>>()
        val revealCalls = mutableListOf<Pair<Double, Double>>()
        override val baseResolution: Int = H3Config.WALKING_RESOLUTION

        override suspend fun warmUp() = Unit

        override fun cellsForPolygon(
            rings: List<com.tripex.pose.domain.geo.atlas.Ring>,
            resolution: Int,
        ): Set<Long> = emptySet()

        override fun cellAt(
            lat: Double,
            lng: Double,
        ): Long = ((lat * 1_000).toLong() shl 20) xor (lng * 1_000).toLong()

        override fun cellCenter(cell: Long): Pair<Double, Double> = 0.0 to 0.0

        override fun revealDisk(
            lat: Double,
            lng: Double,
            k: Int,
        ): Set<Long> {
            val center = cellAt(lat, lng)
            return (0 until diskSize).map { center + it }.toSet()
        }

        override fun revealAround(
            lat: Double,
            lng: Double,
            radiusMeters: Double,
        ): Set<Long> {
            revealCalls += lat to lng
            return revealDisk(lat, lng, k = 1)
        }

        override fun bridge(
            from: Long,
            to: Long,
        ): Set<Long> {
            bridgeCalls += from to to
            return bridgeCells
        }

        override fun parentOf(
            cell: Long,
            resolution: Int,
        ): Long = cell

        override fun gridDistance(
            from: Long,
            to: Long,
        ): Int = bridgeCells.size

        override fun outline(cells: Collection<Long>): FogGeometry = FogGeometry.EMPTY

        override fun toDebugString(cell: Long): String = cell.toString()
    }
}
