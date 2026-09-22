package com.tripex.pose.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripex.pose.domain.geo.H3Converter
import com.tripex.pose.domain.geo.atlas.AtlasRepository
import com.tripex.pose.domain.map.MapStyleProvider
import com.tripex.pose.domain.settings.AppLanguageRepository
import com.tripex.pose.domain.tiles.PmTilesBootstrap
import com.tripex.pose.domain.usecase.ObserveUnlockedCountUseCase
import com.tripex.pose.ui.shell.AppShellContract.FailedSignal
import com.tripex.pose.ui.shell.AppShellContract.Readiness
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Startup gate (M2.2). Emits [Readiness] derived from real signals only — there is no timer and
 * no synthetic progress. The signal that genuinely takes time on a cold first launch is the
 * PMTiles copy into `filesDir`, and it is the reason a loading indicator exists at all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppShellViewModel @Inject constructor(
    private val mapStyleProvider: MapStyleProvider,
    private val h3Converter: H3Converter,
    private val atlasRepository: AtlasRepository,
    private val pmTilesBootstrap: PmTilesBootstrap,
    private val observeUnlockedCount: ObserveUnlockedCountUseCase,
    private val appLanguage: AppLanguageRepository,
) : ViewModel() {

    private val attempt = MutableStateFlow(0)

    /** Read once: the answer only changes when the player picks, and then they are past it. */
    private val needsLanguage = MutableStateFlow(false)

    init {
        viewModelScope.launch { needsLanguage.value = appLanguage.selected() == null }
    }

    val state: StateFlow<AppShellContract.State> = attempt
        .flatMapLatest { readiness() }
        .combine(needsLanguage) { readiness, needsPick ->
            AppShellContract.State(readiness = readiness, needsLanguage = needsPick)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = AppShellContract.State(),
        )

    fun onIntent(intent: AppShellContract.Intent) {
        when (intent) {
            AppShellContract.Intent.Retry -> attempt.update { it + 1 }
        }
    }

    private fun readiness(): Flow<Readiness> {
        val signals = listOf(
            // Room is open and answering once the DAO flow emits for the first time.
            signal(FailedSignal.Storage, degraded = false) { observeUnlockedCount().first() },
            // Forces the H3 native library to load here rather than mid-walk.
            signal(FailedSignal.Hexes, degraded = false) { h3Converter.warmUp() },
            signal(FailedSignal.Style, degraded = false) {
                check(mapStyleProvider.styleUri().isNotEmpty()) { "Empty map style URI" }
            },
            signal(FailedSignal.Atlas, degraded = true) { atlasRepository.land() },
            signal(FailedSignal.Boundaries, degraded = true) { pmTilesBootstrap.ensureReady().getOrThrow() },
        )
        return combine(signals) { emitted -> emitted.toList().reduceToReadiness() }
    }

    /**
     * One startup step as a flow: [Readiness.Preparing] while it runs, [Readiness.Ready] once it
     * completes, [Readiness.Failed] when it throws.
     */
    private fun signal(
        name: FailedSignal,
        degraded: Boolean,
        block: suspend () -> Unit,
    ): Flow<Readiness> = flow<Readiness> {
        block()
        emit(Readiness.Ready)
    }
        .onStart { emit(Readiness.Preparing) }
        .catch { emit(Readiness.Failed(signal = name, degraded = degraded)) }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /**
         * A blocking failure wins over everything, then an unfinished signal, then a degraded
         * failure. Only an all-ready list is [Readiness.Ready].
         */
        fun List<Readiness>.reduceToReadiness(): Readiness {
            firstOrNull { it is Readiness.Failed && !it.degraded }?.let { return it }
            if (any { it is Readiness.Preparing }) return Readiness.Preparing
            firstOrNull { it is Readiness.Failed }?.let { return it }
            return Readiness.Ready
        }
    }
}
