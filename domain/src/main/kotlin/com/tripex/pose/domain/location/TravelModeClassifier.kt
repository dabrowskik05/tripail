package com.tripex.pose.domain.location

import com.tripex.pose.domain.geo.TrailCorridor
import com.tripex.pose.domain.geo.TravelMode

/**
 * Walking or riding, judged from speed alone — no extra permission, nothing for the player to
 * pick.
 *
 * Switching to [TravelMode.VEHICLE] is immediate; switching back needs a few slow fixes in a row,
 * so a traffic jam does not turn a motorway into a walk.
 */
class TravelModeClassifier {
    private var current = TravelMode.WALK
    private var slowStreak = 0

    fun update(previous: TrackingSession.Fix?, location: DomainLocation, nowMs: Long): TravelMode {
        if (previous == null) {
            current = TravelMode.WALK
            slowStreak = 0
            return current
        }
        val seconds = (nowMs - previous.atMs) / 1000.0
        if (seconds <= 0.5) return current
        val metres = TrailCorridor.distanceMeters(
            previous.latitude, previous.longitude, location.latitude, location.longitude,
        )
        val kmh = metres / seconds * 3.6
        when {
            kmh >= VEHICLE_KMH -> {
                current = TravelMode.VEHICLE
                slowStreak = 0
            }
            kmh <= WALK_KMH -> {
                slowStreak += 1
                if (slowStreak >= SLOW_FIXES_TO_WALK) current = TravelMode.WALK
            }
            else -> slowStreak = 0
        }
        return current
    }

    companion object {
        const val VEHICLE_KMH = 25.0
        const val WALK_KMH = 10.0
        const val SLOW_FIXES_TO_WALK = 3
    }
}
