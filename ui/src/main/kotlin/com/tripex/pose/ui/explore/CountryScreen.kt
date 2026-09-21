package com.tripex.pose.ui.explore

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.atlas.AreaKey
import com.tripex.pose.ui.R
import com.tripex.pose.ui.explore.components.AreaStatsPanel
import com.tripex.pose.ui.map.host.MapHostState
import com.tripex.pose.ui.map.host.MapLevel
import com.tripex.pose.ui.map.host.MapScene
import kotlinx.coroutines.flow.collectLatest

@Composable
fun CountryRoute(
    host: MapHostState,
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

    // Back leaves the region layers before it leaves the country.
    BackHandler(enabled = state.showingRegions) {
        viewModel.onIntent(CountryContract.Intent.ShowCountries)
    }

    BoundaryMapScreen(
        host = host,
        scene = MapScene(
            level = if (state.showingRegions) MapLevel.Region else MapLevel.Country,
            continentId = state.map.continentId,
            countryIso2 = state.iso2,
            selectedId = if (state.showingRegions) null else state.iso2,
        ),
        title = state.name,
        onBack = onBack,
        onTap = { viewModel.onIntent(CountryContract.Intent.FeatureTapped(it)) },
    ) {
        AreaStatsPanel(
            flag = state.flag,
            title = state.name,
            coverage = state.coverage,
            onExplore = { viewModel.onIntent(CountryContract.Intent.ExploreRequested) },
            secondaryLabel = stringResource(
                if (state.showingRegions) R.string.area_back else R.string.area_regions,
            ),
            onSecondary = {
                viewModel.onIntent(
                    if (state.showingRegions) {
                        CountryContract.Intent.ShowCountries
                    } else {
                        CountryContract.Intent.ShowRegions
                    },
                )
            },
        )
    }
}
