package com.tripex.pose.ui.map.host

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripex.pose.domain.geo.atlas.BoundaryTilesProvider
import com.tripex.pose.domain.location.DomainLocation
import com.tripex.pose.domain.location.LocationTracker
import com.tripex.pose.domain.map.MapStyleProvider
import com.tripex.pose.domain.usecase.ObserveFogGeoJsonUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Supplies the single map surface: where the basemap and the boundary tiles come from, and the
 * one global parchment stream.
 *
 * There is exactly one subscription to the wash for the whole app. Each level used to open its
 * own, which meant three copies of the GeoJSON string being rebuilt in parallel.
 *
 * The wash does not follow the camera: it is one geometry for the whole world, so zooming never
 * changes the shape of what has been discovered.
 */
@HiltViewModel
class MapHostViewModel @Inject constructor(
    mapStyleProvider: MapStyleProvider,
    tilesProvider: BoundaryTilesProvider,
    private val observeFogGeoJson: ObserveFogGeoJsonUseCase,
    locationTracker: LocationTracker,
) : ViewModel() {

    val styleUri: String = mapStyleProvider.styleUri()

    private val _boundarySourceUri = MutableStateFlow(tilesProvider.styleSourceUri())
    val boundarySourceUri: StateFlow<String> = _boundarySourceUri.asStateFlow()

    private val _fogGeoJson = MutableStateFlow(observeFogGeoJson.emptyWorld())
    val fogGeoJson: StateFlow<String> = _fogGeoJson.asStateFlow()

    init {
        viewModelScope.launch {
            observeFogGeoJson().collect { _fogGeoJson.value = it }
        }
    }

    /**
     * Where the player is, for the marker. Only while someone is watching: the subscription stops
     * [STOP_TIMEOUT_MS] after the map leaves the screen, so this never runs in the background —
     * that is the tracking service's job. Without permission the stream fails; it retries now and
     * then, so granting location later brings the marker without a restart.
     */
    val playerLocation: StateFlow<Pair<Double, Double>?> = locationTracker.locationUpdates()
        .map<DomainLocation, Pair<Double, Double>?> { it.latitude to it.longitude }
        .retryWhen { _, _ ->
            delay(RETRY_MS)
            true
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val RETRY_MS = 15_000L
    }
}
