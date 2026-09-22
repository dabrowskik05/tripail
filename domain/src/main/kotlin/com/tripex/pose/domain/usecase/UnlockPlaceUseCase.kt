package com.tripex.pose.domain.usecase

import com.tripex.pose.domain.geo.Place
import com.tripex.pose.domain.geo.RevealRadiusPolicy
import com.tripex.pose.domain.repository.UnlockedPlaceRepository
import javax.inject.Inject

data class UnlockPlaceResult(
    val place: Place,
    val radiusMeters: Double,
)

/**
 * Reveals a place as a **circle**, not as H3 cells.
 *
 * Rasterising a city at walking resolution produced ~770 000 indices for Warsaw and hit a hard
 * cap that simply refused the unlock. A circle is a centre, a radius and a ring generated when
 * the fog is drawn — so "I have been to Rome, I have all of Rome" costs one database row.
 *
 * The single place a `unlocked_place` row is built, so the id and the [source] can only be got
 * right or wrong in one spot. Both writers go through it: the player pressing "Reveal"
 * ([ToggleRevealUseCase]) and standing somewhere long enough ([AutoUnlockCityUseCase]).
 */
class UnlockPlaceUseCase
    @Inject
    constructor(
        private val unlockedPlaces: UnlockedPlaceRepository,
    ) {
        suspend fun unlock(
            place: Place,
            radiusMeters: Double? = null,
            source: UnlockedPlaceRepository.Source = UnlockedPlaceRepository.Source.Manual,
        ): Result<UnlockPlaceResult> = runCatching {
            val radius = radiusMeters ?: RevealRadiusPolicy.radiusMeters(place)
            unlockedPlaces.unlock(
                UnlockedPlaceRepository.UnlockedPlace(
                    id = idOf(place),
                    name = place.displayName,
                    latitude = place.latitude,
                    longitude = place.longitude,
                    radiusMeters = radius,
                    unlockedAt = System.currentTimeMillis(),
                    source = source,
                ),
            )
            UnlockPlaceResult(place = place, radiusMeters = radius)
        }

        suspend fun lock(place: Place): Boolean = unlockedPlaces.lock(idOf(place))

        /** Provider id when there is one; otherwise name and position, which is stable enough. */
        fun idOf(place: Place): String =
            place.id.ifBlank { "${place.displayName}@${place.latitude},${place.longitude}" }
    }
