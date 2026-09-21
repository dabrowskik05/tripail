package com.tripex.pose.domain.usecase

import com.tripex.pose.core.di.DefaultDispatcher
import com.tripex.pose.domain.geo.AreaCoverage
import com.tripex.pose.domain.geo.CoverageResolutionPolicy
import com.tripex.pose.domain.geo.GeoCircle
import com.tripex.pose.domain.geo.H3Converter
import com.tripex.pose.domain.geo.atlas.AreaKey
import com.tripex.pose.domain.geo.atlas.AtlasRepository
import com.tripex.pose.domain.geo.atlas.BoundaryGeometrySource
import com.tripex.pose.domain.geo.atlas.Ring
import com.tripex.pose.domain.geo.atlas.boundaryId
import com.tripex.pose.domain.geo.atlas.boundaryLevel
import com.tripex.pose.domain.geo.projection.GeometryOps
import com.tripex.pose.domain.repository.AreaStatsCache
import com.tripex.pose.domain.repository.UnlockedAreaRepository
import com.tripex.pose.domain.repository.UnlockedPlaceRepository
import com.tripex.pose.domain.repository.UnlockedRegionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Real discovery percentage for a continent, country or region (M3.4), without touching how
 * user data is stored — the database still holds nothing but walking-resolution indices.
 *
 * ```
 * polygon from PMTiles  ──cellsForPolygon(res)──►  area cells        (denominator)
 * unlocked res 11       ──parentOf(res)─────────►  user parents
 *                                    intersection ─────────────────►  numerator
 * ```
 *
 * The numerator reads [UnlockedAreaRepository.observeFar], which already returns distinct
 * resolution-7 parents, and lifts them to the coverage resolution. Coverage resolutions are
 * always 7 or coarser (see [CoverageResolutionPolicy]), so lifting is always well defined, and
 * the alternative — expanding area cells down to resolution 7 for an `IN` clause — would mean
 * millions of parameters for a country the size of Russia.
 *
 * **Known limit of the persistent cache.** The plan asks for `area_stats` to hold numbers rather
 * than geometry *and* for a repeat visit to read the cache instead of recomputing. Both cannot
 * hold at once: the intersection needs the denominator **set**, and a set of cell indices is
 * geometry. So the Room cache supplies the resolution, the stable denominator across restarts and
 * the bundle-version invalidation hook, while the set itself is memoised per process. The first
 * visit to an area after a cold start still pays for one `cellsForPolygon`.
 */
@Singleton
class ObserveAreaCoverageUseCase
    @Inject
    constructor(
        private val repository: UnlockedAreaRepository,
        private val boundaries: BoundaryGeometrySource,
        private val atlas: AtlasRepository,
        private val areaStats: AreaStatsCache,
        private val regionRepository: UnlockedRegionRepository,
        private val placeRepository: UnlockedPlaceRepository,
        private val h3: H3Converter,
        @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) {
        /**
         * Denominator cell sets for the areas visited in this process.
         *
         * [AreaStatsCache] persists the *count*, which is what survives process death and what
         * bundle-version invalidation hangs off. The intersection needs the actual set, and a set
         * is geometry-shaped data that the plan deliberately keeps out of the database — so it
         * lives here, bounded, for as long as the process does.
         */
        private val areaCellsMemo = object : LinkedHashMap<String, Set<Long>>(MEMO_SIZE + 1, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Set<Long>>?): Boolean =
                size > MEMO_SIZE
        }
        private val memoMutex = Mutex()

        operator fun invoke(area: AreaKey): Flow<AreaCoverage> =
            flow {
                val rings = ringsFor(area)
                if (rings.isNullOrEmpty()) {
                    emit(AreaCoverage.Unavailable)
                    return@flow
                }

                val navArg = area.toNavArg()
                val cached = areaStats.denominator(navArg)
                val resolution = cached?.resolution ?: resolutionFor(rings)
                val areaCells = areaCells(area, rings, resolution)
                if (areaCells.isEmpty()) {
                    emit(AreaCoverage.Unavailable)
                    return@flow
                }
                if (cached == null || cached.cellCount != areaCells.size) {
                    areaStats.put(navArg, resolution, areaCells.size)
                }

                emitAll(
                    combine(
                        repository.observeFar(),
                        regionRepository.observeAll(),
                        placeRepository.observeAll(),
                    ) { parents, regions, places ->
                        // Two kinds of ownership meet here: individual walked cells, and whole
                        // regions held as ids. Both are counted in the same units.
                        val discovered = HashSet<Long>(parents.size)
                        for (parent in parents) {
                            val lifted = h3.parentOf(parent, resolution)
                            if (areaCells.contains(lifted)) discovered += lifted
                        }
                        for (region in regions) {
                            discovered += regionCells(region, resolution).filterTo(HashSet()) {
                                areaCells.contains(it)
                            }
                        }
                        for (place in places) {
                            val ring = GeoCircle.ring(place.latitude, place.longitude, place.radiusMeters)
                            discovered += h3.cellsForPolygon(listOf(ring), resolution)
                                .filterTo(HashSet()) { areaCells.contains(it) }
                        }
                        AreaCoverage.of(
                            discoveredCells = discovered.size,
                            areaCells = areaCells.size,
                            resolution = resolution,
                        )
                    },
                )
            }
                .distinctUntilChanged()
                .flowOn(defaultDispatcher)

        /**
         * Countries and regions come from the boundary bundle; continents have no entry there and
         * come from the overview atlas instead. Cities have neither — they only ever arrive from
         * the geocoder, so there is nothing to measure.
         */
        private suspend fun ringsFor(area: AreaKey): List<Ring>? = when (area) {
            is AreaKey.Continent ->
                runCatching { atlas.continents() }.getOrNull()
                    ?.firstOrNull { it.id == area.id }
                    ?.rings

            else -> {
                val level = area.boundaryLevel()
                val id = area.boundaryId()
                if (level == null || id == null) null else boundaries.rings(level, id).getOrNull()
            }
        }

        private fun resolutionFor(rings: List<Ring>): Int =
            CoverageResolutionPolicy.resolutionFor(GeometryOps.areaKm2(GeometryOps.boundsOf(rings)))

        /** Cells of a whole-region unlock, measured in the same units as the denominator. */
        private suspend fun regionCells(
            region: UnlockedRegionRepository.UnlockedRegion,
            resolution: Int,
        ): Set<Long> {
            val key = "region:${region.level}:${region.featureId}@$resolution"
            memoMutex.withLock {
                areaCellsMemo[key]?.let { return it }
                val rings = boundaries.rings(region.level, region.featureId).getOrNull()
                    ?: return emptySet()
                val cells = h3.cellsForPolygon(rings, resolution)
                areaCellsMemo[key] = cells
                return cells
            }
        }

        private suspend fun areaCells(
            area: AreaKey,
            rings: List<Ring>,
            resolution: Int,
        ): Set<Long> {
            val key = "${area.toNavArg()}@$resolution"
            memoMutex.withLock {
                areaCellsMemo[key]?.let { return it }
                val cells = h3.cellsForPolygon(rings, resolution)
                areaCellsMemo[key] = cells
                return cells
            }
        }

        private companion object {
            /** A drill-down session realistically touches a handful of areas, not hundreds. */
            const val MEMO_SIZE = 8
        }
    }
