package com.tripex.pose.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripex.pose.domain.geo.FogGeoJsonBuilder
import com.tripex.pose.domain.geo.MapViewport
import com.tripex.pose.domain.location.TrackingController
import com.tripex.pose.domain.location.TrackingState
import com.tripex.pose.domain.location.TrackingStateHolder
import com.tripex.pose.domain.map.MapStyleProvider
import com.tripex.pose.domain.usecase.ObserveFogGeoJsonUseCase
import com.tripex.pose.domain.usecase.ObserveUnlockedCountUseCase
import com.tripex.pose.domain.usecase.UnlockPlaceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MapViewModel @Inject constructor(
    private val trackingController: TrackingController,
    private val unlockPlace: UnlockPlaceUseCase,
    private val fogGeoJsonBuilder: FogGeoJsonBuilder,
    mapStyleProvider: MapStyleProvider,
    trackingStateHolder: TrackingStateHolder,
    observeUnlockedCount: ObserveUnlockedCountUseCase,
    observeFogGeoJson: ObserveFogGeoJsonUseCase,
) : ViewModel() {

    private val local = MutableStateFlow(
        MapContract.State(
            styleUri = mapStyleProvider.styleUri(),
            fogGeoJson = fogGeoJsonBuilder.emptyWorld(),
            initialViewport = MapViewport.DEFAULT,
        ),
    )
    private val viewport = MutableStateFlow(MapViewport.DEFAULT)

    private val fogGeoJson: StateFlow<String> = observeFogGeoJson(viewport)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = fogGeoJsonBuilder.emptyWorld(),
        )

    val state: StateFlow<MapContract.State> = combine(
        local,
        trackingStateHolder.state,
        observeUnlockedCount(),
        fogGeoJson,
    ) { base, tracking, count, fog ->
        base.copy(
            trackingState = tracking,
            unlockedCount = count,
            fogGeoJson = fog,
            statusMessage = statusFor(tracking, base),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = local.value,
    )

    private val _effects = Channel<MapContract.Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onIntent(intent: MapContract.Intent) {
        when (intent) {
            MapContract.Intent.StartDiscovery -> onStartDiscovery()
            MapContract.Intent.StopDiscovery -> trackingController.stopTracking()
            is MapContract.Intent.PermissionsUpdated -> {
                local.update {
                    it.copy(
                        hasFineLocation = intent.fineGranted,
                        needsPreciseLocationHint = intent.coarseOnly,
                    )
                }
                if (intent.startIfGranted && intent.fineGranted) {
                    viewModelScope.launch {
                        _effects.send(MapContract.Effect.RequestNotificationPermission)
                    }
                    trackingController.startTracking()
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
            MapContract.Intent.CameraTargetConsumed -> {
                local.update { it.copy(cameraTarget = null) }
            }
            MapContract.Intent.OpenSettingsRequested -> {
                viewModelScope.launch {
                    _effects.send(MapContract.Effect.OpenAppSettings)
                }
            }
            MapContract.Intent.ZoomIn -> adjustZoom(delta = 1.0)
            MapContract.Intent.ZoomOut -> adjustZoom(delta = -1.0)
            MapContract.Intent.ToggleFabMenu -> {
                local.update { it.copy(fabExpanded = !it.fabExpanded) }
            }
            MapContract.Intent.OpenSettings -> {
                local.update { it.copy(settingsVisible = true, fabExpanded = false) }
            }
            MapContract.Intent.CloseSettings -> {
                local.update { it.copy(settingsVisible = false) }
            }
            MapContract.Intent.OpenCommunity -> {
                local.update { it.copy(communityVisible = true, fabExpanded = false) }
            }
            MapContract.Intent.CloseCommunity -> {
                local.update { it.copy(communityVisible = false) }
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
        }
    }

    private fun adjustZoom(delta: Double) {
        val current = viewport.value
        val centerLat = (current.bounds.north + current.bounds.south) / 2.0
        val centerLng = (current.bounds.east + current.bounds.west) / 2.0
        local.update {
            it.copy(
                cameraTarget = MapContract.CameraTarget(
                    latitude = centerLat,
                    longitude = centerLng,
                    zoom = (current.zoom + delta).coerceIn(MIN_ZOOM, MAX_ZOOM),
                ),
            )
        }
    }

    private fun submitSearch() {
        val query = local.value.searchQuery
        if (query.isBlank() || local.value.isSearching) return
        viewModelScope.launch {
            local.update { it.copy(isSearching = true, searchMessage = null) }
            val result = unlockPlace(query)
            result.fold(
                onSuccess = { unlocked ->
                    local.update {
                        it.copy(
                            isSearching = false,
                            searchMessage = null,
                            placeDetail = MapContract.PlaceDetail(
                                name = unlocked.place.displayName,
                                typeLabel = "",
                                unlockedHexCount = unlocked.newlyUnlocked,
                            ),
                            cameraTarget = MapContract.CameraTarget(
                                latitude = unlocked.place.latitude,
                                longitude = unlocked.place.longitude,
                            ),
                        )
                    }
                },
                onFailure = { error ->
                    local.update {
                        it.copy(
                            isSearching = false,
                            searchMessage = MapContract.SearchMessage.Failed(error.message),
                        )
                    }
                },
            )
        }
    }

    private fun onStartDiscovery() {
        val current = state.value
        if (current.trackingState == TrackingState.Tracking) return
        if (!current.hasFineLocation) {
            viewModelScope.launch {
                _effects.send(MapContract.Effect.RequestLocationPermissions)
            }
            return
        }
        viewModelScope.launch {
            _effects.send(MapContract.Effect.RequestNotificationPermission)
        }
        trackingController.startTracking()
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
        const val MIN_ZOOM = 2.0
        const val MAX_ZOOM = 20.0
    }
}
