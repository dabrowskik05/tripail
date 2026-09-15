package com.tripex.pose.domain.location

import kotlinx.coroutines.flow.Flow

/**
 * Continuous location stream. Implementations live in `:data`.
 * Throws [SecurityException] when location permission is missing.
 */
interface LocationTracker {
    fun locationUpdates(config: LocationConfig = LocationConfig.DEFAULT): Flow<DomainLocation>
}
