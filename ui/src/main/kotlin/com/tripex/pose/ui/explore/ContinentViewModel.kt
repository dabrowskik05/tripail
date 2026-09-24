package com.tripex.pose.ui.explore

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.tripex.pose.core.logging.Logger
import com.tripex.pose.domain.geo.ContinentBounds
import com.tripex.pose.domain.geo.ContinentFocusPolicy
import com.tripex.pose.domain.geo.ContinentId
import com.tripex.pose.domain.geo.atlas.AdminLevel
import com.tripex.pose.domain.geo.atlas.BoundaryGeometrySource
import com.tripex.pose.domain.geo.projection.GeometryOps
import com.tripex.pose.domain.location.LocationTracker
import com.tripex.pose.domain.location.TrackingSession
import com.tripex.pose.domain.location.TrackingSessionRepository
import com.tripex.pose.ui.navigation.Continent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
 * The continent-wide framing is placed by the menu on the way in (V3.3.5). This level adds two
 * things on top, in this order: a fence around the continent so panning cannot wander onto
 * neighbouring, still-fogged ground (V3.3.7), and — only if the player is actually here — one
 * soft flight down to them (V3.3.6). Seeing the continent whole first is the point; arriving
 * straight at the player would skip the context that makes the drill-down legible.
 */
@HiltViewModel
class ContinentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val boundaries: BoundaryGeometrySource,
    private val trackingSession: TrackingSessionRepository,
    private val locationTracker: LocationTracker,
    private val logger: Logger,
) : ViewModel() {

    private val route: Continent = savedStateHandle.toRoute()
    private val continentId = runCatching { ContinentId.valueOf(route.continentId) }.getOrNull()

    private val _state = MutableStateFlow(
        ContinentContract.State(
            continentId = continentId,
            map = BoundaryMapState(
                selectedId = null,
                continentId = continentId?.name,
            ),
        ),
    )
    val state: StateFlow<ContinentContract.State> = _state.asStateFlow()

    private val _effects = Channel<ContinentContract.Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        val bounds = continentId?.let { ContinentBounds.region(it).bounds }
        if (bounds != null) {
            viewModelScope.launch {
                _effects.send(ContinentContract.Effect.LimitCamera(ContinentFocusPolicy.cameraLimit(bounds)))

                val now = System.currentTimeMillis()
                // Asked fresh: the service's stored fix is only as recent as the last 50 m walked,
                // so after a day at home it was too old to use and the flight silently skipped.
                val fix = locationTracker.currentLocation()
                    ?.let { TrackingSession.Fix(it.latitude, it.longitude, now) }
                    ?: trackingSession.load().lastFix
                val focus = ContinentFocusPolicy.focus(continent = bounds, fix = fix, nowMs = now)
                if (focus != null) {
                    logger.i(TAG, "Player is on $continentId — settling on them")
                    // The continent is shown whole first; the flight follows it rather than
                    // replacing it (the camera queues requests, see MapHostState.flyTo).
                    delay(SHOW_CONTINENT_MS)
                    _effects.send(ContinentContract.Effect.FocusPlayer(focus))
                }
            }
        }
    }

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

        /** How long the whole continent stays in view before the camera settles on the player. */
        const val SHOW_CONTINENT_MS = 700L
    }
}
