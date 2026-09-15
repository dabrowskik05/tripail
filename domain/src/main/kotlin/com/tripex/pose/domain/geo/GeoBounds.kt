package com.tripex.pose.domain.geo

/**
 * Axis-aligned geographic bounding box (degrees).
 */
data class GeoBounds(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
)
