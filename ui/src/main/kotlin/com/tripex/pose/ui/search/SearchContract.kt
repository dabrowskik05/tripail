package com.tripex.pose.ui.search

import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.Place
import com.tripex.pose.domain.usecase.SearchSelection

object SearchContract {

    data class State(
        val query: String = "",
        val isSearching: Boolean = false,
        val suggestions: List<Place> = emptyList(),
        val notFound: Boolean = false,
        /** The panel's subject. Non-null means the bottom sheet is up. */
        val selection: SearchSelection? = null,
        val isApplying: Boolean = false,
    )

    sealed interface Intent {
        data class QueryChanged(val query: String) : Intent

        /** Enter, or the keyboard's search key — a real request, not a peek at the list. */
        data object Submit : Intent
        data class SuggestionPicked(val place: Place) : Intent

        /** A place tapped straight on the map opens the same panel as a search result. */
        data class PlacePicked(val place: Place) : Intent
        data object Reveal : Intent
        data object Cover : Intent
        data object DismissPanel : Intent
        data object Reset : Intent
    }

    sealed interface Effect {
        /** A country is a level to enter, not an area to own. */
        data class OpenCountry(val iso2: String, val bounds: GeoBounds, val continentId: String) : Effect

        /** Centre the camera on what was picked. Never a reveal — see V3.4.4. */
        data class FocusCamera(val bounds: GeoBounds) : Effect
    }
}
