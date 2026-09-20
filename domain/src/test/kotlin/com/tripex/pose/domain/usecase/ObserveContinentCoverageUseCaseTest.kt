package com.tripex.pose.domain.usecase

import app.cash.turbine.test
import com.tripex.pose.domain.geo.ContinentId
import com.tripex.pose.domain.geo.FogGeometry
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.H3Config
import com.tripex.pose.domain.geo.H3Converter
import com.tripex.pose.domain.repository.UnlockedAreaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObserveContinentCoverageUseCaseTest {
    @Test
    fun `warsaw parent cell increases Europe coverage`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val europeCell = 42L
            val repo =
                object : UnlockedAreaRepository {
                    override suspend fun unlock(hexes: Set<Long>): Int = 0

                    override fun observeDetailed(viewportCells: Set<Long>): Flow<List<Long>> = flowOf(emptyList())

                    override fun observeMid(viewportCells: Set<Long>): Flow<List<Long>> = flowOf(emptyList())

                    override fun observeFar(): Flow<List<Long>> = flowOf(listOf(europeCell))

                    override fun observeCount(): Flow<Int> = flowOf(1)
                }
            val h3 =
                object : H3Converter by UnsupportedH3 {
                    override fun cellCenter(cell: Long): Pair<Double, Double> = 52.23 to 21.01
                }
            val useCase = ObserveContinentCoverageUseCase(repo, h3, dispatcher)

            useCase().test {
                val coverage = awaitItem()
                assertTrue(coverage.getValue(ContinentId.Europe) > 0f)
                assertEquals(0f, coverage.getValue(ContinentId.Antarctica), 0.0001f)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private object UnsupportedH3 : H3Converter {
        override val baseResolution: Int = H3Config.WALKING_RESOLUTION

        override fun cellAt(
            lat: Double,
            lng: Double,
        ): Long = error("unused")

        override fun cellCenter(cell: Long): Pair<Double, Double> = error("unused")

        override fun revealDisk(
            lat: Double,
            lng: Double,
            k: Int,
        ): Set<Long> = error("unused")

        override fun revealAround(
            lat: Double,
            lng: Double,
            radiusMeters: Double,
        ): Set<Long> = error("unused")

        override fun bridge(
            from: Long,
            to: Long,
        ): Set<Long> = error("unused")

        override fun parentOf(
            cell: Long,
            resolution: Int,
        ): Long = error("unused")

        override fun gridDistance(
            from: Long,
            to: Long,
        ): Int = error("unused")

        override fun outline(cells: Collection<Long>): FogGeometry = error("unused")

        override fun cellsForBounds(
            bounds: GeoBounds,
            resolution: Int,
        ): Set<Long> = error("unused")

        override fun toDebugString(cell: Long): String = error("unused")
    }
}
