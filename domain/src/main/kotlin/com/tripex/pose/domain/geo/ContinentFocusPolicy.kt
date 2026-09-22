package com.tripex.pose.domain.geo

import com.tripex.pose.domain.location.TrackingSession
import kotlin.math.max

/**
 * Decides whether entering a continent should end on the player, and how close (V3.3.6).
 *
 * The order is the point. The continent is framed first, whole, so the player can see *where*
 * they are; only then does the camera settle onto them. Flying straight to the player would be
 * faster and would throw away the context that makes the drill-down readable.
 *
 * Pure decision, no I/O — the caller supplies the last known fix and the clock.
 */
object ContinentFocusPolicy {

    /**
     * Half-span of the box the camera lands in, in degrees of latitude.
     *
     * About 440 km across: a country-sized window, which is the scale the player acts at. Tighter
     * would arrive at street level having skipped the whole journey.
     */
    const val FOCUS_HALF_SPAN_DEG: Double = 2.0

    /**
     * Past a day old, a fix says where the phone was, not where it is.
     *
     * Flying to yesterday's city is worse than not flying at all: it looks like the app knows
     * something, and it is wrong.
     */
    const val MAX_FIX_AGE_MS: Long = 24L * 60 * 60 * 1000

    /**
     * @return where to settle the camera, or `null` to leave the continent framing alone —
     *   no fix, a stale fix, or a player who is somewhere else entirely.
     */
    fun focus(
        continent: GeoBounds,
        fix: TrackingSession.Fix?,
        nowMs: Long,
    ): GeoBounds? {
        if (fix == null) return null
        if (nowMs - fix.atMs > MAX_FIX_AGE_MS) return null
        if (!ContinentBounds.contains(continent, fix.latitude, fix.longitude)) return null

        // Longitude degrees are shorter away from the equator, so a square on screen needs a
        // wider box in degrees the further north the player is.
        val lngHalfSpan = FOCUS_HALF_SPAN_DEG /
            max(kotlin.math.cos(Math.toRadians(fix.latitude)), MIN_COS)

        return GeoBounds(
            north = (fix.latitude + FOCUS_HALF_SPAN_DEG).coerceAtMost(MAX_LATITUDE),
            south = (fix.latitude - FOCUS_HALF_SPAN_DEG).coerceAtLeast(-MAX_LATITUDE),
            east = (fix.longitude + lngHalfSpan).coerceAtMost(MAX_LONGITUDE),
            west = (fix.longitude - lngHalfSpan).coerceAtLeast(-MAX_LONGITUDE),
        )
    }

    /**
     * Box the camera may not leave while exploring this continent (V3.3.7).
     *
     * The margin is a share of the continent's own span rather than a fixed number of degrees:
     * five degrees of slack is room to breathe around Europe and a rounding error around Asia.
     */
    fun cameraLimit(continent: GeoBounds, marginFraction: Double = LIMIT_MARGIN): GeoBounds {
        val latMargin = (continent.north - continent.south) * marginFraction
        val lngMargin = (continent.east - continent.west) * marginFraction
        return GeoBounds(
            north = (continent.north + latMargin).coerceAtMost(MAX_LATITUDE),
            south = (continent.south - latMargin).coerceAtLeast(-MAX_LATITUDE),
            east = (continent.east + lngMargin).coerceAtMost(MAX_LONGITUDE),
            west = (continent.west - lngMargin).coerceAtLeast(-MAX_LONGITUDE),
        )
    }

    /** Generous on purpose: a cage the player can feel is worse than one they never reach. */
    const val LIMIT_MARGIN: Double = 0.35

    private const val MIN_COS = 0.2
    private const val MAX_LATITUDE = 85.0
    private const val MAX_LONGITUDE = 180.0
}
