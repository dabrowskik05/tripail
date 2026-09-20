package com.tripex.pose.ui.continent

import com.tripex.pose.domain.geo.ContinentId

object ContinentMapContract {

    data class State(
        val coverage: Map<ContinentId, Float> = emptyMap(),
        val selectedContinent: ContinentId? = null,
    )

    sealed interface Intent {
        data class ContinentClicked(val id: ContinentId) : Intent
        data object BackFromDetail : Intent
        data object ExploreSelected : Intent
    }

    sealed interface Effect {
        data class OpenMap(val continentId: ContinentId) : Effect
    }
}
