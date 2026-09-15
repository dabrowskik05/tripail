package com.tripex.pose.domain.location

import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Rejects low-quality GPS fixes before they hit H3 / Room (GPS_SERVICE_SPEC §6.1).
 * Pure Kotlin — [nowElapsedRealtimeNanos] is supplied by the caller.
 */
class LocationFilter @Inject constructor() {
    private val config: LocationConfig = LocationConfig.DEFAULT

    fun shouldAccept(
        candidate: DomainLocation,
        lastAccepted: DomainLocation?,
        nowElapsedRealtimeNanos: Long,
        allowMock: Boolean = false,
    ): Boolean {
        if (candidate.accuracyMeters > config.maxAccuracyMeters) return false
        if (candidate.isMock && !allowMock) return false

        val ageNanos = nowElapsedRealtimeNanos - candidate.elapsedRealtimeNanos
        if (ageNanos > config.maxAgeMs * 1_000_000L) return false

        if (lastAccepted != null) {
            val distanceM = haversineMeters(lastAccepted, candidate)
            if (distanceM < config.minJitterDistanceMeters) return false
        }
        return true
    }

    private fun haversineMeters(a: DomainLocation, b: DomainLocation): Double {
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
    }

    private companion object {
        const val EARTH_RADIUS_M = 6_371_000.0
    }
}
