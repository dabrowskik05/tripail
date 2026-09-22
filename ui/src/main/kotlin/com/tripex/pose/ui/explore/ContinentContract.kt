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

        /** Box the camera may not leave while this continent is open (V3.3.7). */
        data class LimitCamera(val bounds: GeoBounds) : Effect

        /**
         * Settle on the player, once, after the continent has been framed whole (V3.3.6).
         * Raised only when there is a recent fix and it is on this continent.
         */
        data class FocusPlayer(val bounds: GeoBounds) : Effect
    }
}
