package com.tripex.pose.ui.explore

import com.tripex.pose.domain.geo.AreaCoverage
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.atlas.AreaKey

object CountryContract {

    data class State(
        val iso2: String = "",
        val name: String = "",
        val flag: String = CountryFlag.FALLBACK,
        val bounds: GeoBounds? = null,
        val coverage: AreaCoverage = AreaCoverage.Unavailable,
        val map: BoundaryMapState = BoundaryMapState(),
    ) {
        val showingRegions: Boolean get() = map.mode is BoundaryMode.Regions
    }

    sealed interface Intent {
        data class FeatureTapped(val tap: BoundaryTap) : Intent

        /** Swaps the layer filters to ADM1 without leaving this destination (M3.2 pt 3). */
        data object ShowRegions : Intent
        data object ShowCountries : Intent
        data object ExploreRequested : Intent
    }

    sealed interface Effect {
        data class OpenMap(val area: AreaKey, val bounds: GeoBounds, val label: String) : Effect

        /**
         * The one automatic camera move in the app: settle slowly on the picked country
         * (vision, level 3). Deeper levels have no equivalent, by design.
         */
        data class FrameCamera(val bounds: GeoBounds) : Effect
        data class OpenRegion(val regionId: String, val countryIso2: String, val bounds: GeoBounds) : Effect
    }
}
