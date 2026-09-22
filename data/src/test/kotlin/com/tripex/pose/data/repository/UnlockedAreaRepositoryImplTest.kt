package com.tripex.pose.data.repository

import com.tripex.pose.data.local.UnlockedHexDao
import com.tripex.pose.data.local.UnlockedHexEntity
import com.tripex.pose.domain.geo.FogGeometry
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.H3Config
import com.tripex.pose.domain.geo.H3Converter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UnlockedAreaRepositoryImplTest {

    private val dispatcher = StandardTestDispatcher()

    @Test
    fun `unlock returns count of newly inserted rows`() = runTest(dispatcher) {
        val dao = FakeUnlockedHexDao()
        val repo = UnlockedAreaRepositoryImpl(dao, FakeH3(), dispatcher)

        val first = repo.unlock(setOf(1L, 2L, 3L))
        val second = repo.unlock(setOf(2L, 3L, 4L))

        assertEquals(3, first)
        assertEquals(1, second)
        assertEquals(setOf(1L, 2L, 3L, 4L), dao.stored.keys)
    }

    @Test
    fun `unlock of empty set is zero`() = runTest(dispatcher) {
        val repo = UnlockedAreaRepositoryImpl(FakeUnlockedHexDao(), FakeH3(), dispatcher)
        assertEquals(0, repo.unlock(emptySet()))
    }

    private class FakeUnlockedHexDao : UnlockedHexDao {
        val stored = LinkedHashMap<Long, UnlockedHexEntity>()
        private val count = MutableStateFlow(0)

        override suspend fun insertAll(hexes: List<UnlockedHexEntity>): List<Long> =
            hexes.map { entity ->
                if (stored.containsKey(entity.h3Index)) {
                    -1L
                } else {
                    stored[entity.h3Index] = entity
                    count.value = stored.size
                    entity.h3Index
                }
            }

        override fun observeDetailed(viewportCells: Set<Long>): Flow<List<Long>> = flowOf(emptyList())
        override fun observeMid(viewportCells: Set<Long>): Flow<List<Long>> = flowOf(emptyList())
        override fun observeFar(): Flow<List<Long>> = flowOf(emptyList())
        override fun observeCount(): Flow<Int> = count
    }

    private class FakeH3 : H3Converter {
        override val baseResolution: Int = H3Config.WALKING_RESOLUTION

        override suspend fun warmUp() = Unit

        override fun cellsForPolygon(
            rings: List<com.tripex.pose.domain.geo.atlas.Ring>,
            resolution: Int,
        ): Set<Long> = emptySet()
        override fun cellAt(lat: Double, lng: Double): Long = 0L
        override fun cellCenter(cell: Long): Pair<Double, Double> = 0.0 to 0.0
        override fun revealDisk(lat: Double, lng: Double, k: Int): Set<Long> = emptySet()
        override fun revealAround(lat: Double, lng: Double, radiusMeters: Double): Set<Long> = emptySet()
        override fun bridge(from: Long, to: Long): Set<Long> = emptySet()
        override fun parentOf(cell: Long, resolution: Int): Long = cell / 10
        override fun gridDistance(from: Long, to: Long): Int = 0
        override fun outline(cells: Collection<Long>): FogGeometry = FogGeometry.EMPTY
        override fun cellsForBounds(bounds: GeoBounds, resolution: Int): Set<Long> = emptySet()
        override fun toDebugString(cell: Long): String = cell.toString()
    }
}
