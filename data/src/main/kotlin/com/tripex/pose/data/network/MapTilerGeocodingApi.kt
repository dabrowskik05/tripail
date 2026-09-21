package com.tripex.pose.data.network

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * MapTiler Geocoding. Autocomplete-friendly, unlike Nominatim, so a keystroke-driven search is
 * allowed here — see `docs/GEOCODING_API_RESEARCH.md`.
 */
internal interface MapTilerGeocodingApi {

    @GET("geocoding/{query}.json")
    suspend fun search(
        @Path("query") query: String,
        @Query("key") key: String,
        @Query("language") language: String,
        @Query("autocomplete") autocomplete: Boolean = true,
        @Query("limit") limit: Int = 8,
    ): MapTilerResponseDto

    /**
     * Reverse geocoding — coordinates to place. Backs the automatic city unlock, so it is asked
     * only for settlement-sized types; a street address would be useless here.
     */
    @GET("geocoding/{lng},{lat}.json")
    suspend fun reverse(
        @Path("lng") lng: Double,
        @Path("lat") lat: Double,
        @Query("key") key: String,
        @Query("language") language: String,
        @Query("types") types: String = "municipality,municipal_district,place",
        @Query("limit") limit: Int = 1,
    ): MapTilerResponseDto
}
