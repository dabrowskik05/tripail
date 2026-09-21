package com.tripex.pose.domain.usecase

import com.tripex.pose.core.di.DefaultDispatcher
import com.tripex.pose.domain.geo.Place
import com.tripex.pose.domain.geo.PlaceKind
import com.tripex.pose.domain.geo.RevealRadiusPolicy
import com.tripex.pose.domain.repository.GeocodingRepository
import com.tripex.pose.domain.repository.UnlockedPlaceRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Unlocks the whole settlement the player is standing in (ETAP 5).
 *
 * Without this, visiting a city means erasing it a kilometre at a time — precisely the grinding
 * the game rejects. Staying put is read as "I am here", and the city is claimed in one go.
 *
 * Only settlement-sized results count: a street address would unlock a block and a country would
 * unlock a continent, so both are ignored rather than clamped.
 */
@Singleton
class AutoUnlockCityUseCase
    @Inject
    constructor(
        private val geocoding: GeocodingRepository,
        private val unlockedPlaces: UnlockedPlaceRepository,
        @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) {
        sealed interface Result {
            data class Unlocked(val name: String, val radiusMeters: Double) : Result
            data object AlreadyOwned : Result
            data object NothingHere : Result
        }

        suspend operator fun invoke(lat: Double, lng: Double): Result =
            withContext(defaultDispatcher) {
                val place = geocoding.reverseGeocode(lat, lng).getOrNull()
                    ?: return@withContext Result.NothingHere
                if (!place.isSettlement()) return@withContext Result.NothingHere

                val id = place.stableId()
                if (unlockedPlaces.observeAll().first().any { it.id == id }) {
                    return@withContext Result.AlreadyOwned
                }

                val radius = RevealRadiusPolicy.radiusMeters(place)
                unlockedPlaces.unlock(
                    UnlockedPlaceRepository.UnlockedPlace(
                        id = id,
                        name = place.displayName,
                        latitude = place.latitude,
                        longitude = place.longitude,
                        radiusMeters = radius,
                        unlockedAt = System.currentTimeMillis(),
                    ),
                )
                Result.Unlocked(place.displayName, radius)
            }

        private fun Place.isSettlement(): Boolean = kind in SETTLEMENT_KINDS

        private fun Place.stableId(): String =
            id.ifBlank { "$displayName@$latitude,$longitude" }

        private companion object {
            val SETTLEMENT_KINDS = setOf(
                PlaceKind.City,
                PlaceKind.Town,
                PlaceKind.Municipality,
                PlaceKind.Village,
            )
        }
    }
