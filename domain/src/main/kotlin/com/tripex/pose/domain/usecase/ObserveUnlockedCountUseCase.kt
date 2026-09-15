package com.tripex.pose.domain.usecase

import com.tripex.pose.domain.repository.UnlockedAreaRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Observes the total number of unlocked H3 cells. */
class ObserveUnlockedCountUseCase @Inject constructor(
    private val repository: UnlockedAreaRepository,
) {
    operator fun invoke(): Flow<Int> = repository.observeCount()
}
