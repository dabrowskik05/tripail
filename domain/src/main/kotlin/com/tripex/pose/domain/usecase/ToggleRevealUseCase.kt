package com.tripex.pose.domain.usecase

import com.tripex.pose.domain.repository.UnlockedPlaceRepository
import com.tripex.pose.domain.repository.UnlockedRegionRepository
import javax.inject.Inject

/**
 * Reveals or covers whatever the player selected (V3.4.5).
 *
 * The counterpart to [ResolveSearchSelectionUseCase]: that one describes, this one acts, and the
 * button between them is the player's decision. Both directions are deliberately here together —
 * "Reveal" and "Cover" are the same commitment read in opposite directions, and splitting them
 * across two classes invited them to drift.
 */
class ToggleRevealUseCase
    @Inject
    constructor(
        private val unlockRegion: UnlockRegionUseCase,
        private val regions: UnlockedRegionRepository,
        private val unlockPlace: UnlockPlaceUseCase,
    ) {
        suspend fun reveal(selection: SearchSelection): Result<Unit> = runCatching {
            when (val target = selection.target) {
                is RevealTarget.Region ->
                    unlockRegion(target.level, target.featureId).getOrThrow()

                is RevealTarget.Circle ->
                    unlockPlace.unlock(
                        place = target.place,
                        radiusMeters = target.radiusMeters,
                        source = UnlockedPlaceRepository.Source.Manual,
                    ).getOrThrow()

                // A country is navigated into, never claimed whole.
                is RevealTarget.Country -> Unit
            }
        }

        suspend fun cover(selection: SearchSelection): Result<Unit> = runCatching {
            if (!selection.canCover) return@runCatching
            when (val target = selection.target) {
                is RevealTarget.Region -> regions.lock(target.level, target.featureId)
                is RevealTarget.Circle -> unlockPlace.lock(target.place)
                is RevealTarget.Country -> Unit
            }
        }
    }
