package com.tripex.pose.domain.usecase

import com.tripex.pose.domain.geo.FogGeometry
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.H3Config
import com.tripex.pose.domain.geo.H3Converter
import com.tripex.pose.domain.geo.Place
import com.tripex.pose.domain.repository.GeocodingRepository
import com.tripex.pose.domain.repository.UnlockedAreaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockPlaceUseCaseTest {
    @Test
    fun `unlocks revealAround cells for geocoded place`() =
        runTest {
            val place = Place("Warszawa", 52.23, 21.01)
            val geo = FakeGeocoding(Result.success(place))
            val repo = FakeUnlockedRepo()
            val h3 = FakeH3(cells = setOf(10L, 11L, 12L))
            val useCase = UnlockPlaceUseCase(geo, h3, repo)

            val result = useCase("Warszawa").getOrThrow()

            assertEquals(place, result.place)
            assertEquals(3, result.newlyUnlocked)
            assertEquals(setOf(10L, 11L, 12L), repo.unlocked)
            assertEquals(5_000.0, h3.lastRadius!!, 0.01)
        }

    @Test
    fun `empty query fails without calling geocoder`() =
        runTest {
            val geo = FakeGeocoding(Result.failure(IllegalStateException("should not call")))
            val useCase = UnlockPlaceUseCase(geo, FakeH3(), FakeUnlockedRepo())

            val result = useCase("   ")

            assertTrue(result.isFailure)
            assertEquals(0, geo.calls)
        }

    @Test
    fun `geocoding failure propagates`() =
        runTest {
            val geo = FakeGeocoding(Result.failure(NoSuchElementException("No results")))
            val useCase = UnlockPlaceUseCase(geo, FakeH3(), FakeUnlockedRepo())

            assertTrue(useCase("Nowhere").isFailure)
        }

    private class FakeGeocoding(
        private val result: Result<Place>,
    ) : GeocodingRepository {
        var calls: Int = 0

        override suspend fun search(query: String): Result<Place> {
            calls++
            return result
        }
    }

    private class FakeUnlockedRepo : UnlockedAreaRepository {
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

    private class FakeH3(
        private val cells: Set<Long> = emptySet(),
    ) : H3Converter {
        var lastRadius: Double? = null
        override val baseResolution: Int = H3Config.WALKING_RESOLUTION

        override fun cellAt(
            lat: Double,
            lng: Double,
        ): Long = 1L

        override fun cellCenter(cell: Long): Pair<Double, Double> = 0.0 to 0.0

        override fun revealDisk(
            lat: Double,
            lng: Double,
            k: Int,
        ): Set<Long> = cells

        override fun revealAround(
            lat: Double,
            lng: Double,
            radiusMeters: Double,
        ): Set<Long> {
            lastRadius = radiusMeters
            return cells
        }

        override fun bridge(
            from: Long,
            to: Long,
        ): Set<Long> = emptySet()

        override fun parentOf(
            cell: Long,
            resolution: Int,
        ): Long = cell

        override fun gridDistance(
            from: Long,
            to: Long,
        ): Int = 0

        override fun outline(cells: Collection<Long>): FogGeometry = FogGeometry.EMPTY

        override fun cellsForBounds(
            bounds: GeoBounds,
            resolution: Int,
        ): Set<Long> = emptySet()

        override fun toDebugString(cell: Long): String = cell.toString()
    }
}
