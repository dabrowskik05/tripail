package com.tripex.pose.ui.explore

import com.tripex.pose.domain.geo.AreaCoverage
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.atlas.AreaKey

object RegionContract {

    data class State(
        val regionId: String = "",
        val countryIso2: String = "",
        val name: String = "",
        val flag: String = CountryFlag.FALLBACK,
        val bounds: GeoBounds? = null,
        val coverage: AreaCoverage = AreaCoverage.Unavailable,
        val isUnlocking: Boolean = false,
        val isUnlocked: Boolean = false,
        val map: BoundaryMapState = BoundaryMapState(),
    )

    sealed interface Intent {
        data class RegionTapped(val tap: BoundaryTap) : Intent
        data object ExploreRequested : Intent

        /** Claims the whole region by its exact outline, or puts it back under the fog. */
        data object ToggleRegionUnlock : Intent
    }

    sealed interface Effect {
        data class OpenMap(val area: AreaKey, val bounds: GeoBounds, val label: String) : Effect

        /** A tap on another country: leave the region level for that country's. */
        data class OpenCountry(val iso2: String, val bounds: GeoBounds) : Effect
    }
}
