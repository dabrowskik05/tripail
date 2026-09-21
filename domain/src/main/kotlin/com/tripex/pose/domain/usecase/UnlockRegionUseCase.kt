package com.tripex.pose.domain.usecase

import com.tripex.pose.domain.geo.atlas.AdminLevel
import com.tripex.pose.domain.geo.atlas.BoundaryGeometrySource
import com.tripex.pose.domain.repository.UnlockedRegionRepository
import javax.inject.Inject

/**
 * Unlocks an entire administrative area by its exact outline (hybrid model, option A).
 *
 * Nothing is rasterised into cells: the bundle already holds the polygon, so owning the region is
 * owning its id. The outline is still fetched here, because an id with no geometry behind it
 * would be a row that silently reveals nothing.
 */
class UnlockRegionUseCase
    @Inject
    constructor(
        private val boundaries: BoundaryGeometrySource,
        private val repository: UnlockedRegionRepository,
    ) {
        suspend operator fun invoke(
            level: AdminLevel,
            featureId: String,
        ): Result<Boolean> {
            if (featureId.isBlank()) {
                return Result.failure(IllegalArgumentException("Blank feature id"))
            }
            val rings = boundaries.rings(level, featureId).getOrElse { return Result.failure(it) }
            if (rings.isEmpty()) {
                return Result.failure(IllegalStateException("No outline for $level $featureId"))
            }
            return runCatching { repository.unlock(level, featureId) }
        }
    }
