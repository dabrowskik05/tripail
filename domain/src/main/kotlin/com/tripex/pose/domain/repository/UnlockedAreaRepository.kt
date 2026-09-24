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

    /** Every unlocked cell lifted to [com.tripex.pose.domain.geo.H3Config.TRAIL_RESOLUTION]. */
    fun observeTrail(): Flow<List<Long>>

    /** Every unlocked cell lifted to [com.tripex.pose.domain.geo.H3Config.COARSE_RESOLUTION]. */
    fun observeFar(): Flow<List<Long>>

    fun observeCount(): Flow<Int>
}
