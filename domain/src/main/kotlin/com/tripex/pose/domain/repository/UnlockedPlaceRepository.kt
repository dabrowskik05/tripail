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

    /**
     * How a place came to be owned — and therefore whether it can be given back.
     *
     * Ground earned by being there is permanent, the same rule that governs the walked trail.
     * Only a deliberate tap can be deliberately undone.
     */
    enum class Source {
        /** Chosen by the player: searched, or tapped on the map. Reversible. */
        Manual,

        /** Granted by standing there long enough (`AutoUnlockCityUseCase`). Permanent. */
        Auto,
    }

    data class UnlockedPlace(
        val id: String,
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Double,
        val unlockedAt: Long,
        val source: Source = Source.Manual,
    )

    suspend fun unlock(place: UnlockedPlace): Boolean

    /**
     * Gives a place back to the fog.
     *
     * Refuses rows with [Source.Auto]: that city was earned by being in it, so it is not the
     * player's to hand back — see the trail, which has no removal at all.
     *
     * @return true when a row was actually removed.
     */
    suspend fun lock(id: String): Boolean

    suspend fun find(id: String): UnlockedPlace?

    fun observeAll(): Flow<List<UnlockedPlace>>
}
