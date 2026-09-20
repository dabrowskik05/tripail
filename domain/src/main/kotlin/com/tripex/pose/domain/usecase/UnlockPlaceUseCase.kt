package com.tripex.pose.domain.usecase

import com.tripex.pose.domain.geo.H3Config
import com.tripex.pose.domain.geo.H3Converter
import com.tripex.pose.domain.geo.Place
import com.tripex.pose.domain.repository.GeocodingRepository
import com.tripex.pose.domain.repository.UnlockedAreaRepository
import javax.inject.Inject

data class UnlockPlaceResult(
    val place: Place,
    val newlyUnlocked: Int,
)

/**
 * Geocodes a place name and unlocks ~[H3Config.MANUAL_UNLOCK_RADIUS_M] around it.
 */
class UnlockPlaceUseCase
    @Inject
    constructor(
        private val geocodingRepository: GeocodingRepository,
        private val h3: H3Converter,
        private val unlockedAreaRepository: UnlockedAreaRepository,
    ) {
        suspend operator fun invoke(
            query: String,
            radiusMeters: Double = H3Config.MANUAL_UNLOCK_RADIUS_M,
        ): Result<UnlockPlaceResult> {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) {
                return Result.failure(IllegalArgumentException("Empty query"))
            }
            return geocodingRepository.search(trimmed).mapCatching { place ->
                val cells = h3.revealAround(place.latitude, place.longitude, radiusMeters)
                val newlyUnlocked = unlockedAreaRepository.unlock(cells)
                UnlockPlaceResult(place = place, newlyUnlocked = newlyUnlocked)
            }
        }
    }
