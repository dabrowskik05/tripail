package com.tripex.pose.domain.repository

/**
 * Cached denominator of an area coverage ratio (M3.4).
 *
 * Stores **numbers, never geometry**. Invalidation against the shipped boundary bundle version
 * is the implementation's job, so the domain never learns about build config.
 */
interface AreaStatsCache {

    data class Denominator(
        val resolution: Int,
        val cellCount: Int,
    )

    /** `null` when nothing is cached, or when the cached row predates the current bundle. */
    suspend fun denominator(areaKey: String): Denominator?

    suspend fun put(areaKey: String, resolution: Int, cellCount: Int)
}
