package com.tripex.pose.domain.usecase

import com.tripex.pose.core.di.DefaultDispatcher
import com.tripex.pose.domain.geo.FogGeoJsonBuilder
import com.tripex.pose.domain.geo.FogGeometry
import com.tripex.pose.domain.geo.FogLod
import com.tripex.pose.domain.geo.GeoCircle
import com.tripex.pose.domain.geo.H3Config
import com.tripex.pose.domain.geo.H3Converter
import com.tripex.pose.domain.geo.MapViewport
import com.tripex.pose.domain.geo.RevealUnion
import com.tripex.pose.domain.geo.atlas.BoundaryGeometrySource
import com.tripex.pose.domain.geo.atlas.Ring
import com.tripex.pose.domain.repository.UnlockedAreaRepository
import com.tripex.pose.domain.repository.UnlockedPlaceRepository
import com.tripex.pose.domain.repository.UnlockedRegionRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Streams the reveal-wash GeoJSON for the current camera.
 *
 * Two rules govern this class, both learned the hard way on a real drive:
 *
 * 1. **Everything is unioned together, in one pass** (V3.1.5). Walked cells, city circles and
 *    whole regions go into [RevealUnion] as one list. They used to be punched into the world
 *    polygon as separate holes, and overlapping holes are undefined for the tessellator — the
 *    overlap rendered as fog. Merging in stages would leave the same seams, so there is no
 *    staged path here and no way to hand a shape to the builder unmerged.
 *
 * 2. **Nothing is dropped for being old** (V3.1.6). What varies with the camera is the
 *    *resolution* the trail is drawn at, never how much of it exists. See [FogLod].
 */
class ObserveFogGeoJsonUseCase
    @Inject
    constructor(
        private val repository: UnlockedAreaRepository,
        private val h3: H3Converter,
        private val fogGeoJsonBuilder: FogGeoJsonBuilder,
        private val revealUnion: RevealUnion,
        private val regionRepository: UnlockedRegionRepository,
        private val placeRepository: UnlockedPlaceRepository,
        private val boundaries: BoundaryGeometrySource,
        @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) {

        /** Everything needed to draw one frame of wash. Compared as a whole to skip repeat work. */
        private data class RevealInput(
            val lod: FogLod,
            val cells: List<Long>,
            val regions: List<UnlockedRegionRepository.UnlockedRegion>,
            val places: List<UnlockedPlaceRepository.UnlockedPlace>,
        )

        @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
        operator fun invoke(viewport: Flow<MapViewport>): Flow<String> =
            viewport
                .debounce(CAMERA_DEBOUNCE_MS)
                .map { it.toQuery() }
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    combine(
                        cellsFor(query),
                        regionRepository.observeAll(),
                        placeRepository.observeAll(),
                    ) { cells, regions, places ->
                        RevealInput(query.lod, cells, regions, places)
                    }
                }
                // The union is the expensive step; an identical input must not pay for it twice.
                .distinctUntilChanged()
                .map { input -> buildJson(input) }
                .distinctUntilChanged()
                .flowOn(defaultDispatcher)

        /** Full-world wash with nothing revealed — shown before the first Room emission. */
        fun emptyWorld(): String = fogGeoJsonBuilder.build(FogGeometry.EMPTY)

        /**
         * What the camera means for the query: which resolution, and which slice of the world.
         *
         * The viewport's parent cells are part of the key, so panning inside the same tiles does
         * not re-query, while panning to new ground does.
         */
        private data class Query(val lod: FogLod, val viewportParents: Set<Long>)

        private fun MapViewport.toQuery(): Query {
            val lod = FogLod.forZoom(zoom)
            if (!lod.isViewportScoped) return Query(FogLod.Far, emptySet())

            val parents = h3.cellsForBounds(bounds, H3Config.LOD_FAR_RESOLUTION)
            // A viewport-scoped query becomes `WHERE parentRes7 IN (…)`, one bind variable per
            // cell. SQLite refuses past ~999 of them, so a wide viewport would not merely be slow
            // — it would throw. Past the cap the camera is far enough out that the coarse global
            // query is the right answer anyway, so fall back to it rather than clamping the set
            // and silently drawing a slice of what was asked for.
            if (parents.size > MAX_VIEWPORT_PARENTS) return Query(FogLod.Far, emptySet())

            return Query(lod, parents)
        }

        private fun cellsFor(query: Query): Flow<List<Long>> = when (query.lod) {
            FogLod.Near -> repository.observeDetailed(query.viewportParents)
            FogLod.Mid -> repository.observeMid(query.viewportParents)
            // No viewport, no limit: discovered ground stays discovered wherever the camera is.
            FogLod.Far -> repository.observeFar()
        }

        private suspend fun buildJson(input: RevealInput): String {
            val shapes = ArrayList<List<Ring>>(input.regions.size + input.places.size + 8)

            // 1. The walked trail, already merged cell-to-cell by H3 — holes and all.
            if (input.cells.isNotEmpty()) {
                shapes += h3.outline(input.cells).polygons
            }
            // 2. Whole regions, read back from the bundle on demand (the database holds only ids).
            for (region in input.regions) {
                val rings = ringsOf(region) ?: continue
                // Each ring is treated as its own outline: a region can be several landmasses,
                // and the union is what decides how they relate.
                for (ring in rings) shapes += listOf(ring)
            }
            // 3. Cities — three stored numbers turned back into a circle.
            for (place in input.places) {
                shapes += listOf(GeoCircle.ring(place.latitude, place.longitude, place.radiusMeters))
            }

            return fogGeoJsonBuilder.build(revealUnion.union(shapes))
        }

        /** Reading an outline means decoding tiles, so each one is read at most once per process. */
        private suspend fun ringsOf(
            region: UnlockedRegionRepository.UnlockedRegion,
        ): List<Ring>? {
            val key = "${region.level}:${region.featureId}"
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
            const val CAMERA_DEBOUNCE_MS: Long = 250L
            const val REGION_CACHE_SIZE: Int = 32

            /**
             * Bind-variable budget for a viewport-scoped query.
             *
             * SQLite's default `SQLITE_MAX_VARIABLE_NUMBER` is 999; staying well under it leaves
             * room for Room's own parameters and for the limit moving between devices.
             */
            const val MAX_VIEWPORT_PARENTS: Int = 500
        }
    }
