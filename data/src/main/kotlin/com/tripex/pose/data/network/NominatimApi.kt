package com.tripex.pose.data.network

import retrofit2.http.GET
import retrofit2.http.Query

internal interface NominatimApi {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 1,
    ): List<NominatimPlaceDto>
}
