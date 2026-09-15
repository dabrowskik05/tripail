package com.tripex.pose.domain.usecase

import com.tripex.pose.domain.geo.FogGeometry
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.H3Converter
import com.tripex.pose.domain.geo.H3Config
import com.tripex.pose.domain.location.DomainLocation
import com.tripex.pose.domain.repository.UnlockedAreaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockAreaUseCaseTest {

    @Test
    fun `first fix unlocks reveal disk of 7 cells`() = runTest {
        val h3 = FakeH3Converter(diskSize = 7)
        val repo = FakeUnlockedAreaRepository()
        val useCase = UnlockAreaUseCase(h3, repo)

        val unlocked = useCase(location(1.0, 1.0), previous = null)

        assertEquals(7, unlocked)
        assertEquals(7, repo.unlocked.size)
        assertTrue(h3.bridgeCalls.isEmpty())
    }

    @Test
    fun `gap filling unlocks bridge cells between fixes`() = runTest {
        val h3 = FakeH3Converter(diskSize = 7, bridgeCells = setOf(100L, 101L, 102L))
        val repo = FakeUnlockedAreaRepository()
        val useCase = UnlockAreaUseCase(h3, repo)

        useCase(location(1.0, 1.0), previous = null)
        val second = useCase(location(1.1, 1.1), previous = location(1.0, 1.0))

        assertEquals(1, h3.bridgeCalls.size)
        assertTrue(second >= 3)
        assertTrue(repo.unlocked.containsAll(setOf(100L, 101L, 102L)))
    }

    @Test
    fun `duplicate unlocks do not inflate newly unlocked count`() = runTest {
        val h3 = FakeH3Converter(diskSize = 7)
        val repo = FakeUnlockedAreaRepository()
        val useCase = UnlockAreaUseCase(h3, repo)
        val fix = location(1.0, 1.0)

        val first = useCase(fix, previous = null)
        val second = useCase(fix, previous = null)

        assertEquals(7, first)
        assertEquals(0, second)
        assertEquals(7, repo.unlocked.size)
    }

    private fun location(lat: Double, lng: Double) = DomainLocation(
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

        override fun observeDetailed(viewportCells: Set<Long>): Flow<List<Long>> = flowOf(emptyList())
        override fun observeMid(viewportCells: Set<Long>): Flow<List<Long>> = flowOf(emptyList())
        override fun observeFar(): Flow<List<Long>> = flowOf(emptyList())
        override fun observeCount(): Flow<Int> = flowOf(unlocked.size)
    }

    private class FakeH3Converter(
        private val diskSize: Int,
        private val bridgeCells: Set<Long> = emptySet(),
    ) : H3Converter {
        val bridgeCalls = mutableListOf<Pair<Long, Long>>()
        override val baseResolution: Int = H3Config.WALKING_RESOLUTION

        override fun cellAt(lat: Double, lng: Double): Long =
            ((lat * 1_000).toLong() shl 20) xor (lng * 1_000).toLong()

        override fun revealDisk(lat: Double, lng: Double, k: Int): Set<Long> {
            val center = cellAt(lat, lng)
            return (0 until diskSize).map { center + it }.toSet()
        }

        override fun revealAround(lat: Double, lng: Double, radiusMeters: Double): Set<Long> =
            revealDisk(lat, lng, k = 1)

        override fun bridge(from: Long, to: Long): Set<Long> {
            bridgeCalls += from to to
            return bridgeCells
        }

        override fun parentOf(cell: Long, resolution: Int): Long = cell
        override fun gridDistance(from: Long, to: Long): Int = bridgeCells.size
        override fun outline(cells: Collection<Long>): FogGeometry = FogGeometry.EMPTY
        override fun cellsForBounds(bounds: GeoBounds, resolution: Int): Set<Long> = emptySet()
        override fun toDebugString(cell: Long): String = cell.toString()
    }
}
