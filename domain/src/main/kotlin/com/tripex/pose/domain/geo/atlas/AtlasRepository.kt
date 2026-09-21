package com.tripex.pose.domain.geo.atlas

/**
 * Overview atlas (Natural Earth land) for Compose Canvas and land-mask fog.
 * Country/region geometries come from [BoundaryGeometrySource], not here.
 */
interface AtlasRepository {
    suspend fun land(): LandShape
    suspend fun continents(): List<ContinentShape>

    /** Drop in-memory cache (e.g. on process low-memory). */
    fun clearCache()
}
