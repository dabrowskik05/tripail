package com.tripex.pose.domain.location

/**
 * UI-facing start/stop for the location Foreground Service.
 * Implemented in `:app` (owns the Service Intent).
 */
interface TrackingController {
    fun startTracking()

    fun stopTracking()
}
