package com.tripex.pose.ui.explore

import com.tripex.pose.domain.geo.ContinentId
import com.tripex.pose.domain.geo.GeoBounds

object ContinentContract {

    data class State(
        val continentId: ContinentId? = null,
        val map: BoundaryMapState = BoundaryMapState(),
        val isResolving: Boolean = false,
    )

    sealed interface Intent {
        data class CountryTapped(val tap: BoundaryTap) : Intent
    }

    sealed interface Effect {
        data class OpenCountry(val iso2: String, val bounds: GeoBounds) : Effect

        /** Tap landed on water, or on a country the bundle has no geometry for. */
        data object NoCountryHere : Effect
    }
}
