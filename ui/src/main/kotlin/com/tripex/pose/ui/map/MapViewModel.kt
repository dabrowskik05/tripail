package com.tripex.pose.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripex.pose.domain.location.TrackingController
import com.tripex.pose.domain.location.TrackingSetupRepository
import com.tripex.pose.domain.location.TrackingState
import com.tripex.pose.domain.location.TrackingStateHolder
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

/**
 * The explore level: permissions, tracking status and the background-tracking prompts.
 *
 * Search, suggestions and the place panel moved out to `SearchViewModel` (V3.2.2) — they belong
 * to the whole app now, not to this one screen.
 */
@HiltViewModel
class MapViewModel @Inject constructor(
    private val trackingController: TrackingController,
    private val trackingSetup: TrackingSetupRepository,
    trackingStateHolder: TrackingStateHolder,
) : ViewModel() {

    private val local = MutableStateFlow(MapContract.State())

    val state: StateFlow<MapContract.State> = combine(
        local,
        trackingStateHolder.state,
    ) { base, tracking ->
        base.copy(trackingState = tracking, statusMessage = statusFor(tracking, base))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = local.value,
    )

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

            MapContract.Intent.OpenSettingsRequested ->
                viewModelScope.launch { _effects.send(MapContract.Effect.OpenAppSettings) }

            MapContract.Intent.OpenCommunity -> local.update { it.copy(communityVisible = true) }
            MapContract.Intent.CloseCommunity -> local.update { it.copy(communityVisible = false) }

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
                // Declining still counts as having seen it. Re-asking on every start is how a
                // useful prompt becomes background noise.
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
                !trackingSetup.hasSeenReliabilityPrompt() -> MapContract.BackgroundPrompt.Reliability
                else -> null
            } ?: return@launch
            local.update { if (it.backgroundPrompt == null) it.copy(backgroundPrompt = prompt) else it }
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
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
