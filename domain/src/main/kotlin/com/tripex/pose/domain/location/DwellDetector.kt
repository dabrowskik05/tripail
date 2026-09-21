package com.tripex.pose.domain.location

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.cos

/**
 * Decides when the player has *stayed* somewhere rather than passed through it.
 *
 * This is the trigger for unlocking a whole city automatically: erasing a metropolis with a 1 km
 * brush is exactly the grinding the game refuses to ask for. Arriving and staying a while is the
 * signal that the player is actually there.
 *
 * Pure and stateless per call — the caller owns the state, so it is trivially testable and cannot
 * leak anything across a service restart.
 *
 * **The clock must be wall clock** ([System.currentTimeMillis]), not `elapsedRealtime`. The state
 * is persisted across process death and reboot (V3.7.3), and `elapsedRealtime` resets when the
 * phone restarts — a stored anchor would come back looking like it was set in the future. Fix
 * *age* checks are a different problem and stay monotonic; see [LocationFilter].
 */
@Singleton
class DwellDetector @Inject constructor() {

    data class State(
        val anchorLat: Double,
        val anchorLng: Double,
        /** Wall clock, so the anchor keeps its meaning across a restart. */
        val sinceMs: Long,
        val reported: Boolean = false,
    )

    /**
     * @param state previous state, or `null` on the first fix.
     * @param nowMs wall clock — see the class comment.
     * @return the new state, and whether this fix completes a dwell worth acting on.
     */
    fun update(
        state: State?,
        lat: Double,
        lng: Double,
        nowMs: Long,
    ): Pair<State, Boolean> {
        if (state == null || movedAway(state, lat, lng)) {
            return State(lat, lng, nowMs) to false
        }
        // A clock that jumped backwards (NTP correction, user editing the time) would otherwise
        // freeze the timer forever. Re-anchoring costs at most one dwell; a stuck timer costs all
        // of them.
        if (nowMs < state.sinceMs) return State(lat, lng, nowMs) to false
        val dwelledLongEnough = nowMs - state.sinceMs >= DWELL_MILLIS
        if (dwelledLongEnough && !state.reported) {
            return state.copy(reported = true) to true
        }
        return state to false
    }

    private fun movedAway(state: State, lat: Double, lng: Double): Boolean {
        val dLat = abs(lat - state.anchorLat) * METRES_PER_DEGREE
        val dLng = abs(lng - state.anchorLng) * METRES_PER_DEGREE *
            cos(Math.toRadians(state.anchorLat)).coerceAtLeast(MIN_COS)
        return dLat > DWELL_RADIUS_M || dLng > DWELL_RADIUS_M
    }

    companion object {
        /** Long enough to mean "I am here", short enough to feel automatic. */
        const val DWELL_MILLIS: Long = 5 * 60 * 1000

        /** Roughly a neighbourhood: walking around town does not reset the timer. */
        const val DWELL_RADIUS_M: Double = 1_200.0

        private const val METRES_PER_DEGREE = 111_320.0
        private const val MIN_COS = 0.01
    }
}
