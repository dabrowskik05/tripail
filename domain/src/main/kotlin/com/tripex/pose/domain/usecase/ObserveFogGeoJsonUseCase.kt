package com.tripex.pose.domain.usecase

import com.tripex.pose.core.di.DefaultDispatcher
import com.tripex.pose.domain.geo.FogComposer
import com.tripex.pose.domain.geo.FogLayers
import com.tripex.pose.domain.geo.TrailPoint
import com.tripex.pose.domain.geo.atlas.BoundaryGeometrySource
import com.tripex.pose.domain.geo.atlas.Ring
import com.tripex.pose.domain.repository.UnlockedAreaRepository
import com.tripex.pose.domain.repository.UnlockedPlaceRepository
import com.tripex.pose.domain.repository.UnlockedRegionRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Streams the fog — one geometry for the whole world, independent of the camera.
 *
 * The trail, the cities and the regions all go into [FogComposer] together, so nothing a player
 * discovered can be covered again by something else they discovered. Nothing is dropped for
 * being old. [snapshot] draws the picture as it stood at an earlier moment ("my history").
 */
class ObserveFogGeoJsonUseCase
    @Inject
    constructor(
        private val repository: UnlockedAreaRepository,
        private val composer: FogComposer,
        private val regionRepository: UnlockedRegionRepository,
        private val placeRepository: UnlockedPlaceRepository,
        private val boundaries: BoundaryGeometrySource,
        @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) {

        private data class RevealInput(
            val points: List<TrailPoint>,
            val regions: List<UnlockedRegionRepository.UnlockedRegion>,
            val places: List<UnlockedPlaceRepository.UnlockedPlace>,
        )

        operator fun invoke(): Flow<FogLayers> =
            combine(
                repository.observePoints(),
                regionRepository.observeAll(),
                placeRepository.observeAll(),
            ) { points, regions, places -> RevealInput(points, regions, places) }
                .distinctUntilChanged()
                // Building is the expensive step: while one is running, only the newest input waits.
                .conflate()
                .map { input -> build(input) }
                .flowOn(defaultDispatcher)

        /** Everything hidden — shown before the first database emission. */
        fun emptyWorld(): FogLayers = composer.empty()

        /** The fog as it stood at [until] (epoch millis). Uses its own composer, so playback never disturbs the live cache. */
        suspend fun snapshot(input: Snapshot, until: Long): FogLayers =
            FogComposer().compose(
                points = input.points.filter { it.discoveredAt <= until },
                circles = input.places.filter { it.unlockedAt <= until }.map { it.toCircle() },
                regions = input.regions.filter { it.unlockedAt <= until }.mapNotNull { regionOf(it) },
            )

        /** Raw material for [snapshot], loaded once per playback. */
        class Snapshot(
            val points: List<TrailPoint>,
            val regions: List<UnlockedRegionRepository.UnlockedRegion>,
            val places: List<UnlockedPlaceRepository.UnlockedPlace>,
        )

        private suspend fun build(input: RevealInput): FogLayers =
            composer.compose(
                points = input.points,
                circles = input.places.map { it.toCircle() },
                regions = input.regions.mapNotNull { regionOf(it) },
            )

        private fun UnlockedPlaceRepository.UnlockedPlace.toCircle() =
            FogComposer.Circle(latitude, longitude, radiusMeters)

        private suspend fun regionOf(region: UnlockedRegionRepository.UnlockedRegion): FogComposer.Region? {
            val key = "${region.level}:${region.featureId}"
            val rings = ringsOf(key, region) ?: return null
            return FogComposer.Region(key, rings)
        }

        /** Reading an outline means decoding tiles, so each one is read at most once per process. */
        private suspend fun ringsOf(
            key: String,
            region: UnlockedRegionRepository.UnlockedRegion,
        ): List<Ring>? {
            regionRingCache[key]?.let { return it }
            val rings = boundaries.rings(region.level, region.featureId).getOrNull() ?: return null
            regionRingCache[key] = rings
            return rings
        }

        private val regionRingCache = java.util.Collections.synchronizedMap(
            object : LinkedHashMap<String, List<Ring>>(REGION_CACHE_SIZE + 1, 0.75f, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, List<Ring>>?,
                ): Boolean = size > REGION_CACHE_SIZE
            },
        )

        companion object {
            const val REGION_CACHE_SIZE: Int = 64
        }
    }
