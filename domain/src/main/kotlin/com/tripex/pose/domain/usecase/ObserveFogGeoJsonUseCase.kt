package com.tripex.pose.domain.usecase

import com.tripex.pose.core.di.DefaultDispatcher
import com.tripex.pose.domain.geo.FogGeoJsonBuilder
import com.tripex.pose.domain.geo.H3Config
import com.tripex.pose.domain.geo.H3Converter
import com.tripex.pose.domain.geo.MapViewport
import com.tripex.pose.domain.repository.UnlockedAreaRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Streams reveal-wash GeoJSON for the current camera.
 *
 * Always outlines **walking-resolution** cells so hex size stays geographic across zoom
 * (never substitutes parent res 7/9 indices into [H3Converter.outline]).
 */
class ObserveFogGeoJsonUseCase
    @Inject
    constructor(
        private val repository: UnlockedAreaRepository,
        private val h3: H3Converter,
        private val fogGeoJsonBuilder: FogGeoJsonBuilder,
        @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) {
        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke(viewport: Flow<MapViewport>): Flow<String> =
            viewport
                .debounce(CAMERA_DEBOUNCE_MS)
                .distinctUntilChanged()
                .flatMapLatest { vp ->
                    observeWalkingCells(vp).map { cells ->
                        val capped =
                            if (cells.size > MAX_CELLS_PER_OUTLINE) {
                                cells.take(MAX_CELLS_PER_OUTLINE)
                            } else {
                                cells
                            }
                        fogGeoJsonBuilder.build(h3.outline(capped))
                    }
                }.flowOn(defaultDispatcher)

        private fun observeWalkingCells(viewport: MapViewport): Flow<List<Long>> {
            val parents = h3.cellsForBounds(viewport.bounds, H3Config.LOD_FAR_RESOLUTION)
            return if (parents.isEmpty()) {
                flowOf(emptyList())
            } else {
                repository.observeDetailed(parents)
            }
        }

        companion object {
            const val CAMERA_DEBOUNCE_MS: Long = 400L
            const val MAX_CELLS_PER_OUTLINE: Int = 20_000
        }
    }
