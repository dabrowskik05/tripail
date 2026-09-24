package com.tripex.pose.domain.location

import kotlinx.coroutines.flow.Flow

/**
 * Continuous location stream. Implementations live in `:data`.
 * Throws [SecurityException] when location permission is missing.
 */
interface LocationTracker {
    fun locationUpdates(config: LocationConfig = LocationConfig.DEFAULT): Flow<DomainLocation>

    /**
     * One fresh fix, for "where is the player right now" questions outside the tracking loop.
     *
     * The tracking service only records a fix after 50 m of movement, so a player sitting still
     * for a day has no recent fix on disk at all — which is why entering their own continent
     * only sometimes flew the camera to them.
     *
     * @return `null` without permission, with location off, or when nothing arrives in time —
     *   never throws.
     */
    suspend fun currentLocation(): DomainLocation?
}
