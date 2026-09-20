package com.tripex.pose.domain.usecase

import com.tripex.pose.core.di.DefaultDispatcher
import com.tripex.pose.domain.geo.ContinentBounds
import com.tripex.pose.domain.geo.ContinentId
import com.tripex.pose.domain.geo.H3Converter
import com.tripex.pose.domain.repository.UnlockedAreaRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Real per-continent unlock coverage using LOD_FAR parent cells vs estimated land totals.
 *
 * Coverage = unlocked parents whose cell center lies in the continent bbox /
 * [ContinentBounds.ContinentRegion.estimatedLandCells], coerced to `0f..1f`.
 */
class ObserveContinentCoverageUseCase
    @Inject
    constructor(
        private val repository: UnlockedAreaRepository,
        private val h3: H3Converter,
        @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) {
        operator fun invoke(): Flow<Map<ContinentId, Float>> =
            repository
                .observeFar()
                .map { parents -> coverageFor(parents) }
                .flowOn(defaultDispatcher)

        private fun coverageFor(parents: List<Long>): Map<ContinentId, Float> {
            val counts = ContinentId.entries.associateWith { 0 }.toMutableMap()
            for (cell in parents) {
                val (lat, lng) = h3.cellCenter(cell)
                for (region in ContinentBounds.ALL) {
                    if (ContinentBounds.contains(region.bounds, lat, lng)) {
                        counts[region.id] = counts.getValue(region.id) + 1
                        break
                    }
                }
            }
            return ContinentBounds.ALL.associate { region ->
                val ratio = counts.getValue(region.id).toFloat() / region.estimatedLandCells.toFloat()
                region.id to ratio.coerceIn(0f, 1f)
            }
        }
    }
