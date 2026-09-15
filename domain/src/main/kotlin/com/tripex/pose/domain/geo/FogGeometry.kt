package com.tripex.pose.domain.geo

/**
 * Multipolygon in GeoJSON order: polygons → rings → (lng, lat).
 */
@JvmInline
value class FogGeometry(val polygons: List<List<List<Pair<Double, Double>>>>) {
    companion object {
        val EMPTY: FogGeometry = FogGeometry(emptyList())
    }
}
