package com.tripex.pose.ui.map.host

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripex.pose.domain.geo.atlas.BoundaryTilesProvider
import com.tripex.pose.domain.map.MapStyleProvider
import com.tripex.pose.domain.usecase.ObserveFogGeoJsonUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Supplies the single map surface: where the basemap and the boundary tiles come from, and the
 * one global parchment stream.
 *
 * There is exactly one subscription to the wash for the whole app. Each level used to open its
 * own, which meant three copies of a 20 000-cell GeoJSON string being rebuilt in parallel.
 */
@HiltViewModel
class MapHostViewModel @Inject constructor(
    mapStyleProvider: MapStyleProvider,
    tilesProvider: BoundaryTilesProvider,
    private val observeFogGeoJson: ObserveFogGeoJsonUseCase,
) : ViewModel() {

    val styleUri: String = mapStyleProvider.styleUri()

    private val _boundarySourceUri = MutableStateFlow(tilesProvider.styleSourceUri())
    val boundarySourceUri: StateFlow<String> = _boundarySourceUri.asStateFlow()

    private val _fogGeoJson = MutableStateFlow("")
    val fogGeoJson: StateFlow<String> = _fogGeoJson.asStateFlow()

    init {
        viewModelScope.launch {
            observeFogGeoJson.global().collect { _fogGeoJson.value = it }
        }
    }
}
