package com.tripex.pose.domain.usecase

import app.cash.turbine.test
import com.tripex.pose.domain.geo.AreaCoverage
import com.tripex.pose.domain.geo.FogGeometry
import com.tripex.pose.domain.geo.ContinentId
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.H3Config
import com.tripex.pose.domain.geo.H3Converter
import com.tripex.pose.domain.geo.atlas.AdminLevel
import com.tripex.pose.domain.geo.atlas.AreaKey
import com.tripex.pose.domain.geo.atlas.AtlasRepository
import com.tripex.pose.domain.geo.atlas.ContinentShape
import com.tripex.pose.domain.geo.atlas.LandShape
import com.tripex.pose.domain.geo.atlas.BoundaryFeature
import com.tripex.pose.domain.geo.atlas.BoundaryGeometrySource
import com.tripex.pose.domain.geo.atlas.Ring
import com.tripex.pose.domain.repository.AreaStatsCache
import com.tripex.pose.domain.repository.UnlockedAreaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveAreaCoverageUseCaseTest {

    private val dispatcher = StandardTestDispatcher()
    private val poland = AreaKey.Country("PL")

    /** A square ring is enough — the fake H3 decides which cells it maps to. */
    private val squareRing: Ring = listOf(
        14.0 to 49.0,
        24.0 to 49.0,
        24.0 to 55.0,
        14.0 to 55.0,
        14.0 to 49.0,
    )

    @Test
    fun `empty database means zero percent, not unavailable`() = runTest(dispatcher) {
        val useCase = useCase(areaCells = setOf(1L, 2L, 3L, 4L), unlockedParents = emptyList())

        useCase(poland).test {
            val coverage = awaitItem() as AreaCoverage.Known
            assertEquals(0f, coverage.fraction, 0.0001f)
            assertEquals(4, coverage.areaCells)
            assertEquals(0, coverage.discoveredCells)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `every area cell discovered means one hundred percent`() = runTest(dispatcher) {
        val useCase = useCase(
            areaCells = setOf(1L, 2L, 3L, 4L),
            unlockedParents = listOf(1L, 2L, 3L, 4L),
        )

        useCase(poland).test {
            val coverage = awaitItem() as AreaCoverage.Known
            assertEquals(1f, coverage.fraction, 0.0001f)
            assertEquals(4, coverage.discoveredCells)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `parents outside the area do not count`() = runTest(dispatcher) {
        val useCase = useCase(
            areaCells = setOf(1L, 2L, 3L, 4L),
            // 99 and 98 are unlocked but belong to another country.
            unlockedParents = listOf(1L, 2L, 99L, 98L),
        )

        useCase(poland).test {
            val coverage = awaitItem() as AreaCoverage.Known
            assertEquals(0.5f, coverage.fraction, 0.0001f)
            assertEquals(2, coverage.discoveredCells)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `missing geometry reports unavailable instead of zero`() = runTest(dispatcher) {
        val useCase = useCase(areaCells = emptySet(), unlockedParents = emptyList(), rings = null)

        useCase(poland).test {
            assertEquals(AreaCoverage.Unavailable, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cities have no polygon anywhere and report unavailable`() = runTest(dispatcher) {
        val useCase = useCase(areaCells = setOf(1L), unlockedParents = emptyList())

        useCase(AreaKey.City("maptiler:warsaw")).test {
            assertEquals(AreaCoverage.Unavailable, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `continents are measured against the overview atlas, not the boundary bundle`() =
        runTest(dispatcher) {
            val useCase = useCase(
                areaCells = setOf(1L, 2L, 3L, 4L),
                unlockedParents = listOf(1L),
                // Nothing in the bundle — the atlas must carry this one.
                rings = null,
                atlasRings = listOf(squareRing),
            )

            useCase(AreaKey.Continent(ContinentId.Europe)).test {
                val coverage = awaitItem() as AreaCoverage.Known
                assertEquals(0.25f, coverage.fraction, 0.0001f)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `denominator is computed once and reused across emissions`() = runTest(dispatcher) {
        val h3 = FakeH3(areaCells = setOf(1L, 2L))
        val parents = MutableStateFlow(listOf(1L))
        val useCase = useCase(h3 = h3, repository = FakeRepository(parents))

        useCase(poland).test {
            assertEquals(0.5f, (awaitItem() as AreaCoverage.Known).fraction, 0.0001f)
            parents.value = listOf(1L, 2L)
            assertEquals(1f, (awaitItem() as AreaCoverage.Known).fraction, 0.0001f)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals("cellsForPolygon must run once per area", 1, h3.cellsForPolygonCalls)
    }

    @Test
    fun `a second collection reads the cached denominator instead of measuring the area again`() =
        runTest(dispatcher) {
            val cache = FakeAreaStatsCache()
            val useCase = useCase(areaCells = setOf(1L, 2L), unlockedParents = listOf(1L), cache = cache)

            useCase(poland).test {
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(1, cache.writes)

            useCase(poland).test {
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            assertTrue("cache must be read on the second pass", cache.reads >= 2)
            assertEquals("an unchanged denominator must not be rewritten", 1, cache.writes)
        }

    @Test
    fun `a boundaries bundle bump invalidates the cached denominator`() = runTest(dispatcher) {
        val cache = FakeAreaStatsCache()
        val useCase = useCase(areaCells = setOf(1L, 2L), unlockedParents = listOf(1L), cache = cache)

        useCase(poland).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, cache.writes)

        // What a bundle bump looks like from the domain's side: the row is simply gone.
        cache.clear()

        useCase(poland).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals("the denominator must be written again after invalidation", 2, cache.writes)
    }

    private fun useCase(
        areaCells: Set<Long> = setOf(1L),
        unlockedParents: List<Long> = emptyList(),
        rings: List<Ring>? = listOf(squareRing),
        atlasRings: List<Ring>? = null,
        cache: AreaStatsCache = FakeAreaStatsCache(),
        regionRepository: FakeUnlockedRegionRepository = FakeUnlockedRegionRepository(),
        h3: FakeH3 = FakeH3(areaCells),
        // A completing flow by default, so `awaitComplete()` is meaningful; tests that need
        // ongoing emissions pass a MutableStateFlow-backed repository explicitly.
        repository: UnlockedAreaRepository = FakeRepository(flowOf(unlockedParents)),
    ) = ObserveAreaCoverageUseCase(
        repository = repository,
        boundaries = FakeBoundaries(rings),
        atlas = FakeAtlas(atlasRings),
        areaStats = cache,
        regionRepository = regionRepository,
        placeRepository = FakeUnlockedPlaceRepository(),
        h3 = h3,
        defaultDispatcher = dispatcher,
    )

    private class FakeBoundaries(private val rings: List<Ring>?) : BoundaryGeometrySource {
        override suspend fun rings(level: AdminLevel, id: String): Result<List<Ring>> =
            rings?.let { Result.success(it) } ?: Result.failure(IllegalStateException("no geometry"))

        override suspend fun feature(level: AdminLevel, id: String): BoundaryFeature? = null

        override suspend fun featureAt(level: AdminLevel, lat: Double, lng: Double): BoundaryFeature? = null
    }

    private class FakeAtlas(private val rings: List<Ring>?) : AtlasRepository {
        override suspend fun land(): LandShape = LandShape(emptyList(), BOUNDS)
        override suspend fun continents(): List<ContinentShape> =
            rings?.let { listOf(ContinentShape(ContinentId.Europe, it, BOUNDS)) } ?: emptyList()

        override fun clearCache() = Unit

        private companion object {
            val BOUNDS = GeoBounds(north = 55.0, south = 49.0, east = 24.0, west = 14.0)
        }
    }

    private class FakeRepository(private val parents: Flow<List<Long>>) : UnlockedAreaRepository {
        override suspend fun unlock(hexes: Set<Long>): Int = 0
        override fun observeDetailed(viewportCells: Set<Long>): Flow<List<Long>> = flowOf(emptyList())
        override fun observeMid(viewportCells: Set<Long>): Flow<List<Long>> = flowOf(emptyList())
        override fun observeFar(): Flow<List<Long>> = parents
        override fun observeCount(): Flow<Int> = flowOf(0)
    }

    private class FakeAreaStatsCache : AreaStatsCache {
        private val rows = mutableMapOf<String, AreaStatsCache.Denominator>()
        var reads = 0
            private set
        var writes = 0
            private set

        override suspend fun denominator(areaKey: String): AreaStatsCache.Denominator? {
            reads++
            return rows[areaKey]
        }

        override suspend fun put(areaKey: String, resolution: Int, cellCount: Int) {
            writes++
            rows[areaKey] = AreaStatsCache.Denominator(resolution, cellCount)
        }

        fun clear() = rows.clear()
    }

    /** Identity parent mapping keeps the test about the intersection, not about H3 itself. */
    private class FakeH3(private val areaCells: Set<Long>) : H3Converter {
        var cellsForPolygonCalls = 0
            private set

        override val baseResolution: Int = H3Config.WALKING_RESOLUTION
        override suspend fun warmUp() = Unit
        override fun cellAt(lat: Double, lng: Double): Long = 0L
        override fun cellCenter(cell: Long): Pair<Double, Double> = 0.0 to 0.0
        override fun revealDisk(lat: Double, lng: Double, k: Int): Set<Long> = emptySet()
        override fun revealAround(lat: Double, lng: Double, radiusMeters: Double): Set<Long> = emptySet()
        override fun bridge(from: Long, to: Long): Set<Long> = emptySet()
        override fun parentOf(cell: Long, resolution: Int): Long = cell
        override fun gridDistance(from: Long, to: Long): Int = -1
        override fun outline(cells: Collection<Long>): FogGeometry = FogGeometry.EMPTY
        override fun cellsForBounds(bounds: GeoBounds, resolution: Int): Set<Long> = emptySet()
        override fun toDebugString(cell: Long): String = cell.toString()

        override fun cellsForPolygon(rings: List<Ring>, resolution: Int): Set<Long> {
            cellsForPolygonCalls++
            return areaCells
        }
    }
}
