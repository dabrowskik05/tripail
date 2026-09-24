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
                    override suspend fun unlock(hexes: Set<Long>): Int = 0

                    override fun observeTrail(): Flow<List<Long>> = flowOf(emptyList())

                    override fun observeFar(): Flow<List<Long>> = flowOf(emptyList())

                    override fun observeCount(): Flow<Int> = flowOf(42)
                }

            val emissions = ObserveUnlockedCountUseCase(repo)().toList()

            assertEquals(listOf(42), emissions)
        }
}
