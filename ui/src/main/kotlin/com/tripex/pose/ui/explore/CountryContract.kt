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
    )

    sealed interface Intent {
        /**
         * A tap on the map. There is no longer a mode to be in: the hit test decides whether it
         * landed on a region of this country or on another country entirely (V3.3.1–V3.3.3).
         */
        data class FeatureTapped(val tap: BoundaryTap) : Intent
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
