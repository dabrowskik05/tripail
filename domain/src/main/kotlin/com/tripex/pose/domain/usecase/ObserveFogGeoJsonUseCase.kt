package com.tripex.pose.domain.usecase

import com.tripex.pose.core.di.DefaultDispatcher
import com.tripex.pose.domain.geo.FogGeoJsonBuilder
import com.tripex.pose.domain.geo.GeoCircle
import com.tripex.pose.domain.geo.H3Config
import com.tripex.pose.domain.geo.H3Converter
import com.tripex.pose.domain.geo.MapViewport
import com.tripex.pose.domain.geo.atlas.BoundaryGeometrySource
import com.tripex.pose.domain.repository.UnlockedAreaRepository
import com.tripex.pose.domain.repository.UnlockedPlaceRepository
import com.tripex.pose.domain.repository.UnlockedRegionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
        private val regionRepository: UnlockedRegionRepository,
        private val placeRepository: UnlockedPlaceRepository,
        private val boundaries: BoundaryGeometrySource,
        @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) {
        /**
         * World-wide wash, independent of the camera.
         *
         * Every level of the app draws the same map, so a hole punched in Czechia has to stay
         * punched while the player is looking at Poland. The viewport-scoped variant below is for
         * the explore screen, where the camera is the subject and the cell count can be huge.
         */
        fun global(): Flow<String> =
            combine(
                repository.observeAllDetailed(MAX_CELLS_PER_OUTLINE),
                regionRepository.observeAll(),
                placeRepository.observeAll(),
            ) { cells, regions, places ->
                fogGeoJsonBuilder.build(
                    h3.outline(cells),
                    regionHoles(regions) + places.map { it.toRing() },
                )
            }
                .distinctUntilChanged()
                .flowOn(defaultDispatcher)

        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke(viewport: Flow<MapViewport>): Flow<String> =
            viewport
                .debounce(CAMERA_DEBOUNCE_MS)
                .distinctUntilChanged()
                .flatMapLatest { vp ->
                    combine(
                        observeWalkingCells(vp),
                        regionRepository.observeAll(),
                    ) { cells, regions ->
                        val capped =
                            if (cells.size > MAX_CELLS_PER_OUTLINE) {
                                cells.take(MAX_CELLS_PER_OUTLINE)
                            } else {
                                cells
                            }
                        fogGeoJsonBuilder.build(h3.outline(capped), regionHoles(regions))
                    }
                }.flowOn(defaultDispatcher)

        /**
         * Outlines of whole-region unlocks, fetched from the bundle on demand. The database holds
         * only ids, so this is where a macro unlock turns back into geometry.
         */
        private suspend fun regionHoles(
            regions: List<UnlockedRegionRepository.UnlockedRegion>,
        ): List<List<Pair<Double, Double>>> {
            if (regions.isEmpty()) return emptyList()
            val holes = ArrayList<List<Pair<Double, Double>>>(regions.size)
            for (region in regions.take(MAX_REGION_HOLES)) {
                val cached = regionRingCache[region.cacheKey()]
                    ?: boundaries.rings(region.level, region.featureId).getOrNull()
                        ?.also { regionRingCache[region.cacheKey()] = it }
                    ?: continue
                holes += cached
            }
            return holes
        }

        /** A searched city is one ring, generated on demand from three stored numbers. */
        private fun UnlockedPlaceRepository.UnlockedPlace.toRing(): List<Pair<Double, Double>> =
            GeoCircle.ring(lat = latitude, lng = longitude, radiusMeters = radiusMeters)

        private fun UnlockedRegionRepository.UnlockedRegion.cacheKey(): String =
            "$level:$featureId"

        /** Reading an outline means decoding tiles, so each one is read at most once per process. */
        private val regionRingCache = java.util.Collections.synchronizedMap(
            object : LinkedHashMap<String, List<List<Pair<Double, Double>>>>(
                REGION_CACHE_SIZE + 1,
                0.75f,
                true,
            ) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, List<List<Pair<Double, Double>>>>?,
                ): Boolean = size > REGION_CACHE_SIZE
            },
        )

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

            /** Past this many whole regions the wash is mostly holes anyway. */
            const val MAX_REGION_HOLES: Int = 64
            const val REGION_CACHE_SIZE: Int = 32
        }
    }
