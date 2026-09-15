package com.tripex.pose.domain.usecase

import app.cash.turbine.test
import com.tripex.pose.domain.geo.FogGeometry
import com.tripex.pose.domain.geo.FogGeoJsonBuilder
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.H3Converter
import com.tripex.pose.domain.geo.H3Config
import com.tripex.pose.domain.geo.MapViewport
import com.tripex.pose.domain.repository.UnlockedAreaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class ObserveFogGeoJsonUseCaseTest {

    @Test
    fun `emits geojson after viewport debounce`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = FakeRepo(cells = listOf(1L, 2L, 3L))
        val useCase = ObserveFogGeoJsonUseCase(
            repository = repo,
            h3 = FakeH3(),
            fogGeoJsonBuilder = FogGeoJsonBuilder(),
            defaultDispatcher = dispatcher,
        )
        val viewport = MutableStateFlow(MapViewport.DEFAULT)

        useCase(viewport).test {
            advanceTimeBy(ObserveFogGeoJsonUseCase.CAMERA_DEBOUNCE_MS + 1)
            val json = awaitItem()
            assertTrue(json.contains("FeatureCollection"))
            assertTrue(json.contains("-180.0"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class FakeRepo(private val cells: List<Long>) : UnlockedAreaRepository {
        override suspend fun unlock(hexes: Set<Long>): Int = 0
        override fun observeDetailed(viewportCells: Set<Long>): Flow<List<Long>> = flowOf(cells)
        override fun observeMid(viewportCells: Set<Long>): Flow<List<Long>> = flowOf(cells)
        override fun observeFar(): Flow<List<Long>> = flowOf(cells)
        override fun observeCount(): Flow<Int> = flowOf(cells.size)
    }

    private class FakeH3 : H3Converter {
        override val baseResolution: Int = H3Config.WALKING_RESOLUTION
        override fun cellAt(lat: Double, lng: Double): Long = 1L
        override fun revealDisk(lat: Double, lng: Double, k: Int): Set<Long> = setOf(1L)
        override fun revealAround(lat: Double, lng: Double, radiusMeters: Double): Set<Long> = setOf(1L)
        override fun bridge(from: Long, to: Long): Set<Long> = emptySet()
        override fun parentOf(cell: Long, resolution: Int): Long = cell
        override fun gridDistance(from: Long, to: Long): Int = 0
        override fun outline(cells: Collection<Long>): FogGeometry {
            if (cells.isEmpty()) return FogGeometry.EMPTY
            val ring = listOf(
                21.0 to 52.0,
                21.1 to 52.0,
                21.1 to 52.1,
                21.0 to 52.1,
                21.0 to 52.0,
            )
            return FogGeometry(listOf(listOf(ring)))
        }
        override fun cellsForBounds(bounds: GeoBounds, resolution: Int): Set<Long> = setOf(99L)
        override fun toDebugString(cell: Long): String = cell.toString()
    }
}
