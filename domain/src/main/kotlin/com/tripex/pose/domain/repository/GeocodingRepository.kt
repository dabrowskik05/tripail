package com.tripex.pose.domain.repository

import com.tripex.pose.domain.geo.Place

/**
 * Forward geocoding — text query → coordinates.
 * Implementations must respect provider rate limits and cache identical queries.
 */
interface GeocodingRepository {
    /**
     * Ranked suggestions for a partial query. Results are deduplicated: providers happily return
     * the same settlement several times at different granularities.
     */
    suspend fun suggest(query: String): Result<List<Place>>

    /**
     * Which settlement contains this point, if any. Used by the automatic city unlock, so a
     * result smaller than a settlement is not interesting and comes back as `null`.
     */
    suspend fun reverseGeocode(lat: Double, lng: Double): Result<Place?>

    /**
     * Resolves [query] to the best matching place.
     * @return [Result.failure] when nothing found or the network call fails.
     */
    suspend fun search(query: String): Result<Place>
}
