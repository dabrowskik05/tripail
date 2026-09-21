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
import kotlinx.coroutines.flow.collectLatest

@Composable
fun RegionRoute(
    host: MapHostState,
    onOpenMap: (AreaKey, GeoBounds, String) -> Unit,
    onBack: () -> Unit,
    viewModel: RegionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is RegionContract.Effect.OpenMap -> onOpenMap(effect.area, effect.bounds, effect.label)
            }
        }
    }

    BoundaryMapScreen(
        host = host,
        scene = MapScene(
            level = MapLevel.Region,
            continentId = state.map.continentId,
            countryIso2 = state.countryIso2,
            selectedId = state.regionId,
        ),
        title = state.name,
        onBack = onBack,
        onTap = { viewModel.onIntent(RegionContract.Intent.RegionTapped(it)) },
    ) {
        AreaStatsPanel(
            flag = state.flag,
            title = state.name,
            coverage = state.coverage,
            onExplore = { viewModel.onIntent(RegionContract.Intent.ExploreRequested) },
            secondaryLabel = stringResource(
                when {
                    state.isUnlocking -> R.string.area_region_unlocking
                    state.isUnlocked -> R.string.area_region_cover
                    else -> R.string.area_region_unlock
                },
            ),
            // Always live except mid-write: unlocking a region must be undoable from the same spot.
            onSecondary = if (state.isUnlocking) {
                null
            } else {
                { viewModel.onIntent(RegionContract.Intent.ToggleRegionUnlock) }
            },
        )
    }
}
