package com.tripex.pose.domain.usecase

import app.cash.turbine.test
import com.tripex.pose.domain.geo.FogGeoJsonBuilder
import com.tripex.pose.domain.geo.atlas.AdminLevel
import com.tripex.pose.domain.geo.atlas.BoundaryFeature
import com.tripex.pose.domain.geo.atlas.BoundaryGeometrySource
import com.tripex.pose.domain.geo.atlas.Ring
import com.tripex.pose.domain.geo.FogGeometry
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.H3Config
import com.tripex.pose.domain.geo.H3Converter
import com.tripex.pose.domain.geo.MapViewport
import com.tripex.pose.domain.repository.UnlockedAreaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObserveFogGeoJsonUseCaseTest {
    @Test
    fun `emits geojson after viewport debounce`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repo = FakeRepo(walkingCells = listOf(11L, 12L), midCells = listOf(9L), farCells = listOf(7L))
            val h3 = FakeH3()
            val useCase =
                ObserveFogGeoJsonUseCase(
                    repository = repo,
                    h3 = h3,
                    fogGeoJsonBuilder = FogGeoJsonBuilder(),
                    regionRepository = FakeUnlockedRegionRepository(),
                    placeRepository = FakeUnlockedPlaceRepository(),
                    boundaries = NoBoundaries,
                    defaultDispatcher = dispatcher,
                )
            val viewport = MutableStateFlow(MapViewport.DEFAULT.copy(zoom = 5.0))

            useCase(viewport).test {
                advanceTimeBy(ObserveFogGeoJsonUseCase.CAMERA_DEBOUNCE_MS + 1)
                val json = awaitItem()
                assertTrue(json.contains("FeatureCollection"))
                assertTrue(json.contains("-180.0"))
                assertEquals(listOf(11L, 12L), h3.lastOutlined)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `outlines walking cells even at low zoom not parent lod ids`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repo = FakeRepo(walkingCells = listOf(100L), midCells = listOf(200L), farCells = listOf(300L))
            val h3 = FakeH3()
            val useCase =
                ObserveFogGeoJsonUseCase(
                    repository = repo,
                    h3 = h3,
                    fogGeoJsonBuilder = FogGeoJsonBuilder(),
                    regionRepository = FakeUnlockedRegionRepository(),
                    placeRepository = FakeUnlockedPlaceRepository(),
                    boundaries = NoBoundaries,
                    defaultDispatcher = dispatcher,
                )
            val viewport = MutableStateFlow(MapViewport.DEFAULT.copy(zoom = 3.0))

            useCase(viewport).test {
                advanceTimeBy(ObserveFogGeoJsonUseCase.CAMERA_DEBOUNCE_MS + 1)
                awaitItem()
                assertEquals(listOf(100L), h3.lastOutlined)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private class FakeRepo(
        private val walkingCells: List<Long>,
        private val midCells: List<Long>,
        private val farCells: List<Long>,
    ) : UnlockedAreaRepository {
        override suspend fun unlock(hexes: Set<Long>): Int = 0

        override fun observeDetailed(viewportCells: Set<Long>): Flow<List<Long>> = flowOf(walkingCells)
        override fun observeAllDetailed(limit: Int): Flow<List<Long>> = observeDetailed(emptySet())

        override fun observeMid(viewportCells: Set<Long>): Flow<List<Long>> = flowOf(midCells)

        override fun observeFar(): Flow<List<Long>> = flowOf(farCells)

        override fun observeCount(): Flow<Int> = flowOf(walkingCells.size)
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

        override fun cellsForBounds(
            bounds: GeoBounds,
            resolution: Int,
        ): Set<Long> = setOf(99L)

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
