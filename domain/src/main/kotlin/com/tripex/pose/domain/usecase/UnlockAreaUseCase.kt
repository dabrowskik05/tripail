package com.tripex.pose.domain.usecase

import com.tripex.pose.domain.geo.H3Converter
import com.tripex.pose.domain.location.DomainLocation
import com.tripex.pose.domain.repository.UnlockedAreaRepository
import javax.inject.Inject

/**
 * Converts a GPS fix into unlocked H3 cells (disk + optional bridge) and persists them.
 *
 * @return number of **newly** unlocked cells (for notification copy).
 */
class UnlockAreaUseCase @Inject constructor(
    private val h3: H3Converter,
    private val repository: UnlockedAreaRepository,
) {
    suspend operator fun invoke(
        location: DomainLocation,
        previous: DomainLocation?,
    ): Int {
        val disk = h3.revealDisk(location.latitude, location.longitude)
        val cells = if (previous == null) {
            disk
        } else {
            val from = h3.cellAt(previous.latitude, previous.longitude)
            val to = h3.cellAt(location.latitude, location.longitude)
            disk + h3.bridge(from, to)
        }
        return repository.unlock(cells)
    }
}
