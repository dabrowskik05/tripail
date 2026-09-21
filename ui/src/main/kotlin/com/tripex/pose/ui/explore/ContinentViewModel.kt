package com.tripex.pose.ui.explore

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.tripex.pose.core.logging.Logger
import com.tripex.pose.domain.geo.ContinentId
import com.tripex.pose.domain.geo.atlas.AdminLevel
import com.tripex.pose.domain.geo.atlas.BoundaryGeometrySource
import com.tripex.pose.domain.geo.projection.GeometryOps
import com.tripex.pose.ui.navigation.Continent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Continent level: country outlines from the local bundle, nothing selected yet (M3.1 / M3.2).
 *
 * The tap only tells us *which* country was hit. Its framing box comes from the real geometry in
 * `boundaries.pmtiles`, because the next level flies the camera onto the country and has to know
 * where it is before any tile has loaded there.
 *
 * The camera for this level itself was already placed by the continent menu on the way in, so
 * nothing here touches it.
 */
@HiltViewModel
class ContinentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val boundaries: BoundaryGeometrySource,
    private val logger: Logger,
) : ViewModel() {

    private val route: Continent = savedStateHandle.toRoute()
    private val continentId = runCatching { ContinentId.valueOf(route.continentId) }.getOrNull()

    private val _state = MutableStateFlow(
        ContinentContract.State(
            continentId = continentId,
            map = BoundaryMapState(
                mode = BoundaryMode.Countries,
                selectedId = null,
                continentId = continentId?.name,
            ),
        ),
    )
    val state: StateFlow<ContinentContract.State> = _state.asStateFlow()

    private val _effects = Channel<ContinentContract.Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onIntent(intent: ContinentContract.Intent) {
        when (intent) {
            is ContinentContract.Intent.CountryTapped -> openCountry(intent.tap)
        }
    }

    private fun openCountry(tap: BoundaryTap) {
        if (_state.value.isResolving) return
        _state.update { it.copy(isResolving = true) }
        viewModelScope.launch {
            try {
                val rings = boundaries.rings(AdminLevel.Adm0, tap.featureId).getOrNull()
                if (rings.isNullOrEmpty()) {
                    logger.w(TAG, "No geometry for country ${tap.featureId}")
                    _effects.send(ContinentContract.Effect.NoCountryHere)
                    return@launch
                }
                // Main landmass only — the full extent would fly the camera out over the ocean.
                val bounds = GeometryOps.mainlandBounds(rings)
                logger.i(TAG, "Country ${tap.featureId} bbox=$bounds")
                _effects.send(ContinentContract.Effect.OpenCountry(tap.featureId, bounds))
            } finally {
                _state.update { it.copy(isResolving = false) }
            }
        }
    }

    private companion object {
        const val TAG = "ContinentLevel"
    }
}
