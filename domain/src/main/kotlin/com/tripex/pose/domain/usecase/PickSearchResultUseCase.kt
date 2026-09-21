package com.tripex.pose.domain.usecase

import com.tripex.pose.domain.geo.BoundaryMatcher
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.Place
import com.tripex.pose.domain.geo.PlaceKind
import com.tripex.pose.domain.geo.atlas.AdminLevel
import com.tripex.pose.domain.geo.atlas.BoundaryGeometrySource
import com.tripex.pose.domain.geo.projection.GeometryOps
import javax.inject.Inject

/** What picking a suggestion did, so the UI knows whether to navigate or just redraw (M4.10). */
sealed interface SearchOutcome {

    /** A country is a place to go, not a thing to own — enter its level. */
    data class OpenCountry(
        val iso2: String,
        val bounds: GeoBounds,
        val label: String,
        /** Empty when the bundle did not name one — the country level then stays untinted. */
        val continentId: String = "",
    ) : SearchOutcome

    /** A region is owned by its exact outline, straight from the bundle. */
    data class RegionUnlocked(val regionId: String, val countryIso2: String?, val label: String) :
        SearchOutcome

    /** Everything settlement-sized is owned as a circle. */
    data class PlaceUnlocked(val place: Place, val radiusMeters: Double) : SearchOutcome
}

/**
 * Routes a picked suggestion to the right action (M4.10).
 *
 * The three kinds behave differently on purpose: a country is too big to "own" in one tap, a
 * region has a real outline in the bundle, and a city is best approximated by a circle. Falling
 * back to the circle is always safe, so a result the bundle cannot match still does something.
 */
class PickSearchResultUseCase
    @Inject
    constructor(
        private val boundaryMatcher: BoundaryMatcher,
        private val boundaries: BoundaryGeometrySource,
        private val unlockRegionUseCase: UnlockRegionUseCase,
        private val unlockPlace: UnlockPlaceUseCase,
    ) {
        suspend operator fun invoke(place: Place): Result<SearchOutcome> = runCatching {
            when (place.kind) {
                PlaceKind.Country -> openCountry(place)
                PlaceKind.Region -> claimRegion(place)
                else -> unlockAsCircle(place)
            }
        }

        private suspend fun openCountry(place: Place): SearchOutcome {
            val feature = boundaryMatcher.match(AdminLevel.Adm0, place)
                ?: return unlockAsCircle(place)
            val bounds = boundsOf(AdminLevel.Adm0, feature.id)
                ?: place.boundingBox
                ?: return unlockAsCircle(place)
            return SearchOutcome.OpenCountry(
                iso2 = feature.id,
                bounds = bounds,
                label = feature.displayName.ifBlank { place.displayName },
                // Without this the country would be entered in the neutral palette, while the
                // same country reached by tapping is tinted (vision §3).
                continentId = feature.continentId.orEmpty(),
            )
        }

        private suspend fun claimRegion(place: Place): SearchOutcome {
            val feature = boundaryMatcher.match(AdminLevel.Adm1, place)
                // M4.6 step 2: no match in the bundle degrades to a radius rather than doing
                // nothing. The player still gets an unlock, just an approximate one.
                ?: return unlockAsCircle(place)
            unlockRegionUseCase(AdminLevel.Adm1, feature.id).getOrThrow()
            return SearchOutcome.RegionUnlocked(
                regionId = feature.id,
                countryIso2 = feature.countryIso2,
                label = feature.displayName.ifBlank { place.displayName },
            )
        }

        private suspend fun unlockAsCircle(place: Place): SearchOutcome {
            val result = unlockPlace.unlock(place).getOrThrow()
            return SearchOutcome.PlaceUnlocked(result.place, result.radiusMeters)
        }

        private suspend fun boundsOf(level: AdminLevel, id: String): GeoBounds? =
            boundaries.rings(level, id).getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { GeometryOps.boundsOf(it) }
    }
