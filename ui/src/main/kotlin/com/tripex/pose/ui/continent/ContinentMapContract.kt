package com.tripex.pose.ui.continent

import com.tripex.pose.domain.geo.AreaCoverage
import com.tripex.pose.domain.geo.ContinentId
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.atlas.ContinentShape

object ContinentMapContract {

    data class State(
        val shapes: List<ContinentShape> = emptyList(),
        /** Real coverage of the selected continent; absent until one is picked. */
        val coverage: AreaCoverage = AreaCoverage.Unavailable,
        val selectedContinent: ContinentId? = null,
        val atlasUnavailable: Boolean = false,
    )

    sealed interface Intent {
        /** `null` means a tap on open water — it clears the selection. */
        data class ContinentClicked(val id: ContinentId?) : Intent
        data object ClearSelection : Intent
        data object ExploreSelected : Intent
    }

    sealed interface Effect {
        /** Bounds come from the atlas shape, so the map opens on the continent itself. */
        data class OpenMap(val continentId: ContinentId, val bounds: GeoBounds) : Effect
    }
}
