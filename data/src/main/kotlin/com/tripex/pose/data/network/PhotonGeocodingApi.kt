package com.tripex.pose.data.network

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Photon (komoot) — free OpenStreetMap geocoder built for search-as-you-type, no API key.
 *
 * The public instance only speaks `default` (each place's local name), `en`, `de` and `fr`.
 * Polish exonyms are **not** indexed: "Rzym" finds the Polish village, not Rome. Accepted for now
 * (search in local or English names); see `docs/PLAN.md` E2.
 *
 * Fair use: the app debounces typing into one pipeline (`SearchViewModel`) and caches every
 * non-empty answer in Room, so a user generates a handful of requests per search.
 */
internal interface PhotonGeocodingApi {

    @GET("api/")
    suspend fun search(
        @Query("q") query: String,
        @Query("lang") language: String,
        @Query("limit") limit: Int = 10,
    ): PhotonResponseDto

    /**
     * Coordinates → place, restricted to one [layer] (`city`, `locality`…). Without a layer the
     * nearest object wins — a shop or a bus stop instead of the town around it.
     */
    @GET("reverse")
    suspend fun reverse(
        @Query("lat") lat: Double,
        @Query("lon") lng: Double,
        @Query("lang") language: String,
        @Query("layer") layer: String,
        @Query("limit") limit: Int = 1,
    ): PhotonResponseDto
}
