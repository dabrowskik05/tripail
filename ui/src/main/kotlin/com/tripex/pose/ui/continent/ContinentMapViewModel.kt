package com.tripex.pose.ui.continent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripex.pose.domain.geo.ContinentBounds
import com.tripex.pose.domain.geo.atlas.AtlasRepository
import com.tripex.pose.domain.geo.AreaCoverage
import com.tripex.pose.domain.geo.ContinentId
import com.tripex.pose.domain.geo.atlas.AreaKey
import com.tripex.pose.domain.usecase.ObserveAreaCoverageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ContinentMapViewModel @Inject constructor(
    private val atlasRepository: AtlasRepository,
    private val observeAreaCoverage: ObserveAreaCoverageUseCase,
) : ViewModel() {

    private val local = MutableStateFlow(ContinentMapContract.State())

    init {
        loadShapes()
    }

    val state: StateFlow<ContinentMapContract.State> = local.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ContinentMapContract.State(),
    )

    private var coverageJob: Job? = null

    private val _effects = Channel<ContinentMapContract.Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onIntent(intent: ContinentMapContract.Intent) {
        when (intent) {
            is ContinentMapContract.Intent.ContinentClicked -> {
                local.update {
                    it.copy(selectedContinent = intent.id, coverage = AreaCoverage.Unavailable)
                }
                observeCoverage(intent.id)
            }
            ContinentMapContract.Intent.ClearSelection -> {
                coverageJob?.cancel()
                local.update {
                    it.copy(selectedContinent = null, coverage = AreaCoverage.Unavailable)
                }
            }
            ContinentMapContract.Intent.ExploreSelected -> {
                val current = local.value
                val id = current.selectedContinent ?: return
                val bounds = ContinentBounds.framing(
                    id = id,
                    atlasBounds = current.shapes.firstOrNull { it.id == id }?.bounds,
                )
                viewModelScope.launch {
                    _effects.send(ContinentMapContract.Effect.OpenMap(id, bounds))
                }
            }
        }
    }

    /** Measured against the real atlas outline — no more `estimatedLandCells` guesswork. */
    private fun observeCoverage(id: ContinentId?) {
        coverageJob?.cancel()
        if (id == null) return
        coverageJob = viewModelScope.launch {
            observeAreaCoverage(AreaKey.Continent(id)).collectLatest { coverage ->
                local.update { if (it.selectedContinent == id) it.copy(coverage = coverage) else it }
            }
        }
    }

    private fun loadShapes() {
        viewModelScope.launch {
            // The atlas only powers this overview; a failure here degrades the screen, it does
            // not take the app down (see AppShellViewModel readiness).
            runCatching { atlasRepository.continents() }
                .onSuccess { shapes -> local.update { it.copy(shapes = shapes, atlasUnavailable = false) } }
                .onFailure { local.update { it.copy(shapes = emptyList(), atlasUnavailable = true) } }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
