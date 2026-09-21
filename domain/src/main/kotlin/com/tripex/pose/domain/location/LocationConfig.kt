package com.tripex.pose.domain.location

/**
 * Single source of truth for GPS sampling and filter thresholds (GPS_SERVICE_SPEC §5.1 / §6.1).
 */
data class LocationConfig(
    val intervalMs: Long = INTERVAL_MS,
    val fastestIntervalMs: Long = FASTEST_MS,
    val minDistanceMeters: Float = MIN_DISTANCE_M,
    val maxUpdateDelayMs: Long = BATCH_MS,
    val maxAccuracyMeters: Float = MAX_ACCURACY_M,
    val maxAgeMs: Long = MAX_AGE_MS,
    val minJitterDistanceMeters: Float = MIN_JITTER_DISTANCE_M,
) {
    companion object {
        const val INTERVAL_MS: Long = 15_000L
        const val FASTEST_MS: Long = 10_000L
        const val MIN_DISTANCE_M: Float = 50f
        const val BATCH_MS: Long = 30_000L
        const val MAX_ACCURACY_M: Float = 50f
        const val MAX_AGE_MS: Long = 30_000L
        const val MIN_JITTER_DISTANCE_M: Float = 20f

        /**
         * Past this age a restored fix is a memory, not a starting point (V3.7.3).
         *
         * Bridging to it would paint a straight corridor of unlocked cells across everything
         * between — at motorway speed, twenty kilometres of places nobody visited.
         */
        const val BRIDGE_MAX_AGE_MS: Long = 10 * 60 * 1000L

        /**
         * Silence long enough to mean something is wrong rather than "standing indoors".
         *
         * A service that is alive and collecting nothing is worse than one that crashed: the
         * notification says it is working. After this long the notification says otherwise and
         * the stream is re-subscribed.
         */
        const val GPS_SILENCE_TIMEOUT_MS: Long = 2 * 60 * 1000L

        val DEFAULT: LocationConfig = LocationConfig()
    }
}
