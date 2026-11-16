package com.tripex.pose.domain.usecase

import com.tripex.pose.domain.geo.H3Config
import com.tripex.pose.domain.geo.H3Converter
import com.tripex.pose.domain.geo.TrailCorridor
import com.tripex.pose.domain.geo.TrailPoint
import com.tripex.pose.domain.geo.TravelMode
import com.tripex.pose.domain.location.DomainLocation
import com.tripex.pose.domain.location.TrackingSession
import com.tripex.pose.domain.repository.UnlockedAreaRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Turns a GPS fix into a trail point.
 *
 * The trail is a list of points snapped to a ~150 m grid; consecutive points share a segment and
 * are later drawn as one smooth corridor. Ground that is already on the trail is not stored
 * again — a daily commute adds nothing — but the points where a trip joins and leaves known
 * ground are kept, so the new corridor connects to the old one instead of stopping short.
 *
 * @return 1 when new ground was recorded, 0 otherwise.
 */
@Singleton
class UnlockAreaUseCase
    @Inject
    constructor(
        private val h3: H3Converter,
        private val repository: UnlockedAreaRepository,
    ) {
        private val mutex = Mutex()
        private var segmentId: Long? = null
        private var segmentMode: TravelMode? = null
        private var lastCell: Long? = null
        private var lastWasKnown = false

        suspend operator fun invoke(
            location: DomainLocation,
            bridgeFrom: TrackingSession.Fix?,
            mode: TravelMode = TravelMode.WALK,
            atMs: Long = System.currentTimeMillis(),
        ): Int = mutex.withLock {
            val cell = cellOf(location.latitude, location.longitude)
            val continuous = bridgeFrom != null && TrailCorridor.distanceMeters(
                bridgeFrom.latitude, bridgeFrom.longitude, location.latitude, location.longitude,
            ) <= H3Config.MAX_BRIDGE_DISTANCE_M
            if (!continuous) reset()

            // Where the line should start from if a new segment opens on this fix.
            val previousCell = lastCell ?: bridgeFrom?.takeIf { continuous }
                ?.let { cellOf(it.latitude, it.longitude) }
            if (cell == lastCell && mode == segmentMode) return@withLock 0

            if (repository.isKnown(cell, mode)) {
                // Close the open line on the first known point, then stay quiet.
                if (segmentId != null && !lastWasKnown && segmentMode == mode) {
                    store(cell, mode, checkNotNull(segmentId), atMs)
                }
                lastWasKnown = true
                lastCell = cell
                segmentMode = mode
                return@withLock 0
            }

            if (segmentId == null || lastWasKnown || segmentMode != mode) {
                val id = maxOf(atMs, (segmentId ?: 0L) + 1)
                segmentId = id
                if (previousCell != null && previousCell != cell) store(previousCell, mode, id, atMs)
            }
            store(cell, mode, checkNotNull(segmentId), atMs)
            segmentMode = mode
            lastCell = cell
            lastWasKnown = false
            1
        }

        /** One stand-alone point — "discover where I am", a photo, an imported position. */
        suspend fun single(
            latitude: Double,
            longitude: Double,
            atMs: Long = System.currentTimeMillis(),
        ): Int {
            val cell = cellOf(latitude, longitude)
            if (repository.isKnown(cell, TravelMode.UNKNOWN)) return 0
            store(cell, TravelMode.UNKNOWN, TrailPoint.SEGMENT_SINGLE, atMs)
            return 1
        }

        /** Many stand-alone points at once; duplicates of each other and of the trail are dropped. */
        suspend fun import(positions: List<Triple<Double, Double, Long>>): Int {
            val fresh = LinkedHashMap<Long, TrailPoint>()
            for ((latitude, longitude, atMs) in positions) {
                val cell = runCatching { cellOf(latitude, longitude) }.getOrNull() ?: continue
                if (cell in fresh || repository.isKnown(cell, TravelMode.UNKNOWN)) continue
                fresh[cell] = pointAt(cell, TravelMode.UNKNOWN, TrailPoint.SEGMENT_SINGLE, atMs)
            }
            repository.addPoints(fresh.map { it.key to it.value })
            return fresh.size
        }

        private fun reset() {
            segmentId = null
            segmentMode = null
            lastCell = null
            lastWasKnown = false
        }

        private fun cellOf(latitude: Double, longitude: Double): Long =
            h3.parentOf(h3.cellAt(latitude, longitude), H3Config.TRAIL_RESOLUTION)

        private fun pointAt(cell: Long, mode: TravelMode, segment: Long, atMs: Long): TrailPoint {
            val (latitude, longitude) = h3.cellCenter(cell)
            return TrailPoint(latitude, longitude, mode, segment, atMs)
        }

        private suspend fun store(cell: Long, mode: TravelMode, segment: Long, atMs: Long) {
            repository.addPoint(cell, pointAt(cell, mode, segment, atMs))
        }
    }
