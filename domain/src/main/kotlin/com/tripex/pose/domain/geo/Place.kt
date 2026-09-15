package com.tripex.pose.domain.geo

/**
 * A geocoded place from Nominatim (or another provider).
 */
data class Place(
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val boundingBox: GeoBounds? = null,
)
