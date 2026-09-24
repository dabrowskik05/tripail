package com.tripex.pose.domain.usecase

import app.cash.turbine.test
import com.tripex.pose.domain.geo.FogGeoJsonBuilder
import com.tripex.pose.domain.geo.RevealUnion
import com.tripex.pose.domain.repository.UnlockedPlaceRepository
import com.tripex.pose.domain.geo.atlas.AdminLevel
import com.tripex.pose.domain.geo.atlas.BoundaryFeature
import com.tripex.pose.domain.geo.atlas.BoundaryGeometrySource
import com.tripex.pose.domain.geo.atlas.Ring
import com.tripex.pose.domain.geo.FogGeometry
import com.tripex.pose.domain.geo.H3Config
import com.tripex.pose.domain.geo.H3Converter
import com.tripex.pose.domain.repository.UnlockedAreaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObserveFogGeoJsonUseCaseTest {
    @Test
    fun `emits the world polygon with the trail punched out`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val h3 = FakeH3()
            val useCase = useCase(FakeRepo(trailCells = listOf(11L, 12L)), h3, dispatcher)

            useCase().test {
                val json = awaitItem()
                assertTrue(json.contains("FeatureCollection"))
                assertTrue(json.contains("-180.0"))
                assertEquals(listOf(11L, 12L), h3.lastOutlined)
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * The trail used to be drawn from walking cells close in and from coarse parents far out
     * (`FogLod`), so zooming changed its shape. It is now always the trail-resolution parents,
     * and the camera is not an input at all.
     */
    @Test
    fun `draws trail-resolution parents, never the coarse ones`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val h3 = FakeH3()
            val repo = FakeRepo(trailCells = listOf(200L), farCells = listOf(300L))

            useCase(repo, h3, dispatcher)().test {
                awaitItem()
                assertEquals(listOf(200L), h3.lastOutlined)
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * The photographed regression: a city circle overlapping the walked trail rendered the
     * overlap as fog. Unioned, the two become one shape, so the world polygon gets exactly one
     * hole rather than two that intersect.
     */
    @Test
    fun `a city circle overlapping the trail produces one hole, not two`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val places = FakeUnlockedPlaceRepository()
            // Centred inside the square FakeH3.outline() returns, so the two overlap.
            places.places.value = listOf(
                UnlockedPlaceRepository.UnlockedPlace(
                    id = "skierniewice",
                    name = "Skierniewice",
                    latitude = 52.05,
                    longitude = 21.05,
                    radiusMeters = 5_000.0,
                    unlockedAt = 0L,
                ),
            )
            val useCase = ObserveFogGeoJsonUseCase(
                repository = FakeRepo(trailCells = listOf(100L)),
                h3 = FakeH3(),
                fogGeoJsonBuilder = FogGeoJsonBuilder(),
                revealUnion = RevealUnion(),
                regionRepository = FakeUnlockedRegionRepository(),
                placeRepository = places,
                boundaries = NoBoundaries,
                defaultDispatcher = dispatcher,
            )

            useCase().test {
                val json = awaitItem()

                // One Feature (the world polygon). Two overlapping holes would have produced
                // the world polygon plus leftover island Features.
                assertEquals(1, json.split("\"type\":\"Feature\"").size - 1)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun useCase(
        repo: FakeRepo,
        h3: FakeH3,
        dispatcher: TestDispatcher,
    ) = ObserveFogGeoJsonUseCase(
        repository = repo,
        h3 = h3,
        fogGeoJsonBuilder = FogGeoJsonBuilder(),
        revealUnion = RevealUnion(),
        regionRepository = FakeUnlockedRegionRepository(),
        placeRepository = FakeUnlockedPlaceRepository(),
        boundaries = NoBoundaries,
        defaultDispatcher = dispatcher,
    )

    private class FakeRepo(
        private val trailCells: List<Long>,
        private val farCells: List<Long> = emptyList(),
    ) : UnlockedAreaRepository {
        override suspend fun unlock(hexes: Set<Long>): Int = 0

        override fun observeTrail(): Flow<List<Long>> = flowOf(trailCells)

        override fun observeFar(): Flow<List<Long>> = flowOf(farCells)

        override fun observeCount(): Flow<Int> = flowOf(trailCells.size)
    }

    private class FakeH3 : H3Converter {
        var lastOutlined: List<Long> = emptyList()
        override val baseResolution: Int = H3Config.WALKING_RESOLUTION

        override suspend fun warmUp() = Unit

        override fun cellsForPolygon(
            rings: List<com.tripex.pose.domain.geo.atlas.Ring>,
            resolution: Int,
        ): Set<Long> = emptySet()

        override fun cellAt(
            lat: Double,
            lng: Double,
        ): Long = 1L

        override fun cellCenter(cell: Long): Pair<Double, Double> = 0.0 to 0.0

        override fun revealDisk(
            lat: Double,
            lng: Double,
            k: Int,
        ): Set<Long> = setOf(1L)

        override fun revealAround(
            lat: Double,
            lng: Double,
            radiusMeters: Double,
        ): Set<Long> = setOf(1L)

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

        override fun outline(cells: Collection<Long>): FogGeometry {
            lastOutlined = cells.toList()
            if (cells.isEmpty()) return FogGeometry.EMPTY
            val ring =
                listOf(
                    21.0 to 52.0,
                    21.1 to 52.0,
                    21.1 to 52.1,
                    21.0 to 52.1,
                    21.0 to 52.0,
                )
            return FogGeometry(listOf(listOf(ring)))
        }

        override fun toDebugString(cell: Long): String = cell.toString()
    }

    /** No macro-scale unlocks in these tests — the wash comes from H3 cells alone. */
    private object NoBoundaries : BoundaryGeometrySource {
        override suspend fun rings(level: AdminLevel, id: String): Result<List<Ring>> =
            Result.failure(IllegalStateException("no bundle"))

        override suspend fun feature(level: AdminLevel, id: String): BoundaryFeature? = null

        override suspend fun featureAt(level: AdminLevel, lat: Double, lng: Double): BoundaryFeature? = null
    }
}
