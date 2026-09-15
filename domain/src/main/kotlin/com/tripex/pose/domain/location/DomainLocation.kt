package com.tripex.pose.domain.location

/**
 * Platform-agnostic location fix used by tracking pipeline and UseCases.
 */
data class DomainLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val elapsedRealtimeNanos: Long,
    val isMock: Boolean,
)
