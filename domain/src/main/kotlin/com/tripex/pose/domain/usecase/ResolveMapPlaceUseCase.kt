package com.tripex.pose.domain.usecase

import com.tripex.pose.domain.geo.Place
import com.tripex.pose.domain.repository.GeocodingRepository
import javax.inject.Inject

/**
 * Turns a settlement tapped on the map into the same [Place] a search for it returns.
 *
 * A map label carries a name and a point, nothing else. Used as it was, every city fell back to
 * the same fixed radius — Warsaw and Skierniewice uncovered circles of one size — and its id was
 * built from the label's coordinates, which move slightly between zoom levels, so a city
 * revealed a moment ago was not recognised on the next tap and could not be covered again.
 *
 * The geocoder at the label's point answers with the city's own record: its real extent, and the
 * id that search and the automatic unlock use too. Offline, the tapped label is kept as it was.
 */
class ResolveMapPlaceUseCase
    @Inject
    constructor(
        private val geocoding: GeocodingRepository,
    ) {
        suspend operator fun invoke(tapped: Place): Place =
            geocoding.reverseGeocode(tapped.latitude, tapped.longitude).getOrNull() ?: tapped
    }
