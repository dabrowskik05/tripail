package com.tripex.pose.ui.explore

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.tripex.pose.core.logging.Logger
import com.tripex.pose.domain.geo.AreaCoverage
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.atlas.AdminLevel
import com.tripex.pose.domain.geo.atlas.AreaKey
import com.tripex.pose.domain.geo.atlas.BoundaryGeometrySource
import com.tripex.pose.domain.geo.projection.GeometryOps
import com.tripex.pose.domain.usecase.ObserveAreaCoverageUseCase
import com.tripex.pose.ui.navigation.Country
import com.tripex.pose.ui.navigation.bounds
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Country level (M3.2). Selecting a different country stays on this destination — it is a filter
 * change on the existing layers plus a fresh coverage subscription, never a new screen.
 *
 * This is the one level where the camera moves by itself (vision, level 3): picking a country
 * flies slowly onto it. Every deeper level leaves the camera exactly where the player left it.
 */
@HiltViewModel
class CountryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val boundaries: BoundaryGeometrySource,
    private val observeAreaCoverage: ObserveAreaCoverageUseCase,
    private val logger: Logger,
) : ViewModel() {

    private val route: Country = savedStateHandle.toRoute()

    private val _state = MutableStateFlow(
        CountryContract.State(
            iso2 = route.iso2,
            name = AreaLabels.country(route.iso2),
            flag = CountryFlag.of(route.iso2),
            bounds = route.bounds(),
            map = BoundaryMapState(
                continentId = route.continentId.ifBlank { null },
                mode = BoundaryMode.Countries,
                selectedId = route.iso2,
            ),
        ),
    )
    val state: StateFlow<CountryContract.State> = _state.asStateFlow()

    private val _effects = Channel<CountryContract.Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var coverageJob: Job? = null

    init {
        observeCoverage(route.iso2)
        refreshName(route.iso2)
        // Arriving at the level is itself a country pick, so it frames the same way a tap does.
        frame(route.bounds())
    }

    fun onIntent(intent: CountryContract.Intent) {
        when (intent) {
            is CountryContract.Intent.FeatureTapped -> onTap(intent.tap)
            CountryContract.Intent.ShowRegions -> _state.update {
                it.copy(map = it.map.copy(mode = BoundaryMode.Regions(it.iso2), selectedId = null))
            }
            CountryContract.Intent.ShowCountries -> _state.update {
                it.copy(map = it.map.copy(mode = BoundaryMode.Countries, selectedId = it.iso2))
            }
            CountryContract.Intent.ExploreRequested -> {
                val current = _state.value
                viewModelScope.launch {
                    // Outline is read here, once, because the map screen needs somewhere to look —
                    // not on every selection.
                    val bounds = current.bounds ?: boundsOf(AdminLevel.Adm0, current.iso2) ?: return@launch
                    _effects.send(
                        CountryContract.Effect.OpenMap(
                            area = AreaKey.Country(current.iso2),
                            bounds = bounds,
                            label = current.name,
                        ),
                    )
                }
            }
        }
    }

    private fun onTap(tap: BoundaryTap) {
        when (_state.value.map.mode) {
            BoundaryMode.Countries -> selectCountry(tap)
            is BoundaryMode.Regions -> openRegion(tap)
        }
    }

    /**
     * M3.2 pt 4: another country is a selection change, not a level change.
     *
     * The visible part is free — the name rides in on the tapped feature and the highlight is a
     * filter swap. The outline is read off the main thread purely to frame the camera, and the
     * flight is issued only once it has arrived, so a tap never stalls waiting for tiles.
     */
    private fun selectCountry(tap: BoundaryTap) {
        val iso2 = tap.featureId
        if (iso2 == _state.value.iso2) return
        _state.update {
            it.copy(
                iso2 = iso2,
                name = AreaLabels.country(iso2, tap.name),
                flag = CountryFlag.of(iso2),
                bounds = null,
                coverage = AreaCoverage.Unavailable,
                map = it.map.copy(selectedId = iso2),
            )
        }
        observeCoverage(iso2)
        frameCountry(iso2)
    }

    /** Reads the country's main landmass, remembers it, and asks the camera to settle on it. */
    private fun frameCountry(iso2: String) {
        viewModelScope.launch {
            val bounds = boundsOf(AdminLevel.Adm0, iso2)
            if (bounds == null) {
                logger.w(TAG, "No geometry to frame country $iso2")
                return@launch
            }
            _state.update { if (it.iso2 == iso2) it.copy(bounds = bounds) else it }
            if (_state.value.iso2 == iso2) frame(bounds)
        }
    }

    private fun frame(bounds: GeoBounds?) {
        val target = bounds ?: return
        viewModelScope.launch { _effects.send(CountryContract.Effect.FrameCamera(target)) }
    }

    private fun openRegion(tap: BoundaryTap) {
        val countryIso2 = _state.value.iso2
        viewModelScope.launch {
            val bounds = boundsOf(AdminLevel.Adm1, tap.featureId)
            if (bounds == null) {
                logger.w(TAG, "No geometry for region ${tap.featureId}")
                return@launch
            }
            _effects.send(CountryContract.Effect.OpenRegion(tap.featureId, countryIso2, bounds))
        }
    }

    private fun observeCoverage(iso2: String) {
        coverageJob?.cancel()
        coverageJob = viewModelScope.launch {
            observeAreaCoverage(AreaKey.Country(iso2)).collectLatest { coverage ->
                _state.update { if (it.iso2 == iso2) it.copy(coverage = coverage) else it }
            }
        }
    }

    /**
     * Only for codes the platform does not know. `feature()` scans every tile at the search zoom,
     * so it must never run for a country ICU can already name — which is almost all of them.
     */
    private fun refreshName(iso2: String) {
        if (!AreaLabels.country(iso2).equals(iso2, ignoreCase = true)) return
        viewModelScope.launch {
            val feature = boundaries.feature(AdminLevel.Adm0, iso2) ?: return@launch
            _state.update {
                if (it.iso2 != iso2) it else it.copy(name = AreaLabels.country(iso2, feature.displayName))
            }
        }
    }

    /** Framing box of the area's main landmass — never the full extent (see [GeometryOps]). */
    private suspend fun boundsOf(level: AdminLevel, id: String): GeoBounds? =
        boundaries.rings(level, id).getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?.let { GeometryOps.mainlandBounds(it) }

    private companion object {
        const val TAG = "CountryLevel"
    }
}
