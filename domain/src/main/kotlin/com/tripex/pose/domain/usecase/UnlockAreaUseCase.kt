package com.tripex.pose.domain.usecase

import com.tripex.pose.domain.geo.H3Config
import com.tripex.pose.domain.geo.H3Converter
import com.tripex.pose.domain.geo.TrailCorridor
import com.tripex.pose.domain.location.DomainLocation
import com.tripex.pose.domain.location.TrackingSession
import com.tripex.pose.domain.repository.UnlockedAreaRepository
import javax.inject.Inject

/**
 * Converts a GPS fix into unlocked H3 cells and persists them.
 *
 * Each fix clears a [H3Config.WALK_REVEAL_RADIUS_M] disk. Between two fixes the same disk is
 * **swept along the gap** ([TrailCorridor]) rather than joined by a path of single cells: the old
 * `bridge` drew a thread about 25 m wide between disks 2 km across, which photographed as a row
 * of disconnected dots on a real drive.
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
            val cells = HashSet<Long>(INITIAL_CAPACITY)
            cells += h3.revealAround(
                lat = location.latitude,
                lng = location.longitude,
                radiusMeters = H3Config.WALK_REVEAL_RADIUS_M,
            )

            if (bridgeFrom != null) {
                for ((lat, lng) in TrailCorridor.stepsBetween(
                    fromLat = bridgeFrom.latitude,
                    fromLng = bridgeFrom.longitude,
                    toLat = location.latitude,
                    toLng = location.longitude,
                )) {
                    cells += h3.revealAround(lat, lng, H3Config.WALK_REVEAL_RADIUS_M)
                }
            }

            return repository.unlock(cells)
        }

        private companion object {
            /** One disk at walking resolution is ~1800 cells; a short corridor, a few times that. */
            const val INITIAL_CAPACITY = 4_096
        }
    }
