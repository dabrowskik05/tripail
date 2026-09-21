package com.tripex.pose.domain.repository

import com.tripex.pose.domain.geo.atlas.AdminLevel
import kotlinx.coroutines.flow.Flow

/**
 * Macro-scale unlocks (M3 hybrid model).
 *
 * A whole region is stored as its **boundary id**, never as cells: one Polish voivodeship is
 * ~35 500 km², which at walking resolution would be roughly 17 million H3 indices — over 100 MB
 * for a single tap. The outline is fetched from `boundaries.pmtiles` when it is needed for
 * drawing or measuring, so the row stays a few dozen bytes.
 *
 * Small unlocks (a GPS fix, a city) keep going to [UnlockedAreaRepository] as H3 cells.
 */
interface UnlockedRegionRepository {

    data class UnlockedRegion(
        val level: AdminLevel,
        val featureId: String,
        val unlockedAt: Long,
    )

    /** Idempotent — re-unlocking a region already owned is a no-op. */
    suspend fun unlock(level: AdminLevel, featureId: String): Boolean

    /** Puts a region back under the fog. The only unlock in the app that is reversible. */
    suspend fun lock(level: AdminLevel, featureId: String): Boolean

    fun observeAll(): Flow<List<UnlockedRegion>>

    suspend fun isUnlocked(level: AdminLevel, featureId: String): Boolean
}
