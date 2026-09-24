package com.tripex.pose.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.Place
import com.tripex.pose.domain.repository.GeocodingRepository
import com.tripex.pose.domain.usecase.ObserveSearchSuggestionsUseCase
import com.tripex.pose.domain.usecase.ResolveMapPlaceUseCase
import com.tripex.pose.domain.usecase.ResolveSearchSelectionUseCase
import com.tripex.pose.domain.usecase.RevealTarget
import com.tripex.pose.domain.usecase.SearchSelection
import com.tripex.pose.domain.usecase.ToggleRevealUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Search for the whole app, not for one screen (V3.2.2, V3.4.3–V3.4.5).
 *
 * It lives above the navigation graph because the magnifier is in the top bar on every level,
 * and because the panel it opens is the same panel a tap on a city opens. Two copies of this
 * logic, one per entry point, is how the two would drift apart.
 *
 * **Nothing here reveals anything by itself.** Picking a result centres the camera and describes
 * the place; the player decides with a button. That is the correction from V3.4.4 — searching
 * for somewhere to look at used to claim it on the spot.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val geocoding: GeocodingRepository,
    private val resolveSelection: ResolveSearchSelectionUseCase,
    private val toggleReveal: ToggleRevealUseCase,
    private val resolveMapPlace: ResolveMapPlaceUseCase,
    observeSearchSuggestions: ObserveSearchSuggestionsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchContract.State())
    val state: StateFlow<SearchContract.State> = _state.asStateFlow()

    /**
     * What the search field shows, as Compose state so the field reads it **synchronously**.
     *
     * The field used to be fed from [state] through `collectAsStateWithLifecycle`, one frame
     * behind the keyboard. With a keyboard that composes whole words (autocorrect, Polish
     * diacritics) the field and the IME fell out of step and typed letters went missing — a full
     * name was mangled while a short prefix survived. [SearchContract.State.query] is kept in step
     * for the suggestion pipeline and for submit.
     */
    var fieldText: String by mutableStateOf("")
        private set

    private val _effects = Channel<SearchContract.Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        // One debounced pipeline for the whole app; typing never fans out into requests.
        viewModelScope.launch {
            observeSearchSuggestions(_state.map { it.query }).collect { result ->
                _state.update { it.copy(suggestions = result.getOrDefault(emptyList())) }
            }
        }
    }

    fun onIntent(intent: SearchContract.Intent) {
        when (intent) {
            is SearchContract.Intent.QueryChanged -> {
                fieldText = intent.query
                _state.update { it.copy(query = intent.query, notFound = false) }
            }

            SearchContract.Intent.Submit -> submit()
            is SearchContract.Intent.SuggestionPicked -> select(intent.place)
            // A map label is only a name and a point; the geocoder supplies the rest first.
            is SearchContract.Intent.PlacePicked -> {
                _state.update { it.copy(isSearching = true) }
                viewModelScope.launch { select(resolveMapPlace(intent.place)) }
            }
            SearchContract.Intent.Reveal -> apply(reveal = true)
            SearchContract.Intent.Cover -> apply(reveal = false)
            SearchContract.Intent.DismissPanel -> _state.update { it.copy(selection = null) }
            SearchContract.Intent.Reset -> {
                fieldText = ""
                _state.update { SearchContract.State(selection = it.selection) }
            }
        }
    }

    /**
     * Enter performs a **search** (V3.4.3).
     *
     * It used to take `suggestions.first()` and return silently when the list was empty — which
     * is precisely the moment the player presses enter, because nothing useful is on screen.
     * Typing "norwegia" and hitting enter therefore did nothing at all. Now the list is a
     * shortcut when it has something, and a real query when it does not.
     */
    private fun submit() {
        val current = _state.value
        if (current.isSearching) return
        val query = current.query.trim()
        if (query.isEmpty()) return

        current.suggestions.firstOrNull()?.let { return select(it) }

        _state.update { it.copy(isSearching = true, notFound = false) }
        viewModelScope.launch {
            geocoding.search(query)
                .onSuccess { place ->
                    _state.update { it.copy(isSearching = false) }
                    select(place)
                }
                // Silence is a bug, not a result: say so.
                .onFailure { _state.update { it.copy(isSearching = false, notFound = true) } }
        }
    }

    private fun select(place: Place) {
        _state.update { it.copy(isSearching = true, suggestions = emptyList(), notFound = false) }
        viewModelScope.launch {
            resolveSelection(place)
                .onSuccess { selection ->
                    _state.update {
                        it.copy(isSearching = false, selection = selection, query = "")
                    }
                    fieldText = ""
                    focus(selection)
                }
                .onFailure { _state.update { it.copy(isSearching = false, notFound = true) } }
        }
    }

    /**
     * The camera goes there; nothing is claimed.
     *
     * A country also raises [SearchContract.Effect.OpenCountry], because entering its level is
     * what "picking a country" means — but the panel opens either way, so the player is never
     * moved somewhere without being told where they are.
     */
    private suspend fun focus(selection: SearchSelection) {
        val target = selection.target
        if (target is RevealTarget.Country) {
            _effects.send(
                SearchContract.Effect.OpenCountry(target.iso2, target.bounds, target.continentId),
            )
            return
        }
        val bounds = selection.bounds ?: pointBounds(selection.latitude, selection.longitude)
        _effects.send(SearchContract.Effect.FocusCamera(bounds))
    }

    private fun apply(reveal: Boolean) {
        val selection = _state.value.selection ?: return
        if (_state.value.isApplying) return
        _state.update { it.copy(isApplying = true) }
        viewModelScope.launch {
            val result = if (reveal) toggleReveal.reveal(selection) else toggleReveal.cover(selection)
            result.onFailure { _state.update { s -> s.copy(isApplying = false) } }
                .onSuccess {
                    // The panel stays open and flips its button, so the change is visible on the
                    // fog underneath rather than being announced and dismissed.
                    _state.update { s ->
                        s.copy(
                            isApplying = false,
                            selection = selection.copy(
                                isRevealed = reveal,
                                canCover = reveal && selection.target !is RevealTarget.Country,
                            ),
                        )
                    }
                }
        }
    }

    /** A point with no extent still needs a box for the camera to frame. */
    private fun pointBounds(lat: Double, lng: Double): GeoBounds = GeoBounds(
        north = lat + POINT_SPAN_DEG,
        south = lat - POINT_SPAN_DEG,
        east = lng + POINT_SPAN_DEG,
        west = lng - POINT_SPAN_DEG,
    )

    private companion object {
        /** Roughly a town across — close enough to see streets, wide enough to keep context. */
        const val POINT_SPAN_DEG = 0.05
    }
}
