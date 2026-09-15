package com.tripex.pose.domain.usecase

import com.tripex.pose.core.di.DefaultDispatcher
import com.tripex.pose.domain.geo.FogGeoJsonBuilder
import com.tripex.pose.domain.geo.H3Config
import com.tripex.pose.domain.geo.H3Converter
import com.tripex.pose.domain.geo.MapViewport
import com.tripex.pose.domain.repository.UnlockedAreaRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Streams fog GeoJSON for the current camera. Recomputes on viewport change (debounced)
 * or when unlocked cells change.
 */
class ObserveFogGeoJsonUseCase @Inject constructor(
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
                observeCells(vp).map { cells ->
                    val capped = if (cells.size > MAX_CELLS_PER_OUTLINE) {
                        cells.take(MAX_CELLS_PER_OUTLINE)
                    } else {
                        cells
                    }
                    fogGeoJsonBuilder.build(h3.outline(capped))
                }
            }
            .flowOn(defaultDispatcher)

    private fun observeCells(viewport: MapViewport): Flow<List<Long>> =
        when {
            viewport.zoom < ZOOM_MID -> repository.observeFar()
            viewport.zoom < ZOOM_DETAILED -> {
                val parents = h3.cellsForBounds(viewport.bounds, H3Config.LOD_FAR_RESOLUTION)
                if (parents.isEmpty()) {
                    repository.observeFar()
                } else {
                    repository.observeMid(parents)
                }
            }
            else -> {
                val parents = h3.cellsForBounds(viewport.bounds, H3Config.LOD_FAR_RESOLUTION)
                if (parents.isEmpty()) {
                    repository.observeFar()
                } else {
                    repository.observeDetailed(parents)
                }
            }
        }

    companion object {
        const val CAMERA_DEBOUNCE_MS: Long = 400L
        const val MAX_CELLS_PER_OUTLINE: Int = 20_000
        const val ZOOM_MID: Double = 11.0
        const val ZOOM_DETAILED: Double = 14.0
    }
}
