package com.tripex.pose.domain.location

/**
 * Persistence for [TrackingSession] — one record, overwritten in place (V3.7.3).
 *
 * Written on every accepted fix, so implementations must be cheap and must never block the
 * collector. Losing the newest write costs one bridge segment; blocking the location pipeline
 * costs the whole trail.
 */
interface TrackingSessionRepository {

    suspend fun load(): TrackingSession

    suspend fun save(session: TrackingSession)

    /** Explicit stop only — a restart must never land here. */
    suspend fun clear()
}
