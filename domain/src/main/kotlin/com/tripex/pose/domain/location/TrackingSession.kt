package com.tripex.pose.domain.location

/**
 * The little bit of tracking state that has to survive the process dying (V3.7.3).
 *
 * Two things live here and nothing else:
 * - [lastFix] — where the last accepted fix was, so the trail can be bridged across a restart
 *   instead of starting a fresh dot,
 * - [dwell] — how long the player has been standing still, so a restart four minutes into a
 *   five-minute stop does not send the timer back to zero.
 *
 * This is **not** a route log. Decision §8.2 pkt 9 is explicit: the database stores H3 cells and
 * nothing that reconstructs where somebody actually was. One row, overwritten in place.
 *
 * All timestamps are wall clock ([System.currentTimeMillis]), not `elapsedRealtime` — the latter
 * resets on reboot, which would make a restored dwell anchor look arbitrarily old or arbitrarily
 * fresh. Fix *age* checks stay on `elapsedRealtime` in [LocationFilter], where monotonicity is
 * what matters and persistence is not.
 */
data class TrackingSession(
    val lastFix: Fix? = null,
    val dwell: DwellDetector.State? = null,
) {
    data class Fix(
        val latitude: Double,
        val longitude: Double,
        val atMs: Long,
    )

    /**
     * A fix old enough that bridging to it would draw a straight line through places nobody went.
     *
     * Ten minutes at motorway speed is twenty kilometres. The trail is allowed to have a gap;
     * it is not allowed to claim a corridor the player never travelled.
     */
    fun bridgeableFix(nowMs: Long): Fix? =
        lastFix?.takeIf { nowMs - it.atMs <= LocationConfig.BRIDGE_MAX_AGE_MS }

    companion object {
        val EMPTY = TrackingSession()
    }
}
