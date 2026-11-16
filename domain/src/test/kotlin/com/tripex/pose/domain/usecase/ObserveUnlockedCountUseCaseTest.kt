package com.tripex.pose.domain.usecase

import com.tripex.pose.domain.repository.UnlockedAreaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveUnlockedCountUseCaseTest {
    @Test
    fun `delegates to repository observeCount`() =
        runTest {
            val repo =
                object : UnlockedAreaRepository {
                    override suspend fun addPoint(cell: Long, point: com.tripex.pose.domain.geo.TrailPoint) = Unit
                    override suspend fun addPoints(points: List<Pair<Long, com.tripex.pose.domain.geo.TrailPoint>>) = Unit
                    override suspend fun isKnown(cell: Long, mode: com.tripex.pose.domain.geo.TravelMode) = false
                    override fun observePoints(): Flow<List<com.tripex.pose.domain.geo.TrailPoint>> = flowOf(emptyList())

                    override fun observeTrail(): Flow<List<Long>> = flowOf(emptyList())

                    override fun observeFar(): Flow<List<Long>> = flowOf(emptyList())

                    override fun observeCount(): Flow<Int> = flowOf(42)
                }

            val emissions = ObserveUnlockedCountUseCase(repo)().toList()

            assertEquals(listOf(42), emissions)
        }
}
