package com.tripex.pose.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripex.pose.domain.geo.ContinentBounds
import com.tripex.pose.domain.map.MapStyleProvider
import com.tripex.pose.domain.usecase.ObserveUnlockedCountUseCase
import com.tripex.pose.ui.map.MapContract
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@HiltViewModel
class AppShellViewModel @Inject constructor(
    mapStyleProvider: MapStyleProvider,
    observeUnlockedCount: ObserveUnlockedCountUseCase,
) : ViewModel() {

    private val stage = MutableStateFlow<AppShellContract.Stage>(
        AppShellContract.Stage.Loading,
    )
    private val mapCameraTarget = MutableStateFlow<MapContract.CameraTarget?>(null)

    private val minimumElapsed = flow {
        emit(false)
        delay(MIN_SPLASH_MILLIS)
        emit(true)
    }

    private val styleReady = flow {
        emit(mapStyleProvider.styleUri().isNotEmpty())
    }

    private val dataReady = observeUnlockedCount()
        .map { true }
        .onStart { emit(false) }

    val state: StateFlow<AppShellContract.State> = combine(
        stage,
        minimumElapsed,
        styleReady,
        dataReady,
        mapCameraTarget,
    ) { currentStage, elapsed, style, data, camera ->
        val signals = listOf(elapsed, style, data)
        val ready = signals.all { it }
        AppShellContract.State(
            stage = currentStage,
            progress = signals.count { it } / signals.size.toFloat(),
            isReady = ready,
            mapCameraTarget = camera,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = AppShellContract.State(),
    )

    fun onIntent(intent: AppShellContract.Intent) {
        when (intent) {
            AppShellContract.Intent.EnterContinentsRequested ->
                stage.value = AppShellContract.Stage.Continents
            is AppShellContract.Intent.OpenMap -> {
                val bounds = ContinentBounds.region(intent.continentId).bounds
                val (lat, lng) = ContinentBounds.center(bounds)
                mapCameraTarget.value = MapContract.CameraTarget(
                    latitude = lat,
                    longitude = lng,
                    zoom = ContinentBounds.MAP_OVERVIEW_ZOOM,
                )
                stage.value = AppShellContract.Stage.Map
            }
            AppShellContract.Intent.BackToContinents -> {
                mapCameraTarget.value = null
                stage.value = AppShellContract.Stage.Continents
            }
            AppShellContract.Intent.MapCameraConsumed ->
                mapCameraTarget.update { null }
        }
    }

    private companion object {
        const val MIN_SPLASH_MILLIS = 1_200L
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
