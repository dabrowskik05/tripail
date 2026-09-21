package com.tripex.pose.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Persistence boundary for unlocked H3 cells.
 */
interface UnlockedAreaRepository {
    /**
     * Insert cells; re-walking an existing index is a no-op.
     * @return count of rows actually inserted (conflicts ignored).
     */
    suspend fun unlock(hexes: Set<Long>): Int

    fun observeDetailed(viewportCells: Set<Long>): Flow<List<Long>>

    /** All unlocked cells regardless of viewport, capped at [limit]. */
    fun observeAllDetailed(limit: Int): Flow<List<Long>>

    fun observeMid(viewportCells: Set<Long>): Flow<List<Long>>

    fun observeFar(): Flow<List<Long>>

    fun observeCount(): Flow<Int>
}
