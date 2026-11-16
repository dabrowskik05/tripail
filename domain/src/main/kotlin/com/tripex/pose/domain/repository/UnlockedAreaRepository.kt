package com.tripex.pose.domain.repository

import com.tripex.pose.domain.geo.TrailPoint
import com.tripex.pose.domain.geo.TravelMode
import kotlinx.coroutines.flow.Flow

/**
 * Persistence boundary for the walked trail: a list of snapped points, nothing more.
 */
interface UnlockedAreaRepository {
    /** Stores one point. [cell] is its trail-resolution grid cell, used for dedup and statistics. */
    suspend fun addPoint(cell: Long, point: TrailPoint)

    /** Bulk variant for imports; each entry is `cell to point`. */
    suspend fun addPoints(points: List<Pair<Long, TrailPoint>>)

    /**
     * Whether this cell is already on the trail at least as clearly as [mode] would draw it.
     * A cell only driven through is not "known" to someone now walking it.
     */
    suspend fun isKnown(cell: Long, mode: TravelMode): Boolean

    /** Every point, ordered so that consecutive points of one segment are neighbours. */
    fun observePoints(): Flow<List<TrailPoint>>

    /** Distinct visited cells at [com.tripex.pose.domain.geo.H3Config.TRAIL_RESOLUTION]. */
    fun observeTrail(): Flow<List<Long>>

    /** Distinct visited cells at [com.tripex.pose.domain.geo.H3Config.COARSE_RESOLUTION]. */
    fun observeFar(): Flow<List<Long>>

    fun observeCount(): Flow<Int>
}
