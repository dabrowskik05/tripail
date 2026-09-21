package com.tripex.pose.domain.usecase

import com.tripex.pose.domain.geo.Place
import com.tripex.pose.domain.geo.RevealRadiusPolicy
import com.tripex.pose.domain.repository.GeocodingRepository
import com.tripex.pose.domain.repository.UnlockedPlaceRepository
import javax.inject.Inject

data class UnlockPlaceResult(
    val place: Place,
    val radiusMeters: Double,
)

/**
 * Unlocks a searched place as a **circle**, not as H3 cells.
 *
 * Rasterising a city at walking resolution produced ~770 000 indices for Warsaw and hit a hard
 * cap that simply refused the unlock. A circle is a centre, a radius and a ring generated when the
 * fog is drawn — so "I have been to Rome, I have all of Rome" costs one database row.
 */
class UnlockPlaceUseCase
    @Inject
    constructor(
        private val geocodingRepository: GeocodingRepository,
        private val unlockedPlaces: UnlockedPlaceRepository,
    ) {
        /** Unlocks a suggestion the player already picked; the geocoder is not consulted again. */
        suspend fun unlock(place: Place): Result<UnlockPlaceResult> = runCatching {
            val radius = RevealRadiusPolicy.radiusMeters(place)
            unlockedPlaces.unlock(
                UnlockedPlaceRepository.UnlockedPlace(
                    id = place.id.ifBlank { "${place.displayName}@${place.latitude},${place.longitude}" },
                    name = place.displayName,
                    latitude = place.latitude,
                    longitude = place.longitude,
                    radiusMeters = radius,
                    unlockedAt = System.currentTimeMillis(),
                ),
            )
            UnlockPlaceResult(place = place, radiusMeters = radius)
        }

        suspend operator fun invoke(
            query: String,
            radiusMeters: Double? = null,
        ): Result<UnlockPlaceResult> {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) {
                return Result.failure(IllegalArgumentException("Empty query"))
            }
            return geocodingRepository.search(trimmed).mapCatching { place ->
                unlock(place).getOrThrow().let {
                    if (radiusMeters == null) it else it.copy(radiusMeters = radiusMeters)
                }
            }
        }
    }
