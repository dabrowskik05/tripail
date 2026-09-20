package com.tripex.pose.domain.usecase

import com.tripex.pose.domain.repository.UnlockedAreaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Observes the total number of unlocked H3 cells. */
class ObserveUnlockedCountUseCase
    @Inject
    constructor(
        private val repository: UnlockedAreaRepository,
    ) {
        operator fun invoke(): Flow<Int> = repository.observeCount()
    }
