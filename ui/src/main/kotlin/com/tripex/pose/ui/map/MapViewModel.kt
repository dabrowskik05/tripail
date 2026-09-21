package com.tripex.pose.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripex.pose.domain.geo.MapViewport
import com.tripex.pose.domain.geo.Place
import com.tripex.pose.domain.geo.PlaceKind
import com.tripex.pose.domain.usecase.ObserveSearchSuggestionsUseCase
import com.tripex.pose.domain.usecase.PickSearchResultUseCase
import com.tripex.pose.domain.usecase.SearchOutcome
import com.tripex.pose.domain.location.TrackingController
import com.tripex.pose.domain.location.TrackingState
import com.tripex.pose.domain.location.TrackingSetupRepository
import com.tripex.pose.domain.location.TrackingStateHolder
import com.tripex.pose.domain.usecase.UnlockPlaceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MapViewModel @Inject constructor(
    private val trackingController: TrackingController,
    private val pickSearchResult: PickSearchResultUseCase,
    private val trackingSetup: TrackingSetupRepository,
    observeSearchSuggestions: ObserveSearchSuggestionsUseCase,
    trackingStateHolder: TrackingStateHolder,
) : ViewModel() {

    private val local = MutableStateFlow(MapContract.State())
    private val viewport = MutableStateFlow(MapViewport.DEFAULT)

    val state: StateFlow<MapContract.State> = combine(
        local,
        trackingStateHolder.state,
    ) { base, tracking ->
        base.copy(
            trackingState = tracking,
            statusMessage = statusFor(tracking, base),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = local.value,
    )

    init {
        // One debounced pipeline for the whole screen; typing never fans out into requests.
        viewModelScope.launch {
            observeSearchSuggestions(local.map { it.searchQuery }).collect { result ->
                local.update { state ->
                    state.copy(suggestions = result.getOrDefault(emptyList()))
                }
            }
        }
    }

    private val _effects = Channel<MapContract.Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onIntent(intent: MapContract.Intent) {
        when (intent) {
            is MapContract.Intent.PermissionsUpdated -> {
                local.update {
                    it.copy(
                        hasFineLocation = intent.fineGranted,
                        needsPreciseLocationHint = intent.coarseOnly,
                    )
                }
                // Vision / ETAP 5: discovery is not a mode the player switches on. The moment
                // location is available, the eraser is running — like a ride-hailing app.
                if (intent.fineGranted) {
                    viewModelScope.launch {
                        _effects.send(MapContract.Effect.RequestNotificationPermission)
                    }
                    trackingController.startTracking()
                    promptForBackgroundTracking(intent.backgroundGranted)
                } else {
                    viewModelScope.launch {
                        _effects.send(MapContract.Effect.RequestLocationPermissions)
                    }
                }
            }
            is MapContract.Intent.CameraIdle -> {
                viewport.value = intent.viewport
            }
            is MapContract.Intent.SearchQueryChanged -> {
                local.update {
                    it.copy(searchQuery = intent.query, searchMessage = null)
                }
            }
            MapContract.Intent.SubmitSearch -> submitSearch()
            is MapContract.Intent.SuggestionPicked -> unlockSuggestion(intent.place)
            MapContract.Intent.DismissSuggestions -> {
                local.update { it.copy(suggestions = emptyList()) }
            }
            MapContract.Intent.CameraTargetConsumed -> {
                local.update { it.copy(cameraTarget = null) }
            }
            MapContract.Intent.OpenSettingsRequested -> {
                viewModelScope.launch {
                    _effects.send(MapContract.Effect.OpenAppSettings)
                }
            }
            MapContract.Intent.OpenSettings -> {
                // There is nothing to configure yet — say so instead of opening an empty dialog.
                                viewModelScope.launch { _effects.send(MapContract.Effect.ShowComingSoon) }
            }
            MapContract.Intent.OpenCommunity -> {
                local.update { it.copy(communityVisible = true) }
            }
            MapContract.Intent.CloseCommunity -> {
                local.update { it.copy(communityVisible = false) }
            }
            MapContract.Intent.BackgroundPromptConfirmed -> {
                val prompt = local.value.backgroundPrompt
                local.update { it.copy(backgroundPrompt = null) }
                viewModelScope.launch {
                    when (prompt) {
                        MapContract.BackgroundPrompt.LocationAlways ->
                            _effects.send(MapContract.Effect.RequestBackgroundLocation)

                        MapContract.BackgroundPrompt.Reliability -> {
                            trackingSetup.markReliabilityPromptSeen()
                            _effects.send(MapContract.Effect.OpenBatteryOptimizationSettings)
                        }

                        null -> Unit
                    }
                }
            }
            MapContract.Intent.BackgroundPromptDismissed -> {
                val prompt = local.value.backgroundPrompt
                local.update { it.copy(backgroundPrompt = null) }
                // Declining the reliability screen still counts as having seen it. Re-asking on
                // every start is how a useful prompt becomes background noise.
                if (prompt == MapContract.BackgroundPrompt.Reliability) {
                    viewModelScope.launch { trackingSetup.markReliabilityPromptSeen() }
                }
            }
            MapContract.Intent.OpenAutostartSettings -> {
                local.update { it.copy(backgroundPrompt = null) }
                viewModelScope.launch {
                    trackingSetup.markReliabilityPromptSeen()
                    _effects.send(MapContract.Effect.OpenAutostartSettings)
                }
            }
            MapContract.Intent.DismissPlaceDetail -> {
                local.update { it.copy(placeDetail = null) }
            }
            MapContract.Intent.TogglePlaceDetailExpanded -> {
                local.update { state ->
                    val detail = state.placeDetail ?: return@update state
                    state.copy(placeDetail = detail.copy(expanded = !detail.expanded))
                }
            }
            is MapContract.Intent.FocusCamera -> {
                local.update { it.copy(cameraTarget = intent.target) }
            }
        }
    }

    /**
     * Two screens, at most one at a time, and only once each (V3.7.4 / V3.7.5).
     *
     * Order matters: permission first, reliability second. Asking somebody to exempt the app
     * from battery optimisation while it still cannot collect location in the background fixes
     * the cheaper half of the problem and leaves the expensive half looking solved.
     */
    private fun promptForBackgroundTracking(backgroundGranted: Boolean) {
        viewModelScope.launch {
            val prompt = when {
                !backgroundGranted -> MapContract.BackgroundPrompt.LocationAlways
                !trackingSetup.hasSeenReliabilityPrompt() ->
                    MapContract.BackgroundPrompt.Reliability
                else -> null
            } ?: return@launch
            local.update { if (it.backgroundPrompt == null) it.copy(backgroundPrompt = prompt) else it }
        }
    }

    /**
     * Pressing enter takes the first suggestion. Typing already produced them, so submitting is
     * not a second search — it is a pick.
     */
    private fun submitSearch() {
        val current = local.value
        if (current.isSearching) return
        val first = current.suggestions.firstOrNull() ?: return
        unlockSuggestion(first)
    }

    /**
     * The suggestion already carries its geometry, so nothing here re-queries the geocoder.
     * What happens next depends on what was picked — see [PickSearchResultUseCase].
     */
    private fun unlockSuggestion(place: Place) {
        local.update { it.copy(isSearching = true, suggestions = emptyList(), searchMessage = null) }
        viewModelScope.launch {
            pickSearchResult(place)
                .onSuccess { outcome -> applyOutcome(outcome) }
                .onFailure { error ->
                    local.update {
                        it.copy(
                            isSearching = false,
                            searchMessage = MapContract.SearchMessage.Failed(error.message),
                        )
                    }
                }
        }
    }

    private suspend fun applyOutcome(outcome: SearchOutcome) {
        local.update { it.copy(isSearching = false, searchQuery = "") }
        when (outcome) {
            is SearchOutcome.OpenCountry ->
                _effects.send(
                    MapContract.Effect.OpenCountry(
                        iso2 = outcome.iso2,
                        bounds = outcome.bounds,
                        label = outcome.label,
                        continentId = outcome.continentId,
                    ),
                )

            is SearchOutcome.RegionUnlocked ->
                local.update {
                    it.copy(
                        placeDetail = MapContract.PlaceDetail(
                            name = outcome.label,
                            kind = PlaceKind.Region,
                        ),
                    )
                }

            is SearchOutcome.PlaceUnlocked ->
                local.update {
                    it.copy(
                        cameraTarget = MapContract.CameraTarget(
                            latitude = outcome.place.latitude,
                            longitude = outcome.place.longitude,
                            zoom = PLACE_ZOOM,
                        ),
                        placeDetail = MapContract.PlaceDetail(
                            name = outcome.place.displayName,
                            kind = outcome.place.kind,
                        ),
                    )
                }
        }
    }

    private fun statusFor(
        tracking: TrackingState,
        base: MapContract.State,
    ): MapContract.StatusMessage? = when {
        base.needsPreciseLocationHint -> MapContract.StatusMessage.PreciseLocationRequired
        tracking == TrackingState.PermissionMissing -> MapContract.StatusMessage.PermissionMissing
        tracking == TrackingState.LocationDisabled -> MapContract.StatusMessage.LocationDisabled
        tracking == TrackingState.Tracking -> MapContract.StatusMessage.TrackingActive
        else -> null
    }


    private companion object {
        /** Close enough to see the streets a search just uncovered. */
        const val PLACE_ZOOM = 11.0
    }
}
