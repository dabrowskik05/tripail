package com.tripex.pose.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Searched places owned as circles (macro unlocks).
 *
 * The second half of the hybrid model: whole regions are stored as boundary ids, whole cities as
 * a centre and a radius. Neither one rasterises into H3, which is what keeps a capital city from
 * costing hundreds of thousands of rows.
 */
interface UnlockedPlaceRepository {

    data class UnlockedPlace(
        val id: String,
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Double,
        val unlockedAt: Long,
    )

    suspend fun unlock(place: UnlockedPlace): Boolean

    suspend fun lock(id: String): Boolean

    fun observeAll(): Flow<List<UnlockedPlace>>
}
