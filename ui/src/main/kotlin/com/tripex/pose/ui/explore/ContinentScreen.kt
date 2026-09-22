package com.tripex.pose.ui.explore

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.ui.R
import com.tripex.pose.ui.continent.ContinentPalette
import com.tripex.pose.ui.explore.components.PanelHint
import com.tripex.pose.ui.map.host.MapHostState
import com.tripex.pose.ui.map.host.MapLevel
import com.tripex.pose.ui.map.host.MapScene
import com.tripex.pose.ui.shell.chrome.AppChromeState
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ContinentRoute(
    host: MapHostState,
    chrome: AppChromeState,
    onOpenCountry: (iso2: String, bounds: GeoBounds) -> Unit,
    onBack: () -> Unit,
    viewModel: ContinentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val missMessage = stringResource(R.string.area_country_unknown)

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is ContinentContract.Effect.OpenCountry -> onOpenCountry(effect.iso2, effect.bounds)
                ContinentContract.Effect.NoCountryHere -> snackbarHostState.showSnackbar(missMessage)
                is ContinentContract.Effect.LimitCamera -> host.limitTo(effect.bounds)
                is ContinentContract.Effect.FocusPlayer -> host.flyTo(effect.bounds, animate = true)
            }
        }
    }

    BoundaryMapScreen(
        host = host,
        chrome = chrome,
        scene = MapScene(
            level = MapLevel.Continent,
            continentId = state.continentId?.name,
        ),
        title = state.continentId?.let { stringResource(ContinentPalette.label(it)) }.orEmpty(),
        onBack = onBack,
        onTap = { viewModel.onIntent(ContinentContract.Intent.CountryTapped(it)) },
    ) {
        PanelHint(
            text = stringResource(R.string.area_pick_country),
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
        )
        SnackbarHost(hostState = snackbarHostState)
    }
}
