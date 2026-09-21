package com.tripex.pose.data.repository

import com.tripex.pose.data.BuildConfig
import com.tripex.pose.data.local.AreaStatsDao
import com.tripex.pose.data.local.AreaStatsEntity
import com.tripex.pose.domain.repository.AreaStatsCache
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed coverage denominator cache (M3.4).
 *
 * The boundary bundle version lives in `BuildConfig`, so invalidation stays here and the domain
 * never learns that build config exists.
 */
@Singleton
internal class AreaStatsCacheImpl
    @Inject
    constructor(
        private val dao: AreaStatsDao,
    ) : AreaStatsCache {

        private val boundariesVersion: Int get() = BuildConfig.BOUNDARIES_VERSION

        override suspend fun denominator(areaKey: String): AreaStatsCache.Denominator? =
            dao.find(areaKey, boundariesVersion)
                ?.let { AreaStatsCache.Denominator(resolution = it.resolution, cellCount = it.cellCount) }

        override suspend fun put(areaKey: String, resolution: Int, cellCount: Int) {
            dao.upsert(
                AreaStatsEntity(
                    areaKey = areaKey,
                    resolution = resolution,
                    cellCount = cellCount,
                    boundariesVersion = boundariesVersion,
                ),
            )
            // Rows from an older bundle can never be reused; drop them rather than let them rot.
            dao.deleteStaleVersions(boundariesVersion)
        }
    }
