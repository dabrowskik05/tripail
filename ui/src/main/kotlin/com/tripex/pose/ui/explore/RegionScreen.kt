package com.tripex.pose.ui.explore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripex.pose.domain.geo.GeoBounds
import androidx.compose.ui.res.stringResource
import com.tripex.pose.domain.geo.atlas.AreaKey
import com.tripex.pose.ui.R
import com.tripex.pose.ui.explore.components.AreaStatsPanel
import com.tripex.pose.ui.map.host.MapHostState
import com.tripex.pose.ui.map.host.MapLevel
import com.tripex.pose.ui.map.host.MapScene
import com.tripex.pose.ui.shell.chrome.AppChromeState
import kotlinx.coroutines.flow.collectLatest

@Composable
fun RegionRoute(
    host: MapHostState,
    chrome: AppChromeState,
    onOpenMap: (AreaKey, GeoBounds, String) -> Unit,
    onOpenCountry: (iso2: String, bounds: GeoBounds) -> Unit,
    onBack: () -> Unit,
    viewModel: RegionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is RegionContract.Effect.OpenMap -> onOpenMap(effect.area, effect.bounds, effect.label)
                is RegionContract.Effect.OpenCountry -> onOpenCountry(effect.iso2, effect.bounds)
            }
        }
    }

    BoundaryMapScreen(
        host = host,
        chrome = chrome,
        scene = MapScene(
            level = MapLevel.Region,
            continentId = state.map.continentId,
            countryIso2 = state.countryIso2,
            selectedId = state.regionId,
        ),
        title = state.name,
        onBack = onBack,
        onTap = { viewModel.onIntent(RegionContract.Intent.RegionTapped(it)) },
        panel = state,
        panelKey = { it.regionId },
    ) { shown ->
        AreaStatsPanel(
            flag = shown.flag,
            title = shown.name,
            coverage = shown.coverage,
            actionLabel = stringResource(
                when {
                    shown.isUnlocking -> R.string.area_region_unlocking
                    shown.isUnlocked -> R.string.area_region_cover
                    else -> R.string.area_region_unlock
                },
            ),
            // Always live except mid-write: revealing a region must be undoable from the same spot.
            onAction = if (shown.isUnlocking) {
                null
            } else {
                { viewModel.onIntent(RegionContract.Intent.ToggleRegionUnlock) }
            },
        )
    }
}
