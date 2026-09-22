package com.tripex.pose.ui.continent

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripex.pose.domain.geo.AreaCoverage
import com.tripex.pose.domain.geo.ContinentId
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.ui.R
import com.tripex.pose.ui.components.ChunkyButton
import com.tripex.pose.ui.continent.components.ContinentMenuCanvas
import com.tripex.pose.ui.continent.components.WorldPan
import com.tripex.pose.ui.shell.chrome.AppChromeState
import com.tripex.pose.ui.shell.chrome.RegisterChrome
import com.tripex.pose.ui.theme.LocalCartoonStyle
import kotlinx.coroutines.flow.collectLatest

private const val DIM_FRACTION = 1f
private const val DIM_DURATION_MILLIS = 300
private const val PERCENT = 100f

private val PanelShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/**
 * Stage A — the continent menu (vision §3).
 *
 * A screen entirely of its own: a Compose canvas of continent shapes over water. It holds **no
 * MapLibre instance and no reference to the map host**, which is the whole point of §3 — and also
 * a hard requirement, because MapLibre's `MapView` is a `SurfaceView` that would paint straight
 * over this canvas if it were composed at the same time.
 */
@Composable
fun ContinentMapRoute(
    chrome: AppChromeState,
    onOpenContinent: (ContinentId, GeoBounds) -> Unit,
    onBack: () -> Unit,
    viewModel: ContinentMapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cartoon = LocalCartoonStyle.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is ContinentMapContract.Effect.OpenMap ->
                    onOpenContinent(effect.continentId, effect.bounds)
            }
        }
    }

    val selected = state.selectedContinent

    // Selecting a continent is a layer of state above the level, so back clears it before it
    // leaves — the same priority order every other level follows (V3.2.3).
    RegisterChrome(
        chrome = chrome,
        title = stringResource(
            if (selected == null) R.string.continent_world_title else ContinentPalette.label(selected),
        ),
        onBack = {
            if (selected != null) {
                viewModel.onIntent(ContinentMapContract.Intent.ClearSelection)
            } else {
                onBack()
            }
        },
    )

    /** Shared by the drag gesture and the slider; one value, two ways to move it. */
    var panFraction by remember { mutableStateOf(WorldPan.CENTRE) }

    val dim by animateFloatAsState(
        targetValue = if (selected == null) 0f else DIM_FRACTION,
        animationSpec = tween(DIM_DURATION_MILLIS),
        label = "continent-dim",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(cartoon.oceanBlue),
    ) {
        ContinentMenuCanvas(
            shapes = state.shapes,
            selectedId = selected,
            dimFraction = dim,
            onTap = { viewModel.onIntent(ContinentMapContract.Intent.ContinentClicked(it)) },
            panFraction = panFraction,
            onPanFractionChange = { panFraction = it },
            modifier = Modifier.fillMaxSize(),
        )

        ContinentMenuPanel(
            state = state,
            panFraction = panFraction,
            onPanFractionChange = { panFraction = it },
            onExplore = { viewModel.onIntent(ContinentMapContract.Intent.ExploreSelected) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * The bottom menu card, and the app's answer to the system navigation bar.
 *
 * The `Surface` runs all the way to the bottom edge while its content is lifted above the bar,
 * so the strip under the gesture handle is the card's own colour instead of ocean showing through
 * — and nothing interactive ever sits where a system gesture starts.
 */
@Composable
private fun ContinentMenuPanel(
    state: ContinentMapContract.State,
    panFraction: Float,
    onPanFractionChange: (Float) -> Unit,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cartoon = LocalCartoonStyle.current
    val selected = state.selectedContinent
    val worldSliderDescription = stringResource(R.string.continent_pan_cd)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = PanelShape,
        color = ContinentPalette.menuSurface,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The continent's own name is already in the header; repeating it here would just
            // push the buttons down.
            val hint = when {
                state.atlasUnavailable -> R.string.continent_atlas_unavailable
                selected == null -> R.string.continent_hint
                else -> null
            }
            if (hint != null) {
                Text(
                    text = stringResource(hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cartoon.inkPrimary,
                    textAlign = TextAlign.Center,
                )
            }

            val coverage = state.coverage
            if (selected != null && coverage is AreaCoverage.Known) {
                Text(
                    text = stringResource(
                        R.string.continent_coverage,
                        (coverage.fraction * PERCENT).toInt(),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = cartoon.inkPrimary,
                )
            }

            // Going back lives in the top-left arrow, not down here.
            if (selected != null) {
                ChunkyButton(
                    text = stringResource(R.string.continent_select),
                    onClick = onExplore,
                )
            }

            // One axis, one control (V3.6.4). Zoom is fixed here, so dragging offered four
            // directions of freedom for a single degree of it — and showed no position.
            Slider(
                value = panFraction,
                onValueChange = onPanFractionChange,
                modifier = Modifier.semantics {
                    contentDescription = worldSliderDescription
                },
            )
        }
    }
}
