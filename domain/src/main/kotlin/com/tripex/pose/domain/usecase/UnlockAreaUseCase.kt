package com.tripex.pose.domain.usecase

import com.tripex.pose.domain.geo.H3Config
import com.tripex.pose.domain.geo.H3Converter
import com.tripex.pose.domain.location.DomainLocation
import com.tripex.pose.domain.location.TrackingSession
import com.tripex.pose.domain.repository.UnlockedAreaRepository
import javax.inject.Inject

/**
 * Converts a GPS fix into unlocked H3 cells and persists them.
 *
 * Each fix clears a [H3Config.WALK_REVEAL_RADIUS_M] disk, and consecutive fixes are bridged so a
 * fast-moving player leaves a continuous swath instead of dots.
 *
 * [bridgeFrom] is a [TrackingSession.Fix] rather than a [DomainLocation] because it may have been
 * restored from disk after the process was killed (V3.7.3) — at that point its accuracy and
 * monotonic timestamp are long gone, and bridging never needed them anyway.
 *
 * @return number of **newly** unlocked cells.
 */
class UnlockAreaUseCase
    @Inject
    constructor(
        private val h3: H3Converter,
        private val repository: UnlockedAreaRepository,
    ) {
        suspend operator fun invoke(
            location: DomainLocation,
            bridgeFrom: TrackingSession.Fix?,
        ): Int {
            val disk = h3.revealAround(
                lat = location.latitude,
                lng = location.longitude,
                radiusMeters = H3Config.WALK_REVEAL_RADIUS_M,
            )
            val cells =
                if (bridgeFrom == null) {
                    disk
                } else {
                    val from = h3.cellAt(bridgeFrom.latitude, bridgeFrom.longitude)
                    val to = h3.cellAt(location.latitude, location.longitude)
                    disk + h3.bridge(from, to)
                }
            return repository.unlock(cells)
        }
    }
