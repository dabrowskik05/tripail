package com.tripex.pose.ui.explore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.atlas.AreaKey
import com.tripex.pose.ui.explore.components.AreaStatsPanel
import com.tripex.pose.ui.map.host.MapHostState
import com.tripex.pose.ui.map.host.MapLevel
import com.tripex.pose.ui.map.host.MapScene
import com.tripex.pose.ui.shell.chrome.AppChromeState
import kotlinx.coroutines.flow.collectLatest

/**
 * The country level.
 *
 * No buttons: regions are tappable on the map from the moment it opens, the neighbouring country
 * is tappable beside them, and the panel simply says where you are and how much of it you have
 * found (V3.2.4, V3.3.1).
 */
@Composable
fun CountryRoute(
    host: MapHostState,
    chrome: AppChromeState,
    onOpenMap: (AreaKey, GeoBounds, String) -> Unit,
    onOpenRegion: (regionId: String, countryIso2: String, bounds: GeoBounds) -> Unit,
    onBack: () -> Unit,
    viewModel: CountryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is CountryContract.Effect.OpenMap -> onOpenMap(effect.area, effect.bounds, effect.label)
                is CountryContract.Effect.OpenRegion ->
                    onOpenRegion(effect.regionId, effect.countryIso2, effect.bounds)
                is CountryContract.Effect.FrameCamera -> host.flyTo(effect.bounds, animate = true)
            }
        }
    }

    BoundaryMapScreen(
        host = host,
        chrome = chrome,
        scene = MapScene(
            level = MapLevel.Country,
            continentId = state.map.continentId,
            countryIso2 = state.iso2,
            selectedId = state.iso2,
        ),
        title = state.name,
        onBack = onBack,
        onTap = { viewModel.onIntent(CountryContract.Intent.FeatureTapped(it)) },
    ) {
        AreaStatsPanel(flag = state.flag, title = state.name, coverage = state.coverage)
    }
}
