package com.tripex.pose.domain.usecase

import com.tripex.pose.domain.geo.atlas.AdminLevel
import com.tripex.pose.domain.repository.UnlockedRegionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory stand-in for the macro-scale unlock table. */
internal class FakeUnlockedRegionRepository(
    initial: List<UnlockedRegionRepository.UnlockedRegion> = emptyList(),
) : UnlockedRegionRepository {

    val regions = MutableStateFlow(initial)

    override suspend fun unlock(level: AdminLevel, featureId: String): Boolean {
        val existing = regions.value
        if (existing.any { it.level == level && it.featureId == featureId }) return false
        regions.value = existing + UnlockedRegionRepository.UnlockedRegion(level, featureId, 0L)
        return true
    }

    override suspend fun lock(level: AdminLevel, featureId: String): Boolean {
        val before = regions.value.size
        regions.value = regions.value.filterNot { it.level == level && it.featureId == featureId }
        return regions.value.size != before
    }

    override fun observeAll(): Flow<List<UnlockedRegionRepository.UnlockedRegion>> = regions

    override suspend fun isUnlocked(level: AdminLevel, featureId: String): Boolean =
        regions.value.any { it.level == level && it.featureId == featureId }
}
