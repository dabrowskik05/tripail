package com.tripex.pose.domain.repository

import com.tripex.pose.domain.geo.Place

/**
 * Forward geocoding — text query → coordinates.
 * Implementations must respect provider rate limits and cache identical queries.
 */
interface GeocodingRepository {
    /**
     * Resolves [query] to the best matching place.
     * @return [Result.failure] when nothing found or the network call fails.
     */
    suspend fun search(query: String): Result<Place>
}
