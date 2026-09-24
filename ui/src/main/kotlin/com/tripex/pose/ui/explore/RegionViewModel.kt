package com.tripex.pose.ui.explore

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.tripex.pose.domain.geo.AreaCoverage
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.atlas.AdminLevel
import com.tripex.pose.domain.geo.atlas.AreaKey
import com.tripex.pose.domain.geo.atlas.BoundaryGeometrySource
import com.tripex.pose.domain.geo.projection.GeometryOps
import com.tripex.pose.domain.repository.UnlockedRegionRepository
import com.tripex.pose.domain.settings.AppLanguageRepository
import com.tripex.pose.domain.usecase.ObserveAreaCoverageUseCase
import com.tripex.pose.domain.usecase.UnlockRegionUseCase
import com.tripex.pose.ui.navigation.Region
import com.tripex.pose.ui.navigation.bounds
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Region level (M3.3). The camera is untouched here — entering the level, selecting another
 * region and unlocking one all leave the view exactly where the player put it (vision, level 4).
 *
 * Regions come entirely from the local bundle — this screen makes no network calls, and a tap on
 * the map never resolves to a city: the bundle has no city polygons, and inventing one would be
 * inventing data.
 */
@HiltViewModel
class RegionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val boundaries: BoundaryGeometrySource,
    private val observeAreaCoverage: ObserveAreaCoverageUseCase,
    private val unlockRegion: UnlockRegionUseCase,
    private val unlockedRegions: UnlockedRegionRepository,
    private val appLanguage: AppLanguageRepository,
) : ViewModel() {

    private val route: Region = savedStateHandle.toRoute()

    private val _state = MutableStateFlow(
        RegionContract.State(
            regionId = route.regionId,
            countryIso2 = route.countryIso2,
            name = AreaLabels.region(route.regionId, fromBundle = null),
            flag = CountryFlag.of(route.countryIso2),
            bounds = route.bounds(),
            map = BoundaryMapState(
                continentId = route.continentId.ifBlank { null },
                selectedId = route.regionId,
            ),
        ),
    )
    val state: StateFlow<RegionContract.State> = _state.asStateFlow()

    private val _effects = Channel<RegionContract.Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var coverageJob: Job? = null

    init {
        observeCoverage(route.regionId)
        refreshName(route.regionId)
        refreshUnlocked(route.regionId)
    }

    fun onIntent(intent: RegionContract.Intent) {
        when (intent) {
            is RegionContract.Intent.RegionTapped -> onTap(intent.tap)
            RegionContract.Intent.ToggleRegionUnlock -> claimRegion()
            RegionContract.Intent.ExploreRequested -> {
                val current = _state.value
                viewModelScope.launch {
                    val bounds = current.bounds ?: boundsOf(current.regionId) ?: return@launch
                    _effects.send(
                        RegionContract.Effect.OpenMap(
                            area = AreaKey.Region(current.regionId),
                            bounds = bounds,
                            label = current.name,
                        ),
                    )
                }
            }
        }
    }

    /**
     * Whole-region unlock. Nothing is rasterised: the id goes to the database and the outline is
     * read back from the bundle when the wash is drawn.
     */
    private fun claimRegion() {
        val current = _state.value
        val regionId = current.regionId
        if (current.isUnlocking || regionId.isBlank()) return
        _state.update { it.copy(isUnlocking = true) }
        viewModelScope.launch {
            // Reversible on purpose: an unlock made by mistake should be one tap to undo.
            val nowUnlocked = if (current.isUnlocked) {
                unlockedRegions.lock(AdminLevel.Adm1, regionId)
                false
            } else {
                unlockRegion(AdminLevel.Adm1, regionId)
                true
            }
            _state.update {
                if (it.regionId != regionId) {
                    it.copy(isUnlocking = false)
                } else {
                    it.copy(isUnlocking = false, isUnlocked = nowUnlocked)
                }
            }
        }
    }

    private fun refreshUnlocked(regionId: String) {
        viewModelScope.launch {
            val owned = unlockedRegions.isUnlocked(AdminLevel.Adm1, regionId)
            _state.update { if (it.regionId == regionId) it.copy(isUnlocked = owned) else it }
        }
    }

    /**
     * Only a region of **this** country is a region pick. Anything else — the neighbouring country
     * under the finger — used to land in [selectRegion] too: "CZ" became the selected region id
     * while the scene kept drawing Poland's regions, which is how Poland's outlines showed up
     * "in Czechia". Another country now opens that country's level instead.
     */
    private fun onTap(tap: BoundaryTap) {
        val country = _state.value.countryIso2
        when {
            tap.level == AdminLevel.Adm1 && tap.countryIso2.equals(country, ignoreCase = true) ->
                selectRegion(tap)
            else -> {
                val iso2 = if (tap.level == AdminLevel.Adm0) tap.featureId else tap.countryIso2
                if (iso2 != null && !iso2.equals(country, ignoreCase = true)) openCountry(iso2)
            }
        }
    }

    private fun openCountry(iso2: String) {
        viewModelScope.launch {
            val bounds = boundaries.rings(AdminLevel.Adm0, iso2).getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { GeometryOps.mainlandBounds(it) }
                ?: return@launch
            _effects.send(RegionContract.Effect.OpenCountry(iso2, bounds))
        }
    }

    private fun selectRegion(tap: BoundaryTap) {
        val regionId = tap.featureId
        if (regionId == _state.value.regionId) return
        _state.update {
            it.copy(
                regionId = regionId,
                name = AreaLabels.region(regionId, tap.name),
                bounds = null,
                coverage = AreaCoverage.Unavailable,
                map = it.map.copy(selectedId = regionId),
            )
        }
        observeCoverage(regionId)
        refreshUnlocked(regionId)
    }

    private fun observeCoverage(regionId: String) {
        coverageJob?.cancel()
        coverageJob = viewModelScope.launch {
            observeAreaCoverage(AreaKey.Region(regionId)).collectLatest { coverage ->
                _state.update { if (it.regionId == regionId) it.copy(coverage = coverage) else it }
            }
        }
    }

    private fun refreshName(regionId: String) {
        viewModelScope.launch {
            val feature = boundaries.feature(AdminLevel.Adm1, regionId) ?: return@launch
            val language = appLanguage.observe().first()
            _state.update {
                if (it.regionId != regionId) {
                    it
                } else {
                    // Prefer the bundle name in the interface language — displayName always
                    // favours Polish, which leaks into an English UI (V3.5.6).
                    it.copy(name = AreaLabels.region(regionId, feature.nameIn(language)))
                }
            }
        }
    }

    private suspend fun boundsOf(regionId: String): GeoBounds? =
        boundaries.rings(AdminLevel.Adm1, regionId).getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?.let { GeometryOps.boundsOf(it) }
}
